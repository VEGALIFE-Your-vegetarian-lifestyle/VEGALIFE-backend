# API Reference: POST /api/subscriptions/acquire

## Overview
Acquire an AI plan. One endpoint handles both paths: a zero-priced active plan
(the FREE tier) is claimed immediately, while a priced active plan starts a
VNPay checkout. The response always reports `remainingUsage` and an `action`
telling the caller which path ran.

## Endpoint
```
POST /api/subscriptions/acquire
```

## Authentication
JWT Bearer token required. A missing, expired, or invalid token returns `401 Unauthorized`. The target member is always the token's subject; no member id is accepted from the client.

## Request

### Path Parameters
None.

### Query Parameters
None.

### Request Body
```json
{
  "planCode": "string — plan code from GET /api/subscriptions, e.g. \"FREE\" or \"PRO\""
}
```

`planCode` is required (`@NotBlank`) and is matched against `ai_plan.code` as stored (the plan catalogue uses uppercase codes).

## Responses

The plan is resolved first: an unknown code is `404`, an inactive plan is `400`, and a zero price branches to claim while a positive price branches to checkout.

| Plan state | Result |
|---|---|
| active, `price_amount = 0`, not yet claimed | `200` `action = "CLAIMED"` |
| active, `price_amount = 0`, already claimed | `409 "Plan already claimed"` |
| active, `price_amount > 0` | `200` `action = "CHECKOUT_CREATED"` |
| exists but not active | `400 "Plan is not available"` |
| unknown code | `404 "Plan not found"` |

### Claim Response (200)
```json
{
  "success": true,
  "message": "Plan claimed successfully",
  "data": {
    "planCode": "FREE",
    "action": "CLAIMED",
    "remainingUsage": 5
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | `Plan claimed successfully` |
| data.planCode | string | The plan that was acquired |
| data.action | string | `CLAIMED` — no money involved |
| data.remainingUsage | integer | `max(allowance − consumed, 0)` after the claim; the only usage number this API exposes |

### Checkout Response (200)
```json
{
  "success": true,
  "message": "Payment session created",
  "data": {
    "planCode": "PRO",
    "action": "CHECKOUT_CREATED",
    "remainingUsage": 12,
    "paymentId": "8f14e45f-ceea-167a-5a36-dedd4bea2543",
    "txnRef": "8f14e45fceea167a5a36dedd4bea2543",
    "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=4900000&…&vnp_SecureHash=...",
    "amount": 49000,
    "currency": "VND",
    "status": "pending"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| data.action | string | `CHECKOUT_CREATED` — a payment is required |
| data.remainingUsage | integer | The member's current allowance; unchanged by starting a checkout, because a pending acquisition grants nothing |
| data.paymentId | string (UUID) | `payment_ledger` row id, also carried as the `vnp_ReturnUrl` path segment so the result page can poll `GET /api/payments/{paymentId}` |
| data.txnRef | string | 32 lowercase hex chars derived from the ledger id; echoed back by VNPay as `vnp_TxnRef` |
| data.paymentUrl | string | Absolute, already-signed VNPay URL — redirect the browser here |
| data.amount | number | Charged amount in whole VND, frozen at creation from `ai_plan.price_amount` |
| data.currency | string | ISO-4217 code; always `VND` |
| data.status | string | Always `"pending"`; the authoritative status arrives via IPN |

The checkout-only fields (`paymentId`, `txnRef`, `paymentUrl`, `amount`, `currency`, `status`) are `@JsonInclude(NON_NULL)`, so a claim response omits them entirely.

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Body missing or blank `planCode` | "Validation failed" |
| 400 | Plan exists but is not active | "Plan is not available" |
| 400 | Priced plan's currency is not `VND` | the validation message |
| 401 | Missing, expired, or invalid JWT | "Unauthorized" |
| 404 | No `ai_plan` row with that `code` | "Plan not found" |
| 409 | Zero-priced plan already claimed by this member | "Plan already claimed" |
| 503 | Payment gateway not configured (`enabled = false` or blank `tmn-code` / `secure-hash-secret` / `return-url`) or unreachable | the gateway message |

## Business Rules
- BR-SUBS-002 — plan terms come from `ai_plan`, never constants.
- BR-SUBS-007 — acquired requests never expire; `remainingUsage = max(allowance − consumed, 0)`.
- BR-SUBS-008 — a zero-priced plan may be claimed once per member, enforced by the partial unique index `uq_ai_subscription_one_claim`; a paid plan may be bought repeatedly and each purchase stacks its quota.
- BR-SUBS-009 — a deactivated plan cannot be acquired, but existing holders keep the allowance it granted.
- BR-PAY-005 — one in-flight checkout per `(member, plan)` within `app.payments.checkout-ttl` (default 30 minutes) is reused: same `paymentId`, same `txnRef`, freshly signed URL.
- BR-PAY-009 — a non-VND price never reaches VNPay.
- BR-PAY-010 — the acquisition row records the plan; `payment_ledger` records only the money.
- A checkout writes the `payment_ledger` row and the `ai_subscription` row (`payment_id` set, `granted_at NULL`) in one transaction; only the IPN grants it (BR-PAY-001).
- `data.status` is a convenience for the UI, not confirmation of payment. After returning from VNPay the frontend must read `GET /api/payments/{paymentId}` or `GET /api/subscriptions/me`.
- No secret is echoed: the response carries only the derived `vnp_SecureHash` embedded in `paymentUrl`.

## Example

### Claim the free plan
```bash
curl -X POST "http://localhost:8080/api/subscriptions/acquire" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access-token>" \
  -d '{"planCode":"FREE"}'
```

```json
{
  "success": true,
  "message": "Plan claimed successfully",
  "data": { "planCode": "FREE", "action": "CLAIMED", "remainingUsage": 5 }
}
```

### Claim the same plan again (409)
```json
{
  "success": false,
  "message": "Plan already claimed",
  "data": null
}
```

### Buy a priced plan
```bash
curl -X POST "http://localhost:8080/api/subscriptions/acquire" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access-token>" \
  -d '{"planCode":"PRO"}'
```

```json
{
  "success": true,
  "message": "Payment session created",
  "data": {
    "planCode": "PRO",
    "action": "CHECKOUT_CREATED",
    "remainingUsage": 12,
    "paymentId": "8f14e45f-ceea-167a-5a36-dedd4bea2543",
    "txnRef": "8f14e45fceea167a5a36dedd4bea2543",
    "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=4900000&vnp_Command=pay&vnp_TxnRef=8f14e45fceea167a5a36dedd4bea2543&vnp_SecureHash=5f0d3b...",
    "amount": 49000,
    "currency": "VND",
    "status": "pending"
  }
}
```

### Second acquire within the checkout TTL (200)
Same `paymentId` and `txnRef`, new `vnp_CreateDate` / `vnp_ExpireDate` / `vnp_SecureHash` — one pending acquisition, one VNPay order.

## Related
- Feature Spec: `docs/feats/request-credits.md`
- ADR: `docs/adrs/021-request-allowance-as-acquisition-sum.md`
- Remaining allowance: `docs/apis/subscriptions/get-me.md` (`GET /api/subscriptions/me`)
- Acquisition history: `docs/apis/subscriptions/get-history.md` (`GET /api/subscriptions/me/history`)
- Fulfilment webhook: `docs/apis/payments/get-vnpay-ipn.md` (`GET|POST /api/payments/vnpay/ipn`)
- Plan catalogue: `docs/apis/subscriptions/get-subscriptions.md`, `docs/apis/admin/plan-crud.md`
- Business Rules: `docs/brs/subscriptions.md`, `docs/brs/payments.md`
