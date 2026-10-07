# Business Rules: AI Chat

Rules governing AI chat turns (issue #14; spec `docs/feats/ai-chat-send.md`).
Quota window timing is owned by BR-SUBS-001 (`docs/brs/subscriptions.md`) and
referenced here, not redefined.

## Rules

| Rule ID | Title | Status | Last Reviewed |
|---------|-------|--------|---------------|
| BR-AI-001 | Quota gate precedes the provider call | Active | 2026-10-07 |
| BR-AI-002 | Only successful replies count toward quota | Active | 2026-10-07 |
| BR-AI-003 | Conversation ownership comes from authentication | Active | 2026-10-07 |
| BR-AI-004 | Context is profile plus own conversation history | Active | 2026-10-07 |
| BR-AI-005 | Chat provider is OpenAI-compatible and set by dedicated env vars | Active | 2026-10-07 |

### BR-AI-001 — Quota gate precedes the provider call
Every AI chat request (JSON and streaming) compares the member's used count
for the current UTC calendar month (BR-SUBS-001 window) against the active
plan's `monthly_request_limit` **before** any model provider call. At or above
the limit the API responds `429` with a `Retry-After` header (seconds until
window end) and the provider is never invoked.

- Enforcement: `AiChatService` + `AiQuotaGuard.requireAllowance(userId)`
- Related: BR-SUBS-001 (window definition), FR-007 in `docs/feats/ai-chat-send.md`
- Added: 2026-10-07

### BR-AI-002 — Only successful replies count toward quota
`ai_usage.request_count` increments by exactly 1 (atomic upsert on the
`(user_id, period_start, period_end)` row) only after the assistant reply is
fully generated and persisted. Provider errors, validation failures, quota
rejections, and streams that die mid-generation persist no history and
consume no quota.

- Enforcement: `AiChatService` (post-success transaction), `AiUsageRepository` upsert
- Related: BR-SUBS-001 (period row), FR-008 / FR-010
- Added: 2026-10-07

### BR-AI-003 — Conversation ownership comes from authentication
A supplied `conversationId` must reference an `ai_conversation` row whose
`user_id` equals the authenticated caller; otherwise the request fails `404`
("Conversation not found") before any provider call. Conversations created
during a turn are always owned by the caller. Members can never read, extend,
or observe another member's messages through chat endpoints.

- Enforcement: `AiChatService` (ownership lookup via `AiConversationRepository`)
- Related: NFR-SEC-001, FR-006
- Added: 2026-10-07

### BR-AI-004 — Context is profile plus own conversation history
Each turn's model context = a system prompt containing only the caller's own
`UserProfile` fields (age, gender, height, weight, description) + the last 20
messages of the conversation including the new message; the model infers
dietary preferences, restrictions, and recent activity from those messages.
No cross-user data, no external stores, no schema change for inferred
preferences.

- Enforcement: `AiChatService` (system prompt builder + history query)
- Related: FR-005
- Added: 2026-10-07

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
