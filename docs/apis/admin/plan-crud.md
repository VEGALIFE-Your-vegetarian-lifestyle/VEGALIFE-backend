# API Reference: Admin AI Plan CRUD

## Overview

Admin-only CRUD for `ai_plan` rows (issue #119). Every subscription/payment API reads plan limits and prices from `ai_plan` (BR-SUBS-001/002); this is the only runtime write path — previously plans could only be seeded by a Flyway migration.

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
      "monthlyRequestLimit": 20,
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
  "monthlyRequestLimit": 500,
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
| monthlyRequestLimit | integer | Yes | >= 0. |
| priceAmount | integer | Yes | >= 0, smallest currency unit (VND has no decimals). |
| priceCurrency | string | Yes | 3-letter ISO code (normalized to uppercase); `VND` for a purchasable plan. |
| active | boolean | Yes | Whether the plan is immediately purchasable/visible publicly. |
| sortOrder | integer | Yes | Display order, ascending. |

### Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 201 | Created | `Plan created successfully` |
| 400 | Missing/invalid field | `Validation failed` |
| 401 | JWT missing/invalid/expired | `Unauthorized` |
| 403 | Authenticated but not an Administrator | `Forbidden` |
| 409 | `code` already exists (case-insensitively normalized to uppercase) | `Plan code already exists` |

A newly created `active: true` plan with `priceAmount > 0` and `priceCurrency: "VND"` is purchasable through the existing checkout flow with no code change (BR-SUBS-002: limits/prices come only from `ai_plan` rows).

## PATCH /api/admin/plans/{planId}

Partial update of any mutable field. `code` can never be changed (it is referenced by `ai_subscription.plan_id` and `payment_ledger.plan_id`).

### Request Body

All fields optional; at least one must be supplied.

```json
{ "priceAmount": 79000 }
```

| Field | Type | Description |
|-------|------|-------------|
| name | string | Not blank if supplied, at most 50 characters. |
| monthlyRequestLimit | integer | >= 0. |
| priceAmount | integer | >= 0. |
| priceCurrency | string | 3-letter ISO code. |
| active | boolean | Setting `false` on the `FREE` plan is rejected (see below). |
| sortOrder | integer | — |

### Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 200 | Updated | `Plan updated successfully` |
| 400 | No field supplied, or an invalid value | `Validation failed` |
| 401 | JWT missing/invalid/expired | `Unauthorized` |
| 403 | Authenticated but not an Administrator | `Forbidden` |
| 404 | Plan does not exist | `Plan not found` |
| 409 | Attempt to set `active: false` on the `FREE` plan | `FREE plan must always remain active` |

## DELETE /api/admin/plans/{planId}

Soft-deactivates a plan (`active = false`) — never a hard delete, so historical subscription/payment rows stay resolvable.

### Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 200 | Deactivated | `Plan deactivated successfully` |
| 401 | JWT missing/invalid/expired | `Unauthorized` |
| 403 | Authenticated but not an Administrator | `Forbidden` |
| 404 | Plan does not exist | `Plan not found` |
| 409 | Target plan's `code` is `FREE` | `FREE plan must always remain active` |

## Business Rules

- Only an Administrator may create, edit, or deactivate a plan.
- `code` is immutable once created.
- The `FREE` plan (BR-SUBS-003's default fallback) can never be deactivated, by either `PATCH .../active=false` or `DELETE`.
- All endpoints live under `/api/admin/**`; the path-based `ROLE_ADMIN` gate in `SecurityConfig` applies automatically.

## Related

- Public plan list: `docs/apis/subscriptions/get-subscriptions.md`
- Checkout: `docs/apis/payments/post-checkout.md`
- Shared error/response contract: `docs/apis/error-responses.md`
