# API Reference: GET /api/payments

## Overview
Page through the caller's own payment history, newest first, with each row carrying the subscription it produced and the plan it was for. Pure `payment_ledger` read: no gateway call, no state change.

## Endpoint
```
GET /api/payments
```

## Authentication
**Required — JWT Bearer.** The path falls under the default `authenticated()` rule for `/api/payments/**` (only `/api/payments/vnpay/ipn` is `permitAll`). The caller's id comes from the token and is part of the query predicate, so a member only ever sees their own rows.

## Request

### Path Parameters
None.

### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | integer ≥ 0 | no | Zero-based page index. Default `0`. Out-of-range pages return `200` with empty `content`. |
| size | integer 1–100 | no | Page size. Default `20`. |

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
        "amount": 49000,
        "currency": "VND",
        "status": "succeeded",
        "provider": "vnpay",
        "txnRef": "VNP1728030123",
        "paidAt": "2026-10-07T09:15:42Z",
        "createdAt": "2026-10-07T09:14:58Z",
        "subscription": {
          "id": "3f1d2a44-88ab-4c9e-b1a7-5c2e9d7f6a10",
          "status": "active",
          "startedAt": "2026-10-07T09:15:42Z",
          "renewalDate": "2026-11-07T09:15:42Z"
        },
        "plan": {
          "code": "PRO",
          "name": "Pro",
          "monthlyRequestLimit": 500,
          "price": { "amount": 49000, "currency": "VND" }
        }
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 7,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data | object | `PageResponse` payload |
| data.content | array | One item per ledger row, newest first |
| data.content[].id | string (UUID) | Ledger row id (usable with `GET /api/payments/{paymentId}`) |
| data.content[].amount | number | Amount in whole VND |
| data.content[].currency | string | Ledger currency, currently always `VND` |
| data.content[].status | string | `pending`, `succeeded`, `failed`, or `refunded` |
| data.content[].provider | string | Payment provider, currently `vnpay` |
| data.content[].txnRef | string | Gateway transaction reference |
| data.content[].paidAt | string (ISO-8601 UTC) \| null | When the IPN webhook recorded success; `null` until then |
| data.content[].createdAt | string (ISO-8601 UTC) | When the row was written at checkout |
| data.content[].subscription | object \| null | Subscription this payment produced: `{id, status, startedAt, renewalDate}`; `null` when the payment never activated one (pending/failed/refunded, or written before this feature) |
| data.content[].plan | object \| null | Full plan object, same shape as `PlanSummaryResponse`; `null` only if the plan row was removed |
| data.page | integer | Zero-based page index echoed back |
| data.size | integer | Page size applied |
| data.totalElements | number | Total rows matching the caller |
| data.totalPages | number | Total pages at this size |
| data.first / data.last | boolean | Boundary flags for pagination controls |

Rows are always ordered `createdAt` descending — no sort parameter.

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `page`/`size` out of validated range | "Validation failed" |
| 401 | No token / invalid or expired token | "Unauthorized" |
| 500 | Unexpected failure | "An unexpected error occurred" |

## Business Rules
- BR-PAY-004: The endpoint never writes — listing payments can neither re-fulfil a payment nor alter a subscription.
- ADR-008: Read-only; no gateway interaction of any kind.
- Feature Spec: `docs/feats/payment-history.md`

## Example

### Request
```bash
curl "/api/payments?page=0&size=2" \
  -H "Authorization: Bearer <jwt>"
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
        "amount": 49000,
        "currency": "VND",
        "status": "succeeded",
        "provider": "vnpay",
        "txnRef": "VNP1728030123",
        "paidAt": "2026-10-07T09:15:42Z",
        "createdAt": "2026-10-07T09:14:58Z",
        "subscription": {
          "id": "3f1d2a44-88ab-4c9e-b1a7-5c2e9d7f6a10",
          "status": "active",
          "startedAt": "2026-10-07T09:15:42Z",
          "renewalDate": "2026-11-07T09:15:42Z"
        },
        "plan": {
          "code": "PRO",
          "name": "Pro",
          "monthlyRequestLimit": 500,
          "price": { "amount": 49000, "currency": "VND" }
        }
      },
      {
        "id": "b2f4a0d3-1c58-4e2a-9f61-8d3a7c4e5b92",
        "amount": 0,
        "currency": "VND",
        "status": "failed",
        "provider": "vnpay",
        "txnRef": "VNP1727944811",
        "paidAt": null,
        "createdAt": "2026-10-06T18:02:11Z",
        "subscription": null,
        "plan": {
          "code": "PRO",
          "name": "Pro",
          "monthlyRequestLimit": 500,
          "price": { "amount": 49000, "currency": "VND" }
        }
      }
    ],
    "page": 0,
    "size": 2,
    "totalElements": 7,
    "totalPages": 4,
    "first": true,
    "last": false
  }
}
```

## Related
- Feature Spec: `docs/feats/payment-history.md`
- Sibling endpoints: `docs/apis/payments/get-payments-paymentid.md` (`GET /api/payments/{paymentId}`), `docs/apis/payments/post-checkout.md` (`POST /api/payments/checkout`)
- Admin counterpart: `docs/apis/admin/get-payments.md` (`GET /api/admin/payments`)
- Business Rules: `docs/brs/payments.md`
- ADR: `docs/adrs/008-vnpay-integration.md`
