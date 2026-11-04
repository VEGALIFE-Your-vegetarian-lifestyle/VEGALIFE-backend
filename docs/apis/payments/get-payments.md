# API Reference: GET /api/payments

## Overview
Page through the caller's own payments, newest first, as a money-only view of
`payment_ledger`: amount, currency, status, provider, reference and timestamps.
Pure ledger read — no gateway call, no state change, no plan context.

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
        "createdAt": "2026-10-07T09:14:58Z"
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
| data.page | integer | Zero-based page index echoed back |
| data.size | integer | Page size applied |
| data.totalElements | number | Total rows matching the caller |
| data.totalPages | number | Total pages at this size |
| data.first / data.last | boolean | Boundary flags for pagination controls |

Rows are always ordered `createdAt` descending — no sort parameter.

The ledger is money-only: this endpoint **does not** return a `plan` or a
`subscription` block. To see which plan a payment bought, read
`GET /api/subscriptions/me/history` and match on `paymentId`.

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `page`/`size` out of validated range | "Validation failed" |
| 401 | No token / invalid or expired token | "Unauthorized" |
| 500 | Unexpected failure | "An unexpected error occurred" |

## Business Rules
- BR-PAY-004: The endpoint never writes — listing payments can neither re-fulfil a payment nor alter an acquisition.
- BR-PAY-010: `payment_ledger` records money only and identifies no plan; the acquisition does. A payment may exist with no granted acquisition (pending or failed).
- ADR-008: Read-only; no gateway interaction of any kind.
- Feature Spec: `docs/feats/request-credits.md`

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
        "createdAt": "2026-10-07T09:14:58Z"
      },
      {
        "id": "b2f4a0d3-1c58-4e2a-9f61-8d3a7c4e5b92",
        "amount": 0,
        "currency": "VND",
        "status": "failed",
        "provider": "vnpay",
        "txnRef": "VNP1727944811",
        "paidAt": null,
        "createdAt": "2026-10-06T18:02:11Z"
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
- Feature Spec: `docs/feats/request-credits.md`
- Sibling endpoint: `docs/apis/payments/get-payments-paymentid.md` (`GET /api/payments/{paymentId}`)
- Plan context: `docs/apis/subscriptions/get-history.md` (`GET /api/subscriptions/me/history`, acquisitions)
- Admin counterpart: `docs/apis/admin/get-payments.md` (`GET /api/admin/payments`)
- Business Rules: `docs/brs/payments.md`
- ADR: `docs/adrs/008-vnpay-integration.md`
