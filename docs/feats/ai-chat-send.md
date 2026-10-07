# Feature Spec: AI Chat Send (JSON + SSE Streaming)

## Status

Approved

## Author / owner

zuyzz (issue #14), direction confirmed with the project owner 2026-10-07.

## Summary

Authenticated members send a chat message to the AI assistant and get a reply,
either as a single JSON response or as an incremental Server-Sent Events
stream. The exchange is persisted per conversation so later turns carry
context, and each reply is gated by the member's subscription-tier monthly AI
quota.

## Problem / motivation

Issue #14: the platform has AI tables (`ai_conversation`, `ai_message`,
`ai_usage`) and subscription tiers with `monthly_request_limit` (FREE=20,
PRO=500) but no endpoint that actually runs an AI chat turn. Members cannot
talk to the assistant at all, and nothing consumes the quota the subscription
feature measures.

## Goals

- Members can hold a multi-turn AI conversation whose history is stored per
  user and reused as context on every turn.
- Replies can be streamed token-by-token so the UI can render incrementally.
- Tier quota (BR-SUBS-001 window) is enforced on every AI call before the
  provider is invoked.
- Any OpenAI-compatible chat endpoint can be plugged in via dedicated
  environment variables, independent of the HF embedding config.

## Non-goals

- Reading history: `GET /api/ai/conversations` and
  `GET /api/ai/conversations/{id}` are a separate follow-up (issue #115).
- Voice input/output, multi-modal (image) messages, conversation export.
- Editing/deleting messages or conversations, conversation renaming.
- Client-visible conversation listing, search, or pagination.
- RAG / tool-calling / function-calling beyond plain chat.

## Requirements

### Functional Requirements

- [ ] FR-001: `POST /api/ai/messages` accepts `{ conversationId?, message }`
  from an authenticated member; when `conversationId` is absent a new
  conversation is created for that member.
- [ ] FR-002: The JSON endpoint responds with `conversationId`, the full
  `reply`, and a `usage` block (`used`, `limit`, `windowStart`,
  `windowEnd`).
- [ ] FR-003: `POST /api/ai/messages/stream` accepts the same body and
  streams SSE events: `delta` (text fragment), `done`
  (conversationId + usage), `error` (failure notice).
- [ ] FR-004: After a successful reply both the member's message and the
  assistant's reply are persisted to `ai_message` in one transaction, and
  `ai_conversation.updated_at` is refreshed; new conversations get a title
  derived from the first message (trimmed to 60 chars).
- [ ] FR-005: Every request builds context as: a system prompt containing the
  member's `UserProfile` data (age, gender, height, weight, description) plus
  the last 20 messages of the conversation (new message included); the model
  infers dietary preferences, restrictions, and recent activity from those
  messages.
- [ ] FR-006: A supplied `conversationId` must reference a conversation owned
  by the caller; otherwise 404 `Conversation not found` (no provider call).
- [ ] FR-007: Before any provider call the used-count for the current UTC
  calendar month is compared to the member's active plan
  `monthly_request_limit`; at or above the limit the request fails 429 with
  `Retry-After` (seconds until window end) and the provider is never invoked.
- [ ] FR-008: `ai_usage.request_count` is incremented by exactly 1 only after
  a successful reply (atomic upsert on the `(user_id, period_start,
  period_end)` unique row).
- [ ] FR-009: Chat provider settings come from dedicated env vars
  `AI_CHAT_BASE_URL`, `AI_CHAT_API_KEY`, `AI_CHAT_MODEL` bound to
  `spring.ai.openai.*`; any OpenAI-compatible `/v1` endpoint works, and the
  `app.filter.embedding.*` HF config is untouched.
- [ ] FR-010: Provider failure yields 502 `AI provider request failed` (JSON)
  or an `error` SSE event (stream); no history is persisted and no quota is
  consumed.

### Non-Functional Requirements

- [ ] NFR-SEC-001: Members can only use conversations they own; responses
  never contain another member's messages, and provider keys are never
  logged.
- [ ] NFR-SEC-002: `message` is required, at most 4000 characters; requests
  are rejected 400 before any provider call.
- [ ] NFR-MAINT-001: Chat orchestration lives in one service shared by both
  endpoints; controllers stay thin (validation + response envelope only).
- [ ] NFR-SCALE-001: Streaming responds through `SseEmitter` async processing
  so the servlet container is not blocked per token.

## Design overview

New `controller/ai/AiChatController` exposes both endpoints; both delegate to
`service/ai/AiChatService`:

1. Validate input, resolve/verify conversation ownership (fail fast on 404).
2. `AiQuotaGuard` resolves the active plan (in-effect subscription or FREE
   default per BR-SUBS-003) and the current-UTC-month usage
   (`sumRequestCountInWindow`); over-limit → 429 before any provider call.
3. Build messages: system prompt (persona + `UserProfile` block) + last 20
   `ai_message` rows + the new user message.
4. Call the Spring AI `ChatClient` (blocking `.content()` for JSON,
   `.stream()` flux for SSE, terminal `SseEmitter` events `delta`/`done`/
   `error`).
5. On success, one transaction: insert `AiConversation` (if new), insert both
   `AiMessage` rows, bump `updated_at`, upsert `AiUsage` +1.

Entities `AiConversation` and `AiMessage` are created new (tables already
exist from `V10__create_ai_tables.sql`; `recent_messages` stays NULL —
history lives in `ai_message`). Two exceptions join `shared/exception/`:
`AiQuotaExceededException` (429 + `Retry-After`) and `AiProviderException`
(502), each with a `GlobalExceptionHandler` entry. Provider config is
`spring.ai.openai.{base-url,api-key,chat.options.model}` re-pointed to
`AI_CHAT_*` env names; `BareRunOpenAiApiKeyConfigTest` keeps enforcing a
non-blank key at startup (ADR-007), with dev/test dummy-key overrides
updated to the new env chain.

## Success metrics

- Issue #14 acceptance criteria all pass on `./mvnw verify` plus a manual
  SSE smoke test against a real OpenAI-compatible endpoint.
- FREE member over the 20-request limit receives 429 with zero provider
  calls observable in logs.

## Acceptance criteria

**As a** Vegalife member, **I want to** chat with the AI assistant and keep
the conversation context, **so that** I get personalized vegan-lifestyle
answers without re-explaining myself every message.

- [ ] Given an authenticated member, when posting a message without
      `conversationId`, then a new conversation is created and an AI reply is
      returned with `conversationId` and `usage`.
- [ ] Given an existing conversation, when posting a message with its
      `conversationId`, then the reply uses the prior messages of that
      conversation as context and both messages are persisted.
- [ ] Given a member with a profile (description, measurements), when sending
      a message, then the system prompt is built from that profile and the
      model infers preferences/dietary restrictions from the messages.
- [ ] Given a streaming request, when the provider responds, then `delta`
      events arrive incrementally and a final `done` event carries
      `conversationId` + `usage`.
- [ ] Given a member at their plan's monthly limit, when posting a message,
      then the API responds 429 with `Retry-After` and no provider call is
      made.
- [ ] Given another member's `conversationId`, when posting a message, then
      the API responds 404 and nothing is persisted.
- [ ] Given a provider outage, when posting a message, then the JSON endpoint
      responds 502 (stream: `error` event), no history rows are written, and
      usage is not incremented.

## Risks / open questions

- HF router as a chat backend is unverified — local/dev runs should set
  `AI_CHAT_BASE_URL` + `AI_CHAT_API_KEY` to a known OpenAI-compatible
  provider; defaults assume OpenAI (`https://api.openai.com/v1`,
  `gpt-4o-mini`).
- Quota increment and history insert are not mutually serializable across
  concurrent same-member requests (upsert is atomic per row; a race can
  briefly under-count by one) — accepted for this scale.
- `SseEmitter` + Spring AI flux requires careful completion/error handling so
  connections never hang; covered by a dedicated streaming test.
