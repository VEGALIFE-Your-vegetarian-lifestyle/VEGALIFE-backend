# API Reference: GET /api/subscriptions

## Overview
List every active AI plan with its request quota, description, and price — the
public plan-comparison source for upgrade screens and logged-out visitors. The
quota is a one-off allowance granted per acquisition, not a monthly limit.

## Endpoint
```
GET /api/subscriptions
```

## Authentication
None. The endpoint is public: `GET /api/subscriptions` is `permitAll` in `SecurityConfig`, while `GET /api/subscriptions/me` on the same prefix stays authenticated.

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
  "message": "Plans retrieved successfully",
  "data": [
    {
      "code": "FREE",
      "name": "Free",
      "description": "Free starter credits — 5 AI requests, claimable once per account",
      "requestQuota": 5,
      "price": {
        "amount": 0,
        "currency": "VND"
      }
    },
    {
      "code": "PRO",
      "name": "Pro",
      "description": null,
      "requestQuota": 500,
      "price": {
        "amount": 49000,
        "currency": "VND"
      }
    }
  ]
}
```

| Field | Type | Description |
|-------|------|--------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data | array | Active plans ordered by `sort_order` (FREE first) |
| data[].code | string | Plan code, unique (`FREE`, `PRO`) |
| data[].name | string | Display name |
| data[].description | string \| null | Optional plan blurb shown on the plan card; `null` when unset |
| data[].requestQuota | integer | AI requests granted each time this plan is acquired. Not a monthly limit — grants never expire |
| data[].price.amount | number | Price of the plan (0 for FREE) |
| data[].price.currency | string | ISO-4217 currency code, e.g. `VND` |

Inactive plans (`active = false`) are omitted, including FREE if an
administrator has deactivated it. A plan is claimable when `price.amount` is
`0`; there is no separate `claimable` field. The response is a plain array,
not `PageResponse` — the plan list is a fixed, non-paginated catalogue.

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 500 | Server error, including an empty `ai_plan` table | "Internal server error" |

## Business Rules
- Public endpoint: no token required, and no token is ever inspected.
- Read-only: no plan row is created or updated.
- Every active plan appears exactly once, ordered by `sort_order`.
- `requestQuota` is the live catalogue value: editing it changes every existing
  holder's allowance on their next read (BR-SUBS-010), not only future
  acquisitions.
- Deactivating a plan removes it from this list but does not revoke allowance
  already granted (BR-SUBS-009).
- All numbers come from `ai_plan` rows — changing a quota or price is a data
  change, not a code change (BR-SUBS-002).
- Feature Spec: `docs/feats/request-credits.md`

## Example

### Request
```bash
curl -X GET "http://localhost:8080/api/subscriptions"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Plans retrieved successfully",
  "data": [
    {
      "code": "FREE",
      "name": "Free",
      "description": "Free starter credits — 5 AI requests, claimable once per account",
      "requestQuota": 5,
      "price": { "amount": 0, "currency": "VND" }
    },
    {
      "code": "PRO",
      "name": "Pro",
      "description": null,
      "requestQuota": 500,
      "price": { "amount": 49000, "currency": "VND" }
    }
  ]
}
```

## Related
- Feature Spec: `docs/feats/request-credits.md`
- Sibling endpoint: `docs/apis/subscriptions/get-me.md` (`GET /api/subscriptions/me`, authenticated)
- Acquire a plan: `docs/apis/subscriptions/post-acquire.md` (`POST /api/subscriptions/acquire`)
- Admin CRUD: `docs/apis/admin/plan-crud.md` (`/api/admin/plans`)
- Business Rules: `docs/brs/subscriptions.md`
