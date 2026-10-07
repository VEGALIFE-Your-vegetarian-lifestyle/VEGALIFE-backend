# API Reference: GET /api/ai/conversations

## Overview

List the authenticated member's own AI conversations, most recently active
first, so a client that restarted can discover its past threads.

## Endpoint

```
GET /api/ai/conversations
```

## Authentication

JWT Bearer (access token). Required — only the caller's own conversations are
returned.

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

No request body.

## Responses

### Success Response (200)
```json
{
  "success": true,
  "message": "Conversations retrieved",
  "data": [
    {
      "id": "3f0a9d21-5f6e-4c11-9a8b-1d2e3f4a5b6c",
      "title": "What can I eat for a high-protein vegan breakfast?",
      "updatedAt": "2026-10-07T18:03:00Z"
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data | array | The caller's conversations |
| data[].id | uuid | Conversation id (use it with the send and detail endpoints) |
| data[].title | string | Title derived from the first message (trimmed to 60 chars) |
| data[].updatedAt | instant | Last activity time; list is sorted by this, descending |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | Missing/invalid access token | "Unauthorized" |
| 500 | Server error | "Internal server error" |

## Business Rules

- BR-AI-003 — conversation ownership comes from authentication; only rows whose
  `user_id` equals the caller are listed.

## Example

### Request
```bash
curl /api/ai/conversations \
  -H "Authorization: Bearer <accessToken>"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Conversations retrieved",
  "data": [
    {
      "id": "3f0a9d21-5f6e-4c11-9a8b-1d2e3f4a5b6c",
      "title": "What can I eat for a high-protein vegan breakfast?",
      "updatedAt": "2026-10-07T18:03:00Z"
    }
  ]
}
```

---

## Related

- Feature Spec: `docs/feats/ai-chat-send.md`
- Detail endpoint: `docs/apis/ai/get-conversations-id.md`
- Send endpoint: `docs/apis/ai/post-messages.md`
- Business Rules: `docs/brs/ai.md`
