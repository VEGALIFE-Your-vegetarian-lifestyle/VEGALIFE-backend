# API Reference: POST /api/subscriptions/me/cancel

## Overview
Cancel the authenticated member's AI subscription immediately: the row in effect and every scheduled successor become `cancelled`, ending paid access at once.

## Endpoint
```
POST /api/subscriptions/me/cancel
```

## Authentication
JWT Bearer token required. A missing, expired, or invalid token returns `401 Unauthorized`.

## Request

### Path Parameters
None.

### Query Parameters
None.

### Request Body
No request body

## Responses

### Success Response (200)
```json
{
  "success": true,
  "message": "Subscription cancelled successfully",
  "data": {
    "status": "cancelled",
    "cancelledAt": "2026-10-07T09:30:00Z"
  }
}
```

| Field | Type | Description |
|-------|------|--------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data.status | string | Always `cancelled` — the status the member's rows now hold |
| data.cancelledAt | string | ISO-8601 instant of the cancellation, identical on every row cancelled in this call |

Cancellation is one transaction: the member's row in effect (currently only `active`; `past_due` included for completeness) and **every** `scheduled` row of that member receive `status = 'cancelled'` and this `cancelledAt`. After the response, no row of the member is in effect — the next `GET /api/subscriptions/me` reads FREE.

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Server-side validation failure | "Validation failed" |
| 401 | Missing, expired, or invalid JWT | "Unauthorized" |
| 409 | No row in effect: member is already cancelled/expired/FREE, or this endpoint was called twice | "Subscription already cancelled" |
| 500 | Server error, including a missing seeded `FREE` plan row on the follow-up read | "Internal server error" |

A 409 writes nothing — it is a pure guard, not a partial update.

## Business Rules
- BR-SUBS-005 — cancellation is immediate, cascades to every `scheduled` successor, and is repeat-safe (409, no writes).
- BR-SUBS-003 (amended) — with no row in effect the member reads as FREE; reads still never write.
- Feature spec FR-003 / FR-004 / FR-005: `docs/feats/subscription-lifecycle.md`

## Example

### Request
```bash
curl -X POST "http://localhost:8080/api/subscriptions/me/cancel" \
  -H "Authorization: Bearer <access-token>"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Subscription cancelled successfully",
  "data": {
    "status": "cancelled",
    "cancelledAt": "2026-10-07T09:30:00Z"
  }
}
```

### Repeat Call (409)
```json
{
  "success": false,
  "message": "Subscription already cancelled",
  "data": null
}
```

## Related
- Feature Spec: `docs/feats/subscription-lifecycle.md`
- Sibling endpoint: `docs/apis/subscriptions/get-history.md` (`GET /api/subscriptions/me/history`)
- Sibling endpoint: `docs/apis/subscriptions/get-me.md` (`GET /api/subscriptions/me`, reads FREE after cancel)
