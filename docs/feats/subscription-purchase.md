# Feature Spec: Purchase AI Subscription (VNPay Checkout + IPN Webhook)

## Status

In progress

## Author / owner

zuyzz (issue #16), written by backend agent; owns the `/api/payments`
contract, the VNPay integration seam, and the `payment_ledger` write path.

## Summary

Adds the paid upgrade path from FREE to PRO. An authenticated
`POST /api/payments/checkout` records a pending `payment_ledger` row and
returns a signed VNPay payment URL the frontend redirects the member to. A
public VNPay IPN webhook (`GET|POST /api/payments/vnpay/ipn`) is the **single
source of truth for fulfilment**: once it verifies the checksum, the
transaction reference, and the amount, it marks the payment `succeeded`,
upserts `ai_subscription` onto the purchased plan, and queues a receipt email
through the existing outbound queue. Replayed webhooks are acknowledged with
the same success code but repeat no side effect.

## Problem / motivation

Issue #15 shipped the subscription **read** model: plans, quotas, and a
`payment_ledger` table that nothing writes to, so every member reads as FREE.
Issue #16 closes the loop for Sprint 3 by wiring the first payment gateway
(VNPay) so a member on the free tier can buy PRO.

The hard part is not the redirect — it is that a payment webhook is an
**unauthenticated, externally-triggered write** that VNPay retries up to ten
times over five minutes. Without an explicit idempotency contract, a retried
webhook double-upgrades the subscription and emails a second receipt; without
amount and checksum validation, anyone on the internet can POST to our
webhook and upgrade any account for free. This spec therefore treats
validation, idempotency, and source-of-truth separation as first-class
requirements rather than implementation detail.

## Goals

- Let an authenticated member on FREE start a VNPay payment for an active,
  priced plan and receive a signed payment URL in one call.
- Fulfil a successful payment exactly once: ledger `pending → succeeded`,
  subscription upserted onto the purchased plan, receipt email queued.
- Acknowledge VNPay's webhook correctly for success, failure, and pending so
  the gateway stops retrying and no state is lost.
- Make both the checkout request and the webhook replay-safe.
- Confine every VNPay-specific detail (signing, endpoints, parameter names,
  response codes) to one package so a second gateway is an addition, not a
  rewrite.

## Non-goals

- Multiple payment gateways — VNPay only this sprint (issue #16 non-goal).
- Subscription downgrade, cancellation, pause, or proration — separate issue.
- Trial periods (issue #16 non-goal).
- Refunds, chargebacks, and QueryDR-driven reconciliation in production code.
  The client speaks only the pay-URL and IPN sides of the protocol; no
  scheduled reconciliation job ships.
- A backend "return" endpoint. VNPay redirects the browser to a
  **frontend** result page; that page is display-only and never mutates
  state (BR-PAY-001).
- A payment-history list endpoint — `GET /api/subscriptions/me` keeps
  returning only the single latest successful payment (FR-005 of #15).
- Webhooks other than VNPay's IPN (no refund callback, no subscription
  lifecycle callback).
- Recurring billing. A purchase buys one plan period; renewals are not
  automated.
- Admin tooling for payments (no manual re-fulfilment UI).

## Requirements

### Functional Requirements

- [ ] FR-001: `POST /api/payments/checkout` with a valid JWT and body
      `{"planCode": "<CODE>"}` returns `200 OK` with `data` shaped
      `{paymentId, txnRef, planCode, amount, currency, status, paymentUrl}`
      where `paymentId` is the `payment_ledger` row id (the frontend's key
      for post-redirect lookups), `status` is `"pending"` and `paymentUrl`
      is an absolute VNPay URL for the configured environment.
- [ ] FR-002: The requested plan must exist and be `active` (`404
      ResourceNotFoundException` when the code matches no plan), must be
      purchasable — `price_amount > 0` and `price_currency = 'VND'`, since
      VNPay settles in VND only (`400 ValidationException` otherwise). The
      seeded FREE plan (0 VND) is therefore never purchasable.
- [ ] FR-003: Checkout writes exactly one `payment_ledger` row per distinct
      checkout: `user_id`, `plan_id`, `amount` = plan price, `currency` =
      plan currency, `status = 'pending'`, `provider = 'vnpay'`, and
      `txn_ref` (see FR-005). `paid_at`, `provider_reference`, and
      `response_code` stay `NULL` until the webhook resolves the payment.
- [ ] FR-004: `paymentUrl` is built from `vnp_Version=2.1.0`,
      `vnp_Command=pay`, `vnp_TmnCode`, `vnp_Amount` (= `amount * 100`),
      `vnp_CurrCode=VND`, `vnp_TxnRef`, `vnp_OrderInfo`, `vnp_OrderType=
      other`, `vnp_Locale`, `vnp_ReturnUrl` (= configured `return-url` base
      plus the `/{paymentId}` path segment), `vnp_CreateDate`, `vnp_IpAddr`,
      `vnp_ExpireDate`, and `vnp_SecureHash` (HMAC-SHA512, lowercase hex),
      all URL-encoded and appended to the configured payment URL. No other
      field may be added or renamed.
- [ ] FR-005: `txnRef` is derived from the ledger primary key with dashes
      removed (32 lowercase hex chars), making it globally unique and
      directly traceable to the ledger row that created it.
- [ ] FR-006: Checkout is idempotent for an in-flight payment: if the caller
      already has a `pending` ledger row for the same plan created within
      `app.payments.checkout-ttl` (default 30 minutes), the existing row is
      reused — same `txnRef`, same ledger row, a freshly signed URL (the
      signature embeds a new `vnp_CreateDate`). No second ledger row is
      created and no second VNPay order exists. Outside the TTL, or once the
      row has left `pending`, a new row is created.
- [ ] FR-007: `GET /api/payments/vnpay/ipn` and
      `POST /api/payments/vnpay/ipn` are public (`permitAll`, exact path,
      no wildcard) and accept VNPay's IPN query/form parameters. Both
      methods are accepted because VNPay's IPN is documented as a GET with
      query parameters while some merchant profiles are configured for POST.
- [ ] FR-008: The webhook's first act is checksum verification
      (HMAC-SHA512 over the sorted, URL-encoded parameter string excluding
      `vnp_SecureHash` and `vnp_SecureHashType`). A mismatch returns
      `{"RspCode":"97","Message":"Invalid Checksum"}` and no state change.
- [ ] FR-009: A `vnp_TxnRef` with no matching `payment_ledger` row returns
      `{"RspCode":"01","Message":"Order not Found"}` and no state change.
- [ ] FR-010: `vnp_Amount` (divided by 100) must equal the ledger `amount`
      exactly; a mismatch returns `{"RspCode":"04","Message":"Invalid
      Amount"}` and no state change. This is what makes a forged or
      tampered webhook unable to buy a plan for 1 VND.
- [ ] FR-011: On `vnp_ResponseCode = 00` **and** `vnp_TransactionStatus =
      00`, the webhook transitions `pending → succeeded` inside one
      transaction: `paid_at = now`, `provider_reference = vnp_TransactionNo`,
      `provider = 'vnpay'`, `bank_code` and `response_code` recorded. It
      then upserts `ai_subscription` for the ledger's user onto the ledger's
      plan (`status = 'active'`, `renewal_date = paid_at + 1 calendar month
      in UTC`) and enqueues a receipt email — then acks
      `{"RspCode":"00","Message":"Confirm Success"}`.
      **Amended by `docs/feats/subscription-lifecycle.md` (FR-008):** once
      `ai_subscription` allows multiple rows per member, the upsert becomes
      a branch-dependent insert through the shared purchase gate
      (BR-SUBS-004) — no row in effect → new `active` row (`started_at =
      paid_at`, `renewal_date = paid_at + 1 calendar month in UTC`); same
      plan already active with no successor → new `scheduled` successor row
      (`extended_from_id` set, `renewal_date` = current `renewal_date` + 1
      month); any other state → no subscription write and a WARN log, with
      the payment remaining `succeeded`. The ledger transition, receipt
      email, and ack above are unchanged.
- [ ] FR-012: On any other terminal outcome (`vnp_ResponseCode != 00` or
      `vnp_TransactionStatus != 00`), the webhook records
      `status = 'failed'` with `response_code` set, changes no
      subscription, sends no email, and still acks
      `{"RspCode":"00","Message":"Confirm Success"}` — the ack confirms
      *receipt of the notification*, not *success of the payment*.
- [ ] FR-013: On `vnp_TransactionStatus = 01` (still processing), the
      webhook leaves the ledger `pending`, changes no subscription, sends
      no email, and acks `00` so VNPay does not treat the notification as
      unhandled.
- [ ] FR-014: Replaying any webhook for an already-terminal ledger is a
      no-op on state: it re-acks `00` (VNPay retries ten times) but does
      **not** re-run the subscription upsert, does not enqueue a second
      receipt, and does not overwrite `paid_at`. A replay whose payload
      disagrees with the recorded outcome is logged at WARN and acked `00`
      with no state change — a `succeeded` payment is never downgraded by a
      later callback (refund is out of scope).
- [ ] FR-015: `vnp_ResponseCode = 07` (transaction flagged by risk rules)
      and `vnp_TransactionStatus = 04` (reversed) are **never** fulfilment:
      the ledger is marked `failed`, an ERROR log records the `txnRef` for
      manual review, and no subscription or email side effect occurs — even
      though VNPay may report that money moved.
- [ ] FR-016: Concurrent deliveries of the same webhook are serialised by a
      pessimistic lock (`SELECT ... FOR UPDATE`) on the ledger row, so only
      one delivery can observe `pending` and perform the side effects.
- [ ] FR-017: The receipt email is enqueued through the existing outbound
      message queue (ADR-005) in the same transaction as the state change —
      the webhook never talks to SMTP directly, and a mail outage cannot
      fail a payment that VNPay has already recorded as paid.
- [ ] FR-018: Checkout and webhook responses never return the secure hash
      secret, the merchant config, `provider_reference` of another user, or
      any internal id other than the caller's own `txnRef`.
- [ ] FR-019: With `app.payments.vnpay.enabled = false` (or a blank
      `tmn-code` / `secure-hash-secret` / `return-url`), checkout fails
      fast with a clear error instead of silently building an unsigned or
      unusable URL; the webhook still runs its validation and rejects with
      `97`.
- [ ] FR-020: The webhook never falls through to `GlobalExceptionHandler`.
      Every outcome — including an unexpected internal failure — returns
      HTTP `200` with a valid `{"RspCode","Message"}` ack (`99` for an
      unexpected failure, so VNPay retries) rather than the `ApiResponse`
      envelope VNPay cannot parse.

### Non-Functional Requirements

- [ ] NFR-SEC-001: `tmn-code` and `secure-hash-secret` come only from
      configuration/environment (`VNPAY_TMN_CODE`, `VNPAY_SECURE_HASH_SECRET`).
      They are never logged, never put in an exception message, and never
      appear in a response. Only the derived `vnp_SecureHash` leaves the
      server.
- [ ] NFR-SEC-002: No subscription write can happen without all three
      checks passing in the same request: valid checksum (FR-008), known
      `txnRef` (FR-009), and amount equality (FR-010).
- [ ] NFR-SEC-003: The IPN endpoint is `permitAll` on an exact path only —
      no `/api/payments/**` wildcard — and, like the rest of the API, runs
      stateless with CSRF disabled. Checkout remains authenticated.
- [ ] NFR-MAINT-001: Every VNPay-specific constant (endpoint URLs,
      parameter names, response codes, hash rules) lives under
      `infrastructure/payment/vnpay/`. Services deal in a small
      gateway-neutral vocabulary; nothing outside that package spells
      `vnp_`.
- [ ] NFR-MAINT-002: The gateway seam is an interface (`PaymentGateway`) so
      a second provider is a new implementation plus configuration, with no
      change to the checkout or webhook services. VNPay is the only
      implementation this sprint.
- [ ] NFR-MAINT-003: No plan price, plan code, or tier constant appears in
      Java (BR-SUBS-002); the amount charged is always read from
      `ai_plan.price_amount`.
- [ ] NFR-SCALE-001: Checkout is O(1) queries (plan lookup, in-flight
      lookup, insert) and the webhook is O(1) (lock+read, upsert, queue
      insert). No table scan, no in-memory join.
- [ ] NFR-RELI-001: The webhook returns its JSON ack as soon as the
      transaction commits; VNPay's five-minute retry budget is never spent
      on background work.
- [ ] NFR-TEST-001: Sandbox tests that make real HTTPS calls to VNPay
      staging are annotated `@EnabledIfEnvironmentVariable` on
      `VNPAY_TMN_CODE` / `VNPAY_SECURE_HASH_SECRET`, so a clone without
      credentials **skips** them and CI stays green. Tests that exercise
      checkout and the webhook end-to-end run unconditionally against
      self-signed payloads in the `integration` profile. A skipped real-call
      test is reported as skipped, never as passed.
- [ ] NFR-TEST-002: All tests seed plan rows rather than restating prices
      (BR-SUBS-002), and no test asserts against a hardcoded PRO price.

## Design overview

Package-by-layer, mirroring the subscription feature:

- **Migration `V26__add_payment_checkout_columns.sql`** adds to
  `payment_ledger`: `txn_ref VARCHAR(64)`, `response_code VARCHAR(10)`,
  `bank_code VARCHAR(32)`; plus a unique index on `txn_ref` and a partial
  index on `(user_id, plan_id, created_at)` for the in-flight lookup
  (FR-006). `provider` and `provider_reference` keep their #15 meanings:
  `provider = 'VNPAY'` and `provider_reference` holds VNPay's
  `vnp_TransactionNo` once known.
- **`infrastructure/payment/vnpay/`** — `VnpayProperties`
  (`@ConfigurationProperties("app.payments.vnpay")`), `VnpaySigner`
  (build/verify HMAC-SHA512, no Spring types), `VnpayClient`
  (`createPaymentUrl`, `verifyCallback`), and
  `VnpayPaymentGateway implements PaymentGateway`. This is the only package
  that knows VNPay's wire format.
- **`service/payment/`** — `PaymentCheckoutService` (FR-001…FR-006) and
  `PaymentWebhookService` (FR-007…FR-017). Fulfilment delegates the
  subscription write to `SubscriptionService.activatePlan(userId, planId,
  paidAt)`, so subscription state transitions stay in one place.
- **`controller/payment/PaymentController`** — `POST /api/payments/checkout`
  taking `@AuthenticationPrincipal UUID userId`, and the two IPN mappings
  on `/api/payments/vnpay/ipn`. The IPN response is the raw VNPay ack
  `{"RspCode","Message"}` and deliberately **not** wrapped in the standard
  `ApiResponse` envelope, because VNPay parses it literally — the only
  documented envelope exception in the API.
- **`SecurityConfig`** gains an exact-path
  `.requestMatchers("/api/payments/vnpay/ipn").permitAll()` before
  `.anyRequest().authenticated()`; checkout falls through to authentication.
- **Config** under `app.payments`: `vnpay.{enabled, tmn-code,
  secure-hash-secret, payment-url, locale}`, `return-url`
  (frontend result-page base; the signed `vnp_ReturnUrl` appends
  `/{paymentId}`), `checkout-ttl` (default `30m`). Dev/test
  profile values point at sandbox hosts with placeholder credentials; prod
  values are environment-only.
- **Receipt email** adds `OutboundEmailPayload.Type.RECEIPT` with a nested
  `Receipt(planName, amount, currency, paidAt, reference)` plus a
  back-compatible 4-argument constructor, a `sendPaymentReceipt` method on
  `EmailService` implemented by both `OutboxEmailService` (enqueue) and
  `SmtpEmailServiceImpl` (render `email/payment-receipt`), and a
  `deliver` branch in `EmailChannelAdapter`.
- **DTOs** `dto/request/payment/CheckoutRequest` (`planCode`, `@NotBlank`)
  and `dto/response/payment/CheckoutResponse`.
- **ADR-008** records the build-vs-buy decision (hand-rolled thin client,
  no Maven SDK) and the source-of-truth decision.

State machine for a ledger row:

```
pending ──success(IPN)──▶ succeeded   (terminal; upserts subscription, queues receipt)
   │
   ├──failure/reversal/fraud(IPN)──▶ failed   (terminal; no side effects)
   │
   └──no IPN / abandoned──▶ pending   (stays; FR-006 TTL simply allows a new row)

refunded is only reachable by a future refund feature; nothing here writes it.
```

## Success metrics

- `POST /api/payments/checkout` returns `200` with a payment URL whose
  signature VNPay's sandbox accepts, and `401` without a token — verified in
  this change.
- A self-signed, checksum-valid success webhook flips one ledger row to
  `succeeded`, upserts one `ai_subscription` row, and enqueues exactly one
  `outbound_message`; delivering the identical webhook again changes
  nothing and still acks `00` — verified in this change.
- Bad checksum (`97`), unknown `txnRef` (`01`), and amount mismatch (`04`)
  each leave the database byte-identical — verified in this change.
- The sandbox tests that call VNPay staging run green with
  `VNPAY_TMN_CODE`/`VNPAY_SECURE_HASH_SECRET` exported, and are reported as
  skipped without them (NFR-TEST-001).
- `grep -r "vnp_" src/main/java --include=*.java | grep -v infrastructure/payment`
  returns nothing (NFR-MAINT-001).

## Acceptance criteria

**As a** member on the free tier, **I want to** purchase the PRO plan through
VNPay, **so that** my AI quota limits increase without waiting for an
administrator.

Mapped from issue #16:

- Given an authenticated user on the free tier, when they call
  `POST /api/payments/checkout` with `planCode = "PRO"`, then a
  `payment_ledger` row is created `pending`, a signed VNPay payment URL
  is returned — VNPay payment initiated — and `paymentId` echoes the
  ledger row id (the path segment the return URL will carry).
- Given a payment completed on VNPay's side, when VNPay delivers a valid
  success IPN, then the ledger becomes `succeeded` and the member's
  `ai_subscription` is upserted onto PRO immediately in the same
  transaction.
- When VNPay delivers a failure IPN, the ledger becomes `failed` and nothing
  else changes; when it delivers a pending IPN, the ledger stays `pending`
  and nothing else changes; both ack `00` so the gateway stops retrying.
- Given a fulfilled payment, when the success IPN is processed, then a
  receipt email is enqueued on the outbound queue exactly once.
- Given the same checkout or the same webhook delivered twice, the second
  delivery creates no second ledger row, performs no second upgrade, and
  enqueues no second receipt, while still acknowledging `00`.

Out of scope for this feature, by issue #16 non-goals: multiple gateways,
downgrade/cancel, and trials.

## Risks / open questions

- **PRO's 49,000 VND price is still a placeholder** (from #15). The charge
  reads whatever `ai_plan.price_amount` holds at checkout time, so product
  can change it with a data migration — but the sandbox test should not
  assert a specific amount for that reason (NFR-TEST-002).
- **Sandbox credentials** (`VNPAY_TMN_CODE`, `VNPAY_SECURE_HASH_SECRET`) are
  not committed anywhere; without them the real staging tests skip
  (NFR-TEST-001). Sandbox terminals are registered through VNPay's dev
  portal, and one merchant profile can have several (Napas 9704xx banks,
  e-commerce, QR).
- **IPN URL registration is out-of-band.** VNPay sends notifications to the
  URL configured on the merchant profile, not to a URL we pass in the
  request. Staging must have `https://<host>/api/payments/vnpay/ipn`
  registered; until it does, only the self-signed webhook tests prove the
  handler, and the sandbox tests prove only the outbound direction.
- **`return-url` is required configuration**, not a default. Checkout fails
  with a clear error when it is blank; `application.yml` falls back to
  `http://localhost:3000/payment/result` for local runs. The signed
  `vnp_ReturnUrl` is that base plus a `/{paymentId}` path segment, so the
  result page knows which payment the redirect belongs to without parsing
  query parameters. The page must still treat the redirect's query
  parameters as **display-only hints** — it must call
  `GET /api/subscriptions/me` for the authoritative tier, because the
  return URL can be edited by the user (BR-PAY-001).
- **No reconciliation job.** If an IPN is lost entirely (VNPay gives up
  after ten retries), a payment could be taken but never fulfilled. The
  client does not speak QueryDR at all; a reconciliation
  sweeper is a sensible follow-up, not part of #16.
- **`renewal_date` will drift from the quota window** for mid-month
  subscribers, exactly as BR-SUBS-001 already documents. This feature sets
  `renewal_date = paid_at + 1 month` and deliberately does **not** change
  the quota window.
- Concurrent checkouts for the same user+plan are only guarded at the
  application level (FR-006 reads then inserts); a true race can still
  create two pending rows. The consequence is two VNPay orders for the same
  user, which is recoverable (only the paid one fulfils), so no unique
  constraint that would block new checkouts behind a stuck pending row is
  added.
