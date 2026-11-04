# API Reference: GET /api/subscriptions/me

## Overview
Return the authenticated member's remaining AI request allowance — one number,
derived from their granted acquisitions and lifetime consumption. This endpoint
no longer reports a tier, a status, a plan, a usage window, or a latest
payment.

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
  "message": "Remaining usage retrieved successfully",
  "data": {
    "remainingUsage": 5
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data.remainingUsage | integer | `max(allowance − consumed, 0)`. Never negative; `0` when the member has no granted acquisitions or has consumed everything |

`remainingUsage` is the **only** usage number any API returns. `used`, the
plan limit, and any window start/end are not exposed anywhere.

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | Missing, expired, or invalid JWT | "Unauthorized" |
| 500 | Server error | "Internal server error" |

## Business Rules
- Read-only: no row is created or updated. An account with no acquisitions is
  not given a plan at read time (BR-SUBS-007).
- `allowance` is the live `SUM(ai_plan.request_quota)` over the member's
  `ai_subscription` rows with `granted_at IS NOT NULL`, **not** filtered by
  `ai_plan.active`. `consumed` is `ai_usage.request_count`, a lifetime counter.
  Nothing is stored on `user` and nothing is snapshotted (BR-SUBS-007).
- A plan quota edit is retroactive: the next read already reflects it
  (BR-SUBS-010).
- Existing accounts have no acquisition rows, so they read `0` until they
  explicitly claim the free plan through `POST /api/subscriptions/acquire`.
- Deactivating a plan does not change the allowance of members who already hold
  it (BR-SUBS-009).
- Feature Spec: `docs/feats/request-credits.md`

## Example

### Request
```bash
curl -X GET "http://localhost:8080/api/subscriptions/me" \
  -H "Authorization: Bearer <access-token>"
```

### Before claiming the free plan (200)
```json
{
  "success": true,
  "message": "Remaining usage retrieved successfully",
  "data": { "remainingUsage": 0 }
}
```

### After claiming FREE and buying PRO once (200)
```json
{
  "success": true,
  "message": "Remaining usage retrieved successfully",
  "data": { "remainingUsage": 488 }
}
```

(5 free requests + 500 PRO requests − 17 consumed.)

## Related
- Feature Spec: `docs/feats/request-credits.md`
- ADR: `docs/adrs/021-request-allowance-as-acquisition-sum.md`
- Acquire a plan: `docs/apis/subscriptions/post-acquire.md` (`POST /api/subscriptions/acquire`)
- Acquisition history: `docs/apis/subscriptions/get-history.md` (`GET /api/subscriptions/me/history`)
- Plan catalogue: `docs/apis/subscriptions/get-subscriptions.md` (`GET /api/subscriptions`, public plan list)
- Business Rules: `docs/brs/subscriptions.md`
