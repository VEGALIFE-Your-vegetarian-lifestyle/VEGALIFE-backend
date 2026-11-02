# API Reference: GET /api/subscriptions/me/history

## Overview
Return the authenticated member's full AI subscription history — every `ai_subscription` row they have ever had, newest first, paginated.

## Endpoint
```
GET /api/subscriptions/me/history
```

## Authentication
JWT Bearer token required. A missing, expired, or invalid token returns `401 Unauthorized`.

## Request

### Path Parameters
None.

### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | integer | No | 0-based page index; default `0`, must be `>= 0`. An index past the last page returns 200 with empty `content` |
| size | integer | No | Page size; default `20`, must be between `1` and `100` |

### Request Body
No request body

## Responses

### Success Response (200)
```json
{
  "success": true,
  "message": "Subscription history retrieved successfully",
  "data": {
    "content": [
      {
        "planCode": "PRO",
        "planName": "Pro",
        "status": "active",
        "startedAt": "2026-10-01T10:15:00Z",
        "renewalDate": "2026-11-01T10:15:00Z",
        "cancelledAt": null,
        "createdAt": "2026-10-01T10:15:01Z"
      },
      {
        "planCode": "PRO",
        "planName": "Pro",
        "status": "cancelled",
        "startedAt": "2026-08-01T08:00:00Z",
        "renewalDate": "2026-09-01T08:00:00Z",
        "cancelledAt": "2026-08-20T14:00:00Z",
        "createdAt": "2026-08-01T08:00:02Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

Envelope fields follow `PageResponse` (`content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`). Rows are ordered by `createdAt` descending — the member's most recent subscription event first.

| Field | Type | Description |
|-------|------|--------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data.content | array | One entry per subscription row, newest first |
| data.content[].planCode | string | Plan code (`FREE`, `PRO`) |
| data.content[].planName | string | Display name of the plan |
| data.content[].status | string | `active`, `past_due`, `scheduled`, `cancelled`, or `expired` |
| data.content[].startedAt | string \| null | ISO-8601 instant the row's period began; for a `scheduled` row, the instant its period will begin (its predecessor's renewal date) |
| data.content[].renewalDate | string \| null | ISO-8601 instant the period ends (the sweep's expiry threshold) |
| data.content[].cancelledAt | string \| null | ISO-8601 instant of cancellation; `null` unless the row was cancelled |
| data.content[].createdAt | string | ISO-8601 instant the row was created (ordering key) |
| data.page / size | integer | Echo of the paging request (with defaults applied) |
| data.totalElements | integer | Total rows for the member across all pages |
| data.totalPages | integer | Total pages for the requested `size` |
| data.first / last | boolean | Whether this is the first / last page |

Subscription ids, `extended_from_id`, and user ids are deliberately never returned.

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `page < 0` or `size` outside 1–100 | "Validation failed" |
| 401 | Missing, expired, or invalid JWT | "Unauthorized" |
| 500 | Server error | "Internal server error" |

A member with no rows gets 200 with empty `content`, `totalElements: 0`, `totalPages: 0` — never 404.

## Business Rules
- Read-only: no row is created or updated.
- BR-SUBS-004 — history exposes the multi-row lifecycle (one row per purchase/extension) that the gate creates.
- Feature spec FR-009: `docs/feats/subscription-lifecycle.md`

## Example

### Request
```bash
curl -X GET "http://localhost:8080/api/subscriptions/me/history?page=0&size=10" \
  -H "Authorization: Bearer <access-token>"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Subscription history retrieved successfully",
  "data": {
    "content": [
      {
        "planCode": "PRO",
        "planName": "Pro",
        "status": "active",
        "startedAt": "2026-10-01T10:15:00Z",
        "renewalDate": "2026-11-01T10:15:00Z",
        "cancelledAt": null,
        "createdAt": "2026-10-01T10:15:01Z"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

## Related
- Feature Spec: `docs/feats/subscription-lifecycle.md`
- Sibling endpoint: `docs/apis/subscriptions/get-me.md` (`GET /api/subscriptions/me`, current state only)
- Sibling endpoint: `docs/apis/subscriptions/post-cancel.md` (`POST /api/subscriptions/me/cancel`)
