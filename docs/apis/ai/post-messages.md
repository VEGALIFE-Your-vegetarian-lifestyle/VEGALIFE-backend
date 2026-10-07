# API Reference: POST /api/ai/messages

## Overview

Send one chat message to the AI assistant and receive the complete reply in a
single response; creates a conversation when none is supplied and persists
both messages.

## Endpoint

```
POST /api/ai/messages
```

## Authentication

JWT Bearer (access token). Required — only the caller's own conversations can
be used.

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

## Responses

### Success Response (200)
```json
{
  "success": true,
  "message": "Message sent successfully",
  "data": {
    "conversationId": "3f0a9d21-5f6e-4c11-9a8b-1d2e3f4a5b6c",
    "reply": "The assistant's full reply text.",
    "usage": {
      "used": 3,
      "limit": 20,
      "windowStart": "2026-10-01T00:00:00Z",
      "windowEnd": "2026-11-01T00:00:00Z"
    }
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data.conversationId | uuid | Conversation this turn belongs to (new or existing) |
| data.reply | string | Full assistant reply |
| data.usage.used | long | Requests counted this UTC month (after this one) |
| data.usage.limit | long | Active plan `monthly_request_limit` (FREE=20, PRO=500) |
| data.usage.windowStart | instant | UTC month window start (BR-SUBS-001) |
| data.usage.windowEnd | instant | UTC month window end |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Validation failed (missing/blank message, >4000 chars) | "Validation failed" |
| 401 | Missing/invalid access token | Auth error message |
| 404 | `conversationId` not found or owned by another member | "Conversation not found" |
| 429 | Monthly plan quota reached (`Retry-After` header = seconds until window end) | "Monthly AI request limit reached (used/limit). Resets at the start of the next UTC month." |
| 500 | Server error | "Internal server error" |
| 502 | AI provider call failed | "AI provider request failed" |

## Business Rules

- BR-AI-001 — quota gate precedes the provider call
- BR-AI-002 — only successful replies count toward quota
- BR-AI-003 — conversation ownership comes from authentication
- BR-AI-004 — context is profile + own conversation history
- BR-AI-005 — provider configured via dedicated `AI_CHAT_*` env vars
- BR-SUBS-001 — quota window is the current UTC calendar month

## Example

### Request
```bash
curl -X POST /api/ai/messages \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{"message":"What can I eat for a high-protein vegan breakfast?"}'
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Message sent successfully",
  "data": {
    "conversationId": "3f0a9d21-5f6e-4c11-9a8b-1d2e3f4a5b6c",
    "reply": "Try tofu scramble with tempeh bacon and a glass of soy milk — about 30g of protein in one plate.",
    "usage": { "used": 3, "limit": 20, "windowStart": "2026-10-01T00:00:00Z", "windowEnd": "2026-11-01T00:00:00Z" }
  }
}
```

---

## Related

- Feature Spec: `docs/feats/ai-chat-send.md`
- Streaming variant: `docs/apis/ai/post-messages-stream.md`
- Business Rules: `docs/brs/ai.md`
- Follow-up (history read APIs): GitHub issue #115
