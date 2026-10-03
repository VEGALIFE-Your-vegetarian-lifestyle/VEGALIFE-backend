# API Reference: GET /api/subscriptions

## Overview
List every active AI subscription plan with its monthly limit and price — the public plan-comparison source for upgrade screens and logged-out visitors.

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
      "monthlyRequestLimit": 20,
      "price": {
        "amount": 0,
        "currency": "VND"
      }
    },
    {
      "code": "PRO",
      "name": "Pro",
      "monthlyRequestLimit": 500,
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
| data[].monthlyRequestLimit | integer | AI requests included per month |
| data[].price.amount | number | Price of the plan (0 for FREE) |
| data[].price.currency | string | ISO-4217 currency code, e.g. `VND` |

Inactive plans (`active = false`) are omitted. The response is a plain array, not `PageResponse` — the plan list is a fixed, non-paginated catalogue.

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 500 | Server error, including an empty `ai_plan` table | "Internal server error" |

## Business Rules
- Public endpoint: no token required, and no token is ever inspected (FR-006).
- Read-only: no plan row is created or updated (FR-008).
- Every active plan appears exactly once, ordered by `sort_order`.
- All numbers come from `ai_plan` rows — changing a limit or price is a data change, not a code change (NFR-MAINT-001).
- Feature Spec: `docs/feats/subscription-api.md`

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
      "monthlyRequestLimit": 20,
      "price": { "amount": 0, "currency": "VND" }
    },
    {
      "code": "PRO",
      "name": "Pro",
      "monthlyRequestLimit": 500,
      "price": { "amount": 49000, "currency": "VND" }
    }
  ]
}
```

## Related
- Feature Spec: `docs/feats/subscription-api.md`
- Sibling endpoint: `docs/apis/subscriptions/get-me.md` (`GET /api/subscriptions/me`, authenticated)
