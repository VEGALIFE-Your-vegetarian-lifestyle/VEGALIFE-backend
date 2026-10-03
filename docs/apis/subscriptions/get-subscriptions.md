# API Reference: GET /api/subscriptions

## Overview
List every active AI subscription plan with its monthly limit, price, and per-feature flags — the public plan-comparison source for upgrade screens and logged-out visitors.

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
      },
      "features": [
        {
          "key": "ai_chat",
          "enabled": true,
          "description": "Chat with the vegan AI assistant"
        },
        {
          "key": "video_summary",
          "enabled": false,
          "description": "Summarize cooking videos"
        },
        {
          "key": "weekly_meal_plan",
          "enabled": false,
          "description": "Generate a weekly meal plan"
        }
      ]
    },
    {
      "code": "PRO",
      "name": "Pro",
      "monthlyRequestLimit": 500,
      "price": {
        "amount": 49000,
        "currency": "VND"
      },
      "features": [
        {
          "key": "ai_chat",
          "enabled": true,
          "description": "Chat with the vegan AI assistant"
        },
        {
          "key": "video_summary",
          "enabled": true,
          "description": "Summarize cooking videos"
        },
        {
          "key": "weekly_meal_plan",
          "enabled": true,
          "description": "Generate a weekly meal plan"
        }
      ]
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
| data[].features | array | Feature flags of this plan ordered by `key` |
| data[].features[].key | string | Stable feature identifier (`ai_chat`, `video_summary`, `weekly_meal_plan`) |
| data[].features[].enabled | boolean | Whether this plan includes the feature |
| data[].features[].description | string | Human-readable description of the feature |

Inactive plans (`active = false`) are omitted. The response is a plain array, not `PageResponse` — the plan list is a fixed, non-paginated catalogue.

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 500 | Server error, including an empty `ai_plan` table | "Internal server error" |

## Business Rules
- Public endpoint: no token required, and no token is ever inspected (FR-006).
- Read-only: no plan or feature row is created or updated (FR-008).
- Every active plan appears exactly once, ordered by `sort_order`; each feature appears once per plan, ordered by `feature_key`.
- All numbers come from `ai_plan` / `ai_plan_feature` rows — changing a limit or price is a data change, not a code change (NFR-MAINT-001).
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
      "price": { "amount": 0, "currency": "VND" },
      "features": [
        { "key": "ai_chat", "enabled": true, "description": "Chat with the vegan AI assistant" },
        { "key": "video_summary", "enabled": false, "description": "Summarize cooking videos" },
        { "key": "weekly_meal_plan", "enabled": false, "description": "Generate a weekly meal plan" }
      ]
    },
    {
      "code": "PRO",
      "name": "Pro",
      "monthlyRequestLimit": 500,
      "price": { "amount": 49000, "currency": "VND" },
      "features": [
        { "key": "ai_chat", "enabled": true, "description": "Chat with the vegan AI assistant" },
        { "key": "video_summary", "enabled": true, "description": "Summarize cooking videos" },
        { "key": "weekly_meal_plan", "enabled": true, "description": "Generate a weekly meal plan" }
      ]
    }
  ]
}
```

## Related
- Feature Spec: `docs/feats/subscription-api.md`
- Sibling endpoint: `docs/apis/subscriptions/get-me.md` (`GET /api/subscriptions/me`, authenticated)
