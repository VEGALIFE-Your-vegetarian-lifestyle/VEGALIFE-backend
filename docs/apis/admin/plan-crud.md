# API Reference: Admin AI Plan CRUD

## Overview

Admin-only CRUD for `ai_plan` rows (issue #119). Every subscription/payment API reads plan quotas and prices from `ai_plan` (BR-SUBS-002); this is the only runtime write path — plans can otherwise only be seeded by a Flyway migration. Each plan carries a one-off `requestQuota` (not a monthly limit) and an optional `description`.

## Endpoints

```text
GET    /api/admin/plans
POST   /api/admin/plans
PATCH  /api/admin/plans/{planId}
DELETE /api/admin/plans/{planId}
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`), role `ADMIN`.

## GET /api/admin/plans

Lists every plan, including inactive ones (the public `GET /api/subscriptions` only shows active plans). No pagination, no filters.

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Plans retrieved successfully",
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "code": "FREE",
      "name": "Free",
      "description": "Free starter credits — 5 AI requests, claimable once per account",
      "requestQuota": 5,
      "priceAmount": 0,
      "priceCurrency": "VND",
      "active": true,
      "sortOrder": 1,
      "createdAt": "2026-01-01T00:00:00Z",
      "updatedAt": "2026-01-01T00:00:00Z"
    }
  ]
}
```

Sorted by `sortOrder` ascending.

## POST /api/admin/plans

### Request Body

```json
{
  "code": "PREMIUM",
  "name": "Premium",
  "description": "500 AI requests, one-off",
  "requestQuota": 500,
  "priceAmount": 99000,
  "priceCurrency": "VND",
  "active": true,
  "sortOrder": 3
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| code | string | Yes | 1-20 uppercase letters/digits/underscores, unique. Normalized to uppercase. |
| name | string | Yes | At most 50 characters. |
| description | string | No | At most 500 characters; shown on the plan card. |
| requestQuota | integer | Yes | >= 0. Requests granted each time the plan is acquired; grants never expire. |
| priceAmount | integer | Yes | >= 0, smallest currency unit (VND has no decimals). |
| priceCurrency | string | Yes | 3-letter ISO code (normalized to uppercase); `VND` for a purchasable plan. |
| active | boolean | Yes | Whether the plan is immediately acquirable/visible publicly. |
| sortOrder | integer | Yes | Display order, ascending. |

### Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 201 | Created | `Plan created successfully` |
| 400 | Missing/invalid field | `Validation failed` |
| 401 | JWT missing/invalid/expired | `Unauthorized` |
| 403 | Authenticated but not an Administrator | `Forbidden` |
| 409 | `code` already exists (case-insensitively normalized to uppercase) | `Plan code already exists` |

A newly created `active: true` plan with `priceAmount > 0` and `priceCurrency: "VND"` is purchasable through `POST /api/subscriptions/acquire` with no code change. A plan with `priceAmount: 0` is **claimable** through the same endpoint — there is no `claimable` flag to set (BR-SUBS-008).

## PATCH /api/admin/plans/{planId}

Partial update of any mutable field. `code` can never be changed (it is referenced by `ai_subscription.plan_id`).

### Request Body

All fields optional; at least one must be supplied.

```json
{ "requestQuota": 800 }
```

| Field | Type | Description |
|-------|------|-------------|
| name | string | Not blank if supplied, at most 50 characters. |
| description | string | At most 500 characters. |
| requestQuota | integer | >= 0. Retroactive: existing holders see the new allowance on their next read (BR-SUBS-010). |
| priceAmount | integer | >= 0. Affects future checkouts only; an in-flight order keeps its frozen amount (BR-PAY-003). |
| priceCurrency | string | 3-letter ISO code. |
| active | boolean | Setting `false` on any plan — including `FREE` — is allowed and stops new acquisitions only (BR-SUBS-009). |
| sortOrder | integer | — |

### Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 200 | Updated | `Plan updated successfully` |
| 400 | No field supplied, or an invalid value | `Validation failed` |
| 401 | JWT missing/invalid/expired | `Unauthorized` |
| 403 | Authenticated but not an Administrator | `Forbidden` |
| 404 | Plan does not exist | `Plan not found` |

Deactivating the `FREE` plan is permitted: it disappears from `GET /api/subscriptions`, `POST /api/subscriptions/acquire` answers `400 "Plan is not available"`, and every member who already claimed it keeps the allowance it granted. Reactivating the plan makes it acquirable again, but a member who already claimed it cannot claim it a second time (BR-SUBS-008).

## DELETE /api/admin/plans/{planId}

Soft-deactivates a plan (`active = false`) — never a hard delete, so historical acquisition rows stay resolvable and keep contributing allowance.

### Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 200 | Deactivated | `Plan deactivated successfully` |
| 401 | JWT missing/invalid/expired | `Unauthorized` |
| 403 | Authenticated but not an Administrator | `Forbidden` |
| 404 | Plan does not exist | `Plan not found` |

`DELETE` on the `FREE` plan now succeeds (`200`). The old `409 "FREE plan must always remain active"` guard is gone.

## Business Rules

- Only an Administrator may create, edit, or deactivate a plan.
- `code` is immutable once created.
- No plan is ever hard-deleted (BR-SUBS-011); `DELETE` is a soft deactivation on every plan, including `FREE`.
- Deactivation stops new acquisition but never revokes allowance already granted; the allowance query ignores `active` (BR-SUBS-009).
- `requestQuota` edits are retroactive for every holder (BR-SUBS-010).
- All endpoints live under `/api/admin/**`; the path-based `ROLE_ADMIN` gate in `SecurityConfig` applies automatically.

## Related

- Public plan list: `docs/apis/subscriptions/get-subscriptions.md`
- Acquire a plan: `docs/apis/subscriptions/post-acquire.md`
- Feature Spec: `docs/feats/request-credits.md`
- Business Rules: `docs/brs/subscriptions.md`
- Shared error/response contract: `docs/apis/error-responses.md`
