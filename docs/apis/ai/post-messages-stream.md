# API Reference: POST /api/ai/messages/stream

## Overview

Send one chat message to the AI assistant and receive the reply incrementally
as Server-Sent Events (`delta` fragments, then a `done` event); same
conversation, context, persistence, and quota semantics as the JSON endpoint.

## Endpoint

```
POST /api/ai/messages/stream
```

## Authentication

JWT Bearer (access token). Required — only the caller's own conversations can
be used. The token is validated when the request is made (standard security
filter chain), not per SSE event.

## Request

### Path Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| —    | —    | —        | No path parameters |

### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| —    | —    | —        | No query parameters |

### Request Body
```json
{
  "conversationId": "uuid — optional; continue an existing conversation, omit to start a new one",
  "message": "string — required, 1..4000 chars"
}
```

Same validation and ownership rules as `POST /api/ai/messages`. Quota is
checked before the stream opens: a 429/404/400 is returned as a normal
`ApiResponse` JSON body (no SSE starts).

## Responses

### Success Response (200, `Content-Type: text/event-stream`)

SSE events (each `data` payload is a JSON object):

| Event | Payload | When |
|-------|---------|------|
| `delta` | `{ "reply": "<text fragment>" }` | Zero or more times, as the provider streams tokens |
| `done` | `{ "conversationId": "...", "reply": "<full reply>", "usage": { used, limit, windowStart, windowEnd } }` | Exactly once after the reply completes and history + usage are persisted |
| `error` | `{ "message": "AI provider request failed" }` | Provider failure; history not persisted, usage not incremented |

```
event: delta
data: {"reply":"Try"}

event: delta
data: {"reply":" tofu scramble..."}

event: done
data: {"conversationId":"3f0a9d21-5f6e-4c11-9a8b-1d2e3f4a5b6c","reply":"Try tofu scramble...","usage":{"used":3,"limit":20,"windowStart":"2026-10-01T00:00:00Z","windowEnd":"2026-11-01T00:00:00Z"}}
```

### Error Responses (JSON body, no SSE stream opened)

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Validation failed (missing/blank message, >4000 chars) | "Validation failed" |
| 401 | Missing/invalid access token | Auth error message |
| 404 | `conversationId` not found or owned by another member | "Conversation not found" |
| 429 | Monthly plan quota reached (`Retry-After` = seconds until window end) | "Monthly AI request limit reached (used/limit). Resets at the start of the next UTC month." |
| 500 | Server error | "Internal server error" |

A failure **after** the stream has opened is delivered as an `error` event
with HTTP 200 already sent.

## Business Rules

- BR-AI-001 — quota gate precedes the provider call
- BR-AI-002 — only successful replies count toward quota (a stream that dies
  mid-way persists nothing and counts nothing)
- BR-AI-003 — conversation ownership comes from authentication
- BR-AI-004 — context is profile + own conversation history
- BR-AI-005 — provider configured via dedicated `AI_CHAT_*` env vars
- BR-SUBS-001 — quota window is the current UTC calendar month

## Example

### Request
```bash
curl -N -X POST /api/ai/messages/stream \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{"conversationId":"3f0a9d21-5f6e-4c11-9a8b-1d2e3f4a5b6c","message":"Plan my next meal"}'
```

### Event Stream (200)
```
event: delta
data: {"reply":"Here"}

event: delta
data: {"reply":" is a balanced plan..."}

event: done
data: {"conversationId":"3f0a9d21-5f6e-4c11-9a8b-1d2e3f4a5b6c","reply":"Here is a balanced plan...","usage":{"used":4,"limit":20,"windowStart":"2026-10-01T00:00:00Z","windowEnd":"2026-11-01T00:00:00Z"}}
```

---

## Related

- Feature Spec: `docs/feats/ai-chat-send.md`
- JSON variant: `docs/apis/ai/post-messages.md`
- Business Rules: `docs/brs/ai.md`
- Follow-up (history read APIs): GitHub issue #115
