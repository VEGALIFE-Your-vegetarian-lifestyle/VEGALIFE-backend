# API Reference: GET /api/payments/vnpay/ipn

## Overview
VNPay's Instant Payment Notification webhook: accept a signed notification for a payment, record its outcome on `payment_ledger`, and on a verified success fulfil the subscription (plan upsert + receipt email queued). This endpoint is the single source of truth for whether a payment succeeded.

The same path is also served by `POST`; VNPay's IPN is documented as a GET with query parameters while some merchant profiles are configured for POST, so both mappings exist and behave identically. Write `POST /api/payments/vnpay/ipn` in the merchant portal if the profile requires it.

## Endpoint
```
GET /api/payments/vnpay/ipn
POST /api/payments/vnpay/ipn
```

## Authentication
**None.** The path is `permitAll` on an exact match — no wildcard. Caller identity is established by the HMAC-SHA512 checksum over the parameters, which only VNPay (and anyone holding the shared secret) can produce.

## Request

### Path Parameters
None.

### Query Parameters (GET) / Form Fields (POST)
The full VNPay IPN payload. `vnp_SecureHash` is mandatory; every other field is validated against the ledger row.

| Name | Type | Required | Description |
|------|------|----------|-------------|
| vnp_TmnCode | string | yes | Merchant terminal code; must match configuration |
| vnp_Amount | number | yes | Amount in hundredths of VND (49000 VND → `4900000`) |
| vnp_BankCode | string | yes | Acquiring bank / card channel code |
| vnp_OrderInfo | string | yes | Order description passed at checkout |
| vnp_TransactionNo | string | yes | VNPay's transaction id; stored as `provider_reference` |
| vnp_ResponseCode | string | yes | Result code, `00` = approved |
| vnp_TransactionStatus | string | yes | `00` paid, `01` processing, `04` reversed |
| vnp_TxnRef | string | yes | Our reference from checkout; matched against `payment_ledger.txn_ref` |
| vnp_CreateDate | string | yes | GMT+7 `yyyyMMddHHmmss` order creation time |
| vnp_PayDate | string | yes | GMT+7 `yyyyMMddHHmmss` payment time |
| vnp_OrderType | string | no | Echo of the order type |
| vnp_Locale | string | no | `vn` or `en` |
| vnp_SecureHashType | string | yes | `HMACSHA512` |
| vnp_SecureHash | string | yes | Lowercase-hex HMAC-SHA512 over the sorted, URL-encoded parameters excluding `vnp_SecureHash` |

### Request Body
No JSON body. GET parameters or POST form fields only.

## Responses

### Response shape
**Not** wrapped in the standard `ApiResponse` envelope — VNPay parses the body literally as `{"RspCode": "...", "Message": "..."}`. This is the one documented exception to the envelope convention. HTTP status is always `200` for a handled notification; a handled notification means *we answered VNPay*, not *the payment succeeded*.

### Acknowledgements

| RspCode | Message | When | State change |
|---------|---------|------|--------------|
| 00 | Confirm Success | Checksum valid, `vnp_TxnRef` found, amount matches — for any outcome (paid, processing, failed, reversed) and for replays of an already-terminal row | Recorded (or none on replay) |
| 01 | Order not Found | Checksum valid but no `payment_ledger` row with that `txn_ref` | None |
| 04 | Invalid Amount | Checksum valid but `vnp_Amount / 100` ≠ ledger `amount` | None |
| 97 | Invalid Checksum | HMAC verification failed, or required parameter missing | None |
| 99 | Unknown error | Unexpected internal failure; VNPay retries | None |

```json
{"RspCode":"00","Message":"Confirm Success"}
```

### Outcome recorded when RspCode = 00

| vnp_ResponseCode / vnp_TransactionStatus | Ledger status | Subscription | Receipt email |
|------------------------------------------|---------------|--------------|---------------|
| `00` / `00` (paid) | `pending → succeeded`, `paid_at` set, `provider_reference` = `vnp_TransactionNo`, `bank_code`, `response_code` recorded | upserted onto the purchased plan, `status = 'active'`, `renewal_date = paid_at + 1 month UTC` | enqueued on the outbound queue, exactly once |
| `01` / `01` (processing) | stays `pending` | unchanged | none |
| `07` (risk-flagged) or `04`/`02` (reversed) | `failed`, `response_code` recorded, ERROR log for manual review | unchanged | none |
| any other terminal combination | `failed`, `response_code` recorded | unchanged | none |
| replay of an already-terminal row | unchanged | unchanged | none |

### Error Responses
The gateway-neutral error envelope does not apply here; every handled case returns one of the acknowledgements above with HTTP `200`. The global `400`/`404`/`401`/`500` envelope is unreachable from this path by design (FR-020).

## Business Rules
- BR-PAY-001: IPN is the sole source of truth for fulfilment; the browser redirect after payment is display-only and mutates nothing.
- BR-PAY-002: A payment counts as paid only when `vnp_ResponseCode = 00` **and** `vnp_TransactionStatus = 00`.
- BR-PAY-003: The notified amount must equal the ledger amount exactly or nothing is recorded.
- BR-PAY-004: `pending → succeeded` happens at most once per ledger row; replays acknowledge `00` with no re-upgrade and no second receipt.
- BR-PAY-006: Risk-flagged (`07`) and reversed (`04`) notifications never fulfil, even though money may have moved; they are failed and logged for review.
- BR-PAY-007: The receipt email is queued in the same transaction as the state change; SMTP is never called inline.
- Feature Spec: `docs/feats/subscription-purchase.md`

## Example

### Request (VNPay → us)
```bash
curl -G "https://api.example.com/api/payments/vnpay/ipn" \
  --data-urlencode "vnp_TmnCode=DEMO0000" \
  --data-urlencode "vnp_Amount=4900000" \
  --data-urlencode "vnp_BankCode=NCB" \
  --data-urlencode "vnp_OrderInfo=Upgrade to PRO" \
  --data-urlencode "vnp_TransactionNo=15123456" \
  --data-urlencode "vnp_ResponseCode=00" \
  --data-urlencode "vnp_TransactionStatus=00" \
  --data-urlencode "vnp_TxnRef=8f14e45fceea167a5a36dedd4bea2543" \
  --data-urlencode "vnp_CreateDate=20261004101500" \
  --data-urlencode "vnp_PayDate=20261004101542" \
  --data-urlencode "vnp_SecureHashType=HMACSHA512" \
  --data-urlencode "vnp_SecureHash=5f0d3b..."
```

### Success Response (200)
```json
{
  "RspCode": "00",
  "Message": "Confirm Success"
}
```

### Tampered amount (200)
```json
{
  "RspCode": "04",
  "Message": "Invalid Amount"
}
```

## Related
- Feature Spec: `docs/feats/subscription-purchase.md`
- Sibling endpoint: `docs/apis/payments/post-checkout.md` (`POST /api/payments/checkout`)
- Business Rules: `docs/brs/payments.md`
- ADR: `docs/adrs/008-vnpay-integration.md`
- Security config: exact-path `permitAll` for `/api/payments/vnpay/ipn` in `SecurityConfig`
