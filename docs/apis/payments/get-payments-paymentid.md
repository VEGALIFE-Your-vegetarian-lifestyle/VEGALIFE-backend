# API Reference: GET /api/payments/{paymentId}

## Overview
Read the ledger state of one payment owned by the caller — the endpoint the frontend result page polls after VNPay redirects the browser back. Pure `payment_ledger` read: no gateway call, no QueryDR, no state change, and no plan context (the ledger records money only).

## Endpoint
```
GET /api/payments/{paymentId}
```

## Authentication
**Required — JWT Bearer.** The path falls under the default `authenticated()` rule for `/api/payments/**` (only `/api/payments/vnpay/ipn` is `permitAll`). The caller's id is taken from the token and is part of the lookup predicate, so a member can only ever read their own rows.

## Request

### Path Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| paymentId | UUID string | yes | The payment's id, as returned in `POST /api/subscriptions/acquire`'s `paymentId` |

### Query Parameters
None.

### Request Body
No request body.

## Responses

### Success Response (200)
```json
{
  "success": true,
  "message": "Payment retrieved successfully",
  "data": {
    "paymentId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "pending",
    "amount": 49000,
    "currency": "VND",
    "createdAt": "2026-10-04T10:15:00Z",
    "paidAt": null
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data.paymentId | string (UUID) | The payment's id (echo of the path parameter) |
| data.status | string | Ledger status: `pending`, `succeeded`, `failed`, or `refunded` |
| data.amount | number | Ledger amount in whole VND |
| data.currency | string | Ledger currency, currently always `VND` |
| data.createdAt | string (ISO-8601 UTC) | When the row was written at checkout |
| data.paidAt | string (ISO-8601 UTC) \| null | When the IPN webhook recorded success; `null` until then (and for `failed`) |

`status` and `paidAt` always reflect the stored row as-is. For a payment the IPN has not yet fulfilled: `status = "pending"`, `paidAt = null`. After fulfilment: final `status` with `paidAt` set.

There is no `plan` field. To resolve the plan or the grant this payment produced, read `GET /api/subscriptions/me/history` and match `paymentId`.

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | No token / invalid or expired token | "Unauthorized" |
| 404 | Payment id does not exist, **or** exists but belongs to another user | "Payment not found" |
| 500 | Unexpected failure | "An unexpected error occurred" |

A foreign id and an unknown id are deliberately indistinguishable (same `404`, same message) so payment ids cannot be enumerated across accounts.

## Business Rules
- BR-PAY-001: The IPN webhook remains the sole source of truth for fulfilment; this endpoint only *reads* whatever the webhook has already recorded.
- BR-PAY-004: Because the endpoint never writes, polling can never re-fulfil a payment or issue a second receipt.
- BR-PAY-010: The ledger records money only; it identifies no plan and no acquisition.
- ADR-008: No QueryDR or any other server-to-server gateway call — regardless of how long a payment stays `pending`.
- Feature Spec: `docs/feats/request-credits.md`

## Example

### Request
```bash
curl /api/payments/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer <jwt>"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Payment retrieved successfully",
  "data": {
    "paymentId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "succeeded",
    "amount": 49000,
    "currency": "VND",
    "createdAt": "2026-10-04T10:15:00Z",
    "paidAt": "2026-10-04T10:15:42Z"
  }
}
```

## Related
- Feature Spec: `docs/feats/request-credits.md`
- Sibling endpoint: `docs/apis/payments/get-payments.md` (`GET /api/payments`, money-only list)
- Sibling endpoint: `docs/apis/payments/get-vnpay-ipn.md` (`GET /api/payments/vnpay/ipn`)
- Checkout that creates the row: `docs/apis/subscriptions/post-acquire.md` (`POST /api/subscriptions/acquire`)
- Business Rules: `docs/brs/payments.md`
- ADR: `docs/adrs/008-vnpay-integration.md`
