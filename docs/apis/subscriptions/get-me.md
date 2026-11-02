# API Reference: GET /api/subscriptions/me

## Overview
Return the authenticated member's AI subscription: current tier and status, monthly quota usage, renewal date, plan summary, and latest successful payment.

## Endpoint
```
GET /api/subscriptions/me
```

## Authentication
JWT Bearer token required. A missing, expired, or invalid token returns `401 Unauthorized`. (Unlike `GET /api/subscriptions`, this endpoint is not public.)

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
  "message": "Subscription retrieved successfully",
  "data": {
    "tier": "FREE",
    "status": "active",
    "renewalDate": null,
    "usage": {
      "used": 0,
      "limit": 20,
      "periodStart": "2026-10-01T00:00:00Z",
      "periodEnd": "2026-11-01T00:00:00Z"
    },
    "currentPlan": {
      "code": "FREE",
      "name": "Free",
      "monthlyRequestLimit": 20,
      "price": {
        "amount": 0,
        "currency": "VND"
      }
    },
    "latestPayment": null
  }
}
```

| Field | Type | Description |
|-------|------|--------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data.tier | string | Plan code of the effective plan (`FREE`, `PRO`) |
| data.status | string | Subscription status (`active`, `cancelled`, `past_due`). Defaults to `active` when the member has no subscription row |
| data.renewalDate | string \| null | ISO-8601 instant of the subscription period end; `null` for the synthesized FREE default |
| data.usage.used | integer | AI requests already made in the current period (0 when none) |
| data.usage.limit | integer | `monthly_request_limit` of the effective plan |
| data.usage.periodStart | string | ISO-8601 instant the usage window opens (first instant of the current UTC month) |
| data.usage.periodEnd | string | ISO-8601 instant the usage window closes (first instant of the next UTC month) |
| data.currentPlan.code | string | Plan code, identical to `data.tier` |
| data.currentPlan.name | string | Display name of the plan |
| data.currentPlan.monthlyRequestLimit | integer | Monthly request allowance — same value as `data.usage.limit` |
| data.currentPlan.price.amount | number | Price of the plan (0 for FREE) |
| data.currentPlan.price.currency | string | ISO-4217 currency code, e.g. `VND` |
| data.latestPayment | object \| null | Latest succeeded payment, or `null` when none exists |
| data.latestPayment.planCode | string | Plan the payment was for |
| data.latestPayment.amount | number | Amount charged |
| data.latestPayment.currency | string | ISO-4217 currency code |
| data.latestPayment.status | string | Always `succeeded` for this field |
| data.latestPayment.provider | string \| null | Payment provider identifier; `null` when unknown |
| data.latestPayment.paidAt | string | ISO-8601 instant the payment succeeded |

Subscription ids, user ids, ledger ids, and `provider_reference` are deliberately never returned.

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | Missing, expired, or invalid JWT | "Unauthorized" |
| 500 | Server error, including a missing seeded `FREE` plan row | "Internal server error" |

## Business Rules
- Read-only: no row is created or updated (FR-008).
- No `ai_subscription` row means the FREE default is synthesized from the `ai_plan` row with `code = 'FREE'` — the response is identical to a real FREE subscription, except `renewalDate` is `null`. No row is written (FR-003).
- `usage` is the current UTC calendar month; `used` sums `ai_usage.request_count` over rows whose `[period_start, period_end)` overlaps that window (FR-004).
- `latestPayment` is the newest `payment_ledger` row with `status = 'succeeded'` by `paid_at`, or `null` (FR-005).
- Feature Spec: `docs/feats/subscription-api.md`

## Example

### Request
```bash
curl -X GET "http://localhost:8080/api/subscriptions/me" \
  -H "Authorization: Bearer <access-token>"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Subscription retrieved successfully",
  "data": {
    "tier": "PRO",
    "status": "active",
    "renewalDate": "2026-11-01T00:00:00Z",
    "usage": {
      "used": 42,
      "limit": 500,
      "periodStart": "2026-10-01T00:00:00Z",
      "periodEnd": "2026-11-01T00:00:00Z"
    },
    "currentPlan": {
      "code": "PRO",
      "name": "Pro",
      "monthlyRequestLimit": 500,
      "price": {
        "amount": 49000,
        "currency": "VND"
      }
    },
    "latestPayment": {
      "planCode": "PRO",
      "amount": 49000,
      "currency": "VND",
      "status": "succeeded",
      "provider": "momo",
      "paidAt": "2026-10-01T10:15:00Z"
    }
  }
}
```

## Related
- Feature Spec: `docs/feats/subscription-api.md`
- Sibling endpoint: `docs/apis/subscriptions/get-subscriptions.md` (`GET /api/subscriptions`, public plan list)
- Usage source: `ai_usage` table — migration `V10__create_ai_tables.sql`
