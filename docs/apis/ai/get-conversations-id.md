# API Reference: GET /api/ai/conversations/{id}

## Overview

Load one of the authenticated member's conversations with its full message
history in chronological order.

## Endpoint

```
GET /api/ai/conversations/{id}
```

## Authentication

JWT Bearer (access token). Required — only a conversation the caller owns can
be read.

## Request

### Path Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| id   | uuid | yes      | Conversation id (from `GET /api/ai/conversations` or a send response) |

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
  "message": "Conversation retrieved",
  "data": {
    "id": "3f0a9d21-5f6e-4c11-9a8b-1d2e3f4a5b6c",
    "title": "What can I eat for a high-protein vegan breakfast?",
    "updatedAt": "2026-10-07T18:03:00Z",
    "messages": [
      {
        "role": "user",
        "content": "What can I eat for a high-protein vegan breakfast?",
        "createdAt": "2026-10-07T18:02:59Z"
      },
      {
        "role": "assistant",
        "content": "Try tofu scramble with tempeh bacon and a glass of soy milk.",
        "createdAt": "2026-10-07T18:03:00Z"
      }
    ]
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data.id | uuid | Conversation id |
| data.title | string | Title derived from the first message |
| data.updatedAt | instant | Last activity time |
| data.messages | array | Full history, oldest first |
| data.messages[].role | string | `user`, `assistant`, or `system` |
| data.messages[].content | string | Message text |
| data.messages[].createdAt | instant | When the message was persisted |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | Missing/invalid access token | "Unauthorized" |
| 404 | Conversation does not exist **or** belongs to another member | "Conversation not found" |
| 500 | Server error | "Internal server error" |

## Business Rules

- BR-AI-003 — conversation ownership comes from authentication. An unknown id
  and another member's id are indistinguishable and both return 404, so the API
  never confirms the existence of a conversation the caller does not own.

## Example

### Request
```bash
curl /api/ai/conversations/3f0a9d21-5f6e-4c11-9a8b-1d2e3f4a5b6c \
  -H "Authorization: Bearer <accessToken>"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Conversation retrieved",
  "data": {
    "id": "3f0a9d21-5f6e-4c11-9a8b-1d2e3f4a5b6c",
    "title": "What can I eat for a high-protein vegan breakfast?",
    "updatedAt": "2026-10-07T18:03:00Z",
    "messages": [
      { "role": "user", "content": "What can I eat for a high-protein vegan breakfast?", "createdAt": "2026-10-07T18:02:59Z" },
      { "role": "assistant", "content": "Try tofu scramble with tempeh bacon and a glass of soy milk.", "createdAt": "2026-10-07T18:03:00Z" }
    ]
  }
}
```

---

## Related

- Feature Spec: `docs/feats/ai-chat-send.md`
- List endpoint: `docs/apis/ai/get-conversations.md`
- Send endpoint: `docs/apis/ai/post-messages.md`
- Business Rules: `docs/brs/ai.md`
