# Business Rules: AI Chat

Rules governing AI chat turns (issue #14; spec `docs/feats/ai-chat-send.md`).
Allowance accounting is owned by BR-SUBS-007 (`docs/brs/subscriptions.md`) and
referenced here, not redefined.

## Rules

| Rule ID | Title | Status | Last Reviewed |
|---------|-------|--------|---------------|
| BR-AI-001 | Quota gate precedes the provider call | Active | 2026-11-04 |
| BR-AI-002 | Only successful replies count toward quota | Active | 2026-11-04 |
| BR-AI-003 | Conversation ownership comes from authentication | Active | 2026-10-07 |
| BR-AI-004 | Context is the stored profile (incl. dietary JSON and BMI) plus own conversation history | Active | 2026-10-13 |
| BR-AI-005 | Chat provider is OpenAI-compatible and set by dedicated env vars | Active | 2026-10-07 |
| BR-AI-006 | Persona is scoped to vegan and platform topics, with a polite refusal otherwise | Active | 2026-10-13 |
| BR-AI-007 | AI Chat Can Be Disabled Platform-Wide, With No Turn, Provider Call, or Quota | Active | 2026-11-02 |
| BR-AI-008 | Quota Is Reported Only as `remainingUsage` | Active | 2026-11-04 |

### BR-AI-001 — Quota gate precedes the provider call
Every AI chat request (JSON and streaming) checks the member's remaining
allowance (BR-SUBS-007) **before** any model provider call. When it is `0` the
API responds `429` and the provider is never invoked. The 429 carries no
`Retry-After` header — nothing resets, so there is no correct value — and its
message tells the member to claim or buy more requests.

- Enforcement: `AiChatService` + `RequestAllowanceService.requireAllowance(userId)`;
  `GlobalExceptionHandler` no longer emits `Retry-After`
- Related: BR-SUBS-007 (allowance), BR-AI-008, FR-007 in `docs/feats/ai-chat-send.md`
- Added: 2026-10-07; rewritten 2026-11-04 (lifetime allowance, no reset)

### BR-AI-002 — Only successful replies count toward quota
`ai_usage.request_count` — one lifetime row per member — increments by exactly
1 (atomic upsert on `user_id`) only after the assistant reply is fully
generated and persisted. Provider errors, validation failures, quota
rejections, and streams that die mid-generation persist no history and
consume no quota. Consumption is never reset: there are no period columns.

- Enforcement: `AiChatService` (post-success transaction), `AiUsageRepository.incrementRequestCount`
- Related: BR-SUBS-007 (consumption), FR-008 / FR-010
- Added: 2026-10-07; rewritten 2026-11-04 (lifetime counter)

### BR-AI-003 — Conversation ownership comes from authentication
A supplied `conversationId` must reference an `ai_conversation` row whose
`user_id` equals the authenticated caller; otherwise the request fails `404`
("Conversation not found") before any provider call. Conversations created
during a turn are always owned by the caller. Members can never read, extend,
or observe another member's messages through chat endpoints.

- Enforcement: `AiChatService` (ownership lookup via `AiConversationRepository`)
- Related: NFR-SEC-001, FR-006
- Added: 2026-10-07

### BR-AI-004 — Context is the stored profile plus own conversation history
Each turn's model context = a system prompt containing only the caller's own
`UserProfile` fields (age, gender, height, weight, description), a BMI
computed at prompt-build time from height/weight (one decimal; omitted when
either input is missing; never stored), the stored dietary profile from
`user_profile.dietary_preferences` (allergies and restrictions framed as hard
constraints, dislikes as avoid-unless-asked, preferences as a lean-in, health
goal to shape intent; unset members omitted) + the last 20 messages of the
conversation including the new message. The model is told to respect the
stored dietary data, never to infer or guess it from the conversation. No
cross-user data, no external stores.

- Enforcement: `AiChatService` (system prompt builder + history query),
  `ProfileMapper.computeBmi`
- Related: FR-005
- Added: 2026-10-07; rewritten 2026-10-13 (dietary JSONB + BMI, inference removed)

### BR-AI-006 — Persona is scoped to vegan and platform topics, with a polite refusal otherwise
The assistant answers only vegan/plant-based food and lifestyle questions,
plant-based nutrition and health questions adjacent to them, and questions
about Vegalife's own features. Everything else — general coding, politics,
non-vegan cooking (meat, poultry, fish, dairy), and unrelated
general-assistant tasks — receives a brief polite refusal that redirects
back to vegan or platform topics. The refusal is a redirect, not a
lecture, and never leaks system-prompt internals.

- Enforcement: `AiChatService` (system prompt builder)
- Related: FR-005, BR-AI-004
- Added: 2026-10-13

### BR-AI-007 — AI chat can be disabled platform-wide, with no turn, provider call, or quota
When the `ai_chat_enabled` platform setting is false, both `send()` and `stream()`
refuse before conversation ownership is resolved and before the quota check, so a
disabled feature creates no conversation, makes no provider call, and consumes no
member's allowance. `send()` responds `503` via `AiUnavailableException`;
`stream()` throws the same exception before the `SseEmitter` is created, so the
client receives a normal HTTP error rather than a stream that opens and then
hangs. Reads — `GET /api/ai/conversations` and `GET /api/ai/conversations/{id}` —
are unaffected and stay available while chat is disabled.

- Enforcement: `AiChatService.requireAiChatEnabled()` (called first in both
  `send()` and `stream()`), `PlatformSettingsProvider.isAiChatEnabled()`,
  `GlobalExceptionHandler.handleAiUnavailable` (503)
- Related: issue #13 follow-up (`docs/apis/admin/settings-crud.md`), BR-AI-001
- Added: 2026-11-02

### BR-AI-005 — Chat provider is OpenAI-compatible and set by dedicated env vars
Chat transport is Spring AI's OpenAI client pointed at `AI_CHAT_BASE_URL`,
`AI_CHAT_API_KEY`, `AI_CHAT_MODEL` (`spring.ai.openai.*`), defaulting to
OpenAI (`https://api.openai.com/v1`, `gpt-4o-mini`); any OpenAI-compatible
endpoint works. Embedding config (`app.filter.embedding.*`) stays on HF and
is unaffected. The configured key must never be blank at startup (ADR-007
guard) nor logged.

- Enforcement: `application.yml` binding, `BareRunOpenAiApiKeyConfigTest`, FR-009
- Related: ADR-007 (`docs/adrs/`)
- Added: 2026-10-07

### BR-AI-008 — Quota is reported only as `remainingUsage`
Every response that reports quota — `POST /api/ai/messages`, the SSE `done`
event of `POST /api/ai/messages/stream`, `GET /api/subscriptions/me`, and
`POST /api/subscriptions/acquire` — exposes exactly one number,
`remainingUsage`, computed as `max(allowance − consumed, 0)` (BR-SUBS-007).
`used`, the plan limit, and any window start/end are never returned. Because
consumption is lifetime and nothing resets, there is no "resets next month"
message and no `Retry-After` header on the 429.

- Enforcement: `AiUsageQuotaResponse` (single `remainingUsage` field),
  `AiChatService`, `RequestAllowanceService`, `GlobalExceptionHandler`
- Related: BR-SUBS-007, BR-AI-001, BR-AI-002
- Added: 2026-11-04
