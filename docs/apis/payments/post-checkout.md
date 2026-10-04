# API Reference: POST /api/payments/checkout

## Overview
Start a VNPay payment for an active, priced AI plan: record a `pending` payment ledger row and return a signed VNPay payment URL for the frontend to redirect to.

## Endpoint
```
POST /api/payments/checkout
```

## Authentication
JWT Bearer token required. A missing, expired, or invalid token returns `401 Unauthorized`.

## Request

### Path Parameters
None.

### Query Parameters
None.

### Request Body
```json
{
  "planCode": "string — plan code from GET /api/subscriptions, e.g. \"PRO\""
}
```

`planCode` is required (`@NotBlank`); it is matched case-sensitively against `ai_plan.code`.

## Responses

### Success Response (200)
```json
{
  "success": true,
  "message": "Payment session created",
  "data": {
    "paymentId": "8f14e45f-ceea-167a-5a36-dedd4bea2543",
    "txnRef": "8f14e45fceea167a5a36dedd4bea2543",
    "planCode": "PRO",
    "amount": 49000,
    "currency": "VND",
    "status": "pending",
    "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=4900000&vnp_Command=pay&vnp_CreateDate=20261004101500&vnp_CurrCode=VND&vnp_ExpireDate=20261004104500&vnp_IpAddr=127.0.0.1&vnp_Locale=vn&vnp_OrderInfo=Upgrade+to+PRO&vnp_OrderType=other&vnp_ReturnUrl=http%3A%2F%2Flocalhost%3A5173%2Fpayment%2Fresult%2F8f14e45f-ceea-167a-5a36-dedd4bea2543&vnp_TmnCode=DEMO0000&vnp_TxnRef=8f14e45fceea167a5a36dedd4bea2543&vnp_Version=2.1.0&vnp_SecureHash=..."
  }
}
```

| Field | Type | Description |
|-------|------|--------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data.paymentId | string | `payment_ledger` row id (UUID) — carried as the `vnp_ReturnUrl` path segment so the frontend result page can identify the payment after the redirect |
| data.txnRef | string | Payment reference — 32 lowercase hex chars, derived from the ledger row id with dashes removed; echoed back by VNPay as `vnp_TxnRef` |
| data.planCode | string | The plan being purchased |
| data.amount | number | Charged amount in whole VND, frozen at creation from `ai_plan.price_amount` |
| data.currency | string | ISO-4217 currency code; always `VND` |
| data.status | string | Always `"pending"` at this point; the authoritative status arrives via IPN |
| data.paymentUrl | string | Absolute VNPay payment URL, already signed — redirect the browser here |

`data.status` is a convenience for the UI. The frontend must not treat it as confirmation of payment; after returning from VNPay it should read `GET /api/subscriptions/me`. The redirect lands on the configured `return-url` base with `/{paymentId}` as the final path segment — that id (not the query string) is how the result page keys any status lookup.

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Body missing/blank `planCode` | "Validation failed" |
| 400 | Plan exists but is not purchasable: inactive, `price_amount = 0` (e.g. `FREE`), or currency other than `VND` | the validation message |
| 401 | Missing, expired, or invalid JWT | "Unauthorized" |
| 404 | No `ai_plan` row with that `code` | "Resource not found" |
| 500 | Payment gateway not configured (`enabled = false` or blank `tmn-code` / `secure-hash-secret` / `return-url`) | "Internal server error" |

## Business Rules
- Checkout never mutates `ai_subscription`; a row is only written by the IPN webhook on a verified success (BR-PAY-001).
- Amount is frozen from `ai_plan.price_amount` at creation and compared against `vnp_Amount` when the webhook arrives (BR-PAY-003, FR-010).
- One in-flight checkout per user+plan: a `pending` row younger than `app.payments.checkout-ttl` (default 30 minutes) is reused, so repeated calls return the same `txnRef` and a freshly signed URL instead of creating a second VNPay order (BR-PAY-005).
- FREE and any zero-priced or non-VND plan are not purchasable (BR-PAY-009).
- No secret is ever echoed: the response carries only the derived `vnp_SecureHash` embedded in `paymentUrl`, never `tmn-code` configuration or `secure-hash-secret`.
- The signed `vnp_ReturnUrl` is `app.payments.return-url` plus `/{paymentId}` (the ledger row id), so the frontend learns which payment the redirect belongs to from the path alone.
- Feature Spec: `docs/feats/subscription-purchase.md`

## Example

### Request
```bash
curl -X POST "http://localhost:8080/api/payments/checkout" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access-token>" \
  -d '{"planCode":"PRO"}'
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Payment session created",
  "data": {
    "paymentId": "8f14e45f-ceea-167a-5a36-dedd4bea2543",
    "txnRef": "8f14e45fceea167a5a36dedd4bea2543",
    "planCode": "PRO",
    "amount": 49000,
    "currency": "VND",
    "status": "pending",
    "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=4900000&vnp_Command=pay&vnp_CreateDate=20261004101500&vnp_CurrCode=VND&vnp_ExpireDate=20261004104500&vnp_IpAddr=127.0.0.1&vnp_Locale=vn&vnp_OrderInfo=Upgrade+to+PRO&vnp_OrderType=other&vnp_ReturnUrl=http%3A%2F%2Flocalhost%3A5173%2Fpayment%2Fresult%2F8f14e45f-ceea-167a-5a36-dedd4bea2543&vnp_TmnCode=DEMO0000&vnp_TxnRef=8f14e45fceea167a5a36dedd4bea2543&vnp_Version=2.1.0&vnp_SecureHash=5f0d3b..."
  }
}
```

### Second call within the checkout TTL (200)
Same `txnRef` and `amount`, new `vnp_CreateDate` / `vnp_ExpireDate` / `vnp_SecureHash` — one pending ledger row, one VNPay order.

## Related
- Feature Spec: `docs/feats/subscription-purchase.md`
- Sibling endpoint: `docs/apis/payments/get-vnpay-ipn.md` (`GET|POST /api/payments/vnpay/ipn`, the fulfilment webhook)
- Business Rules: `docs/brs/payments.md`
- ADR: `docs/adrs/008-vnpay-integration.md`
