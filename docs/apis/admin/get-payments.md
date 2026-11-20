# API Reference: GET /api/admin/payments

## Overview
Page through every payment across all users, filterable by user, status, and
date range — the admin counterpart of `GET /api/payments`, for support and
dispute handling. Pure `payment_ledger` read, money-only: no plan and no
subscription context.

## Endpoint
```
GET /api/admin/payments
```

## Authentication
**Required — JWT Bearer with `ROLE_ADMIN`.** The path is covered by the existing `SecurityConfig` rule `/api/admin/**` → `hasRole("ADMIN")`; an authenticated non-admin receives `403`.

## Request

### Path Parameters
None.

### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | integer ≥ 0 | no | Zero-based page index. Default `0`. Out-of-range pages return `200` with empty `content`. |
| size | integer 1–100 | no | Page size. Default `20`. |
| userId | UUID string | no | Only rows belonging to this user. |
| status | string | no | Ledger status: `pending`, `succeeded`, `failed`, or `refunded`. |
| createdFrom | ISO-8601 date-time | no | Inclusive lower bound on `createdAt`. |
| createdTo | ISO-8601 date-time | no | Inclusive upper bound on `createdAt`. |

All filters are optional and combined with AND (same semantics as `GET /api/admin/users`).

### Request Body
No request body.

## Responses

### Success Response (200)
```json
{
  "success": true,
  "message": "Payment history retrieved successfully",
  "data": {
    "content": [
      {
        "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
        "userId": "550e8400-e29b-41d4-a716-446655440000",
        "amount": 49000,
        "currency": "VND",
        "status": "succeeded",
        "provider": "vnpay",
        "txnRef": "VNP1728030123",
        "paidAt": "2026-10-07T09:15:42Z",
        "createdAt": "2026-10-07T09:14:58Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

Item fields are identical to `GET /api/payments` (`docs/apis/payments/get-payments.md`), plus:

| Field | Type | Description |
|-------|------|-------------|
| data.content[].userId | string (UUID) | The paying user — present on every admin row (omitted from the member endpoint's responses) |

The item no longer carries `plan` or a nested `subscription` block. To resolve
which plan a payment bought, join the acquisition history
(`GET /api/subscriptions/me/history` for that `userId`) on `paymentId`, or query
`ai_subscription` by `payment_id`.

Rows are ordered `createdAt` descending.

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Invalid filter value (`userId`, `status`, date format) or `page`/`size` out of range | "Validation failed" |
| 401 | No token / invalid or expired token | "Unauthorized" |
| 403 | Authenticated non-admin | "Forbidden" |
| 500 | Unexpected failure | "An unexpected error occurred" |

## Business Rules
- BR-PAY-004: The endpoint never writes — it is a pure ledger read.
- BR-PAY-010: `payment_ledger` records money only; the plan lives on the acquisition.
- ADR-008: No gateway interaction of any kind.
- Feature Spec: `docs/feats/request-credits.md`

## Example

### Request
```bash
curl "/api/admin/payments?userId=550e8400-e29b-41d4-a716-446655440000&status=succeeded&createdFrom=2026-10-01T00:00:00Z&size=20" \
  -H "Authorization: Bearer <admin-jwt>"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Payment history retrieved successfully",
  "data": {
    "content": [
      {
        "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
        "userId": "550e8400-e29b-41d4-a716-446655440000",
        "amount": 49000,
        "currency": "VND",
        "status": "succeeded",
        "provider": "vnpay",
        "txnRef": "VNP1728030123",
        "paidAt": "2026-10-07T09:15:42Z",
        "createdAt": "2026-10-07T09:14:58Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

## Related
- Feature Spec: `docs/feats/request-credits.md`
- Member counterpart: `docs/apis/payments/get-payments.md` (`GET /api/payments`)
- Plan context for a payment: `docs/apis/subscriptions/get-history.md` (`GET /api/subscriptions/me/history`)
- Admin list precedent: `docs/apis/admin/get-users.md` (`GET /api/admin/users`)
- Business Rules: `docs/brs/payments.md`
