# Business Rules: Payments (VNPay Checkout & IPN)

Constraints governing payment checkout, webhook fulfilment, and the receipt email for AI plan purchases. ID format `BR-PAY-<NNN>`. Companion to the plan/quota rules in `subscriptions.md` (`BR-SUBS-007` … `BR-SUBS-011`).

Enforcement rows describe the contract implemented by issue #16; the implementation state of that work is tracked in `docs/feats/subscription-purchase.md`.

---

# Business Rule: The Payment Webhook Is the Sole Source of Truth for Fulfilment

## Rule ID
`BR-PAY-001`

## Status
Active

## Statement
A plan acquisition is granted only while handling a checksum-valid VNPay IPN notification on `GET|POST /api/payments/vnpay/ipn`. The browser redirect after payment — VNPay's `vnp_ReturnUrl`, landing on a **frontend** result page — is display-only: the backend exposes no return endpoint, and no code path that runs on redirect, query string, or client-supplied flag may write `payment_ledger` or stamp `ai_subscription.granted_at`.

## Rationale
`vnp_ReturnUrl` is requested by us but navigated by the payer, so every parameter on it is attacker-controlled: anyone can craft a URL that "returns successfully" without paying. A webhook is not unforgeable either, but it is verifiable — the HMAC-SHA512 signature can only be produced with the shared secret — so it is the one channel that can carry an authoritative outcome. Splitting the roles also means the outcome survives the user closing the tab before the redirect lands.

## Scope & Exceptions
Applies to every payment-initiating and payment-resolving path in the system, including any future gateway. The frontend result page may render whatever it likes from the redirect's query parameters, but must read the member's real allowance (`GET /api/subscriptions/me`) or acquisition history before claiming success. No exception for the "user obviously paid" case: if VNPay never delivers a notification, the payment stays `pending` (see the reconciliation gap in `docs/feats/subscription-purchase.md`).

## Enforcement
- `PaymentController` maps only `GET|POST /api/payments/vnpay/ipn`; checkout lives at `POST /api/subscriptions/acquire`, and there is no `/vnpay/return` route
- `PaymentWebhookService` is the only path that stamps `ai_subscription.granted_at` for a purchased (non-claimed) acquisition
- `SecurityConfig` permits the IPN path on an exact match, not a wildcard

## Last Reviewed
2026-11-04, by Vegalife backend team

---

# Business Rule: A Payment Counts as Paid Only on a Double Zero

## Rule ID
`BR-PAY-002`

## Status
Active

## Statement
A notification fulfils a payment only when `vnp_ResponseCode = '00'` **and** `vnp_TransactionStatus = '00'` are both present in the verified payload. Either one alone is not a successful payment. The HTTP `200` + `RspCode 00` acknowledgement returned to VNPay means *"notification received and recorded"*, never *"payment succeeded"*.

## Rationale
VNPay uses two independent codes on purpose: `vnp_ResponseCode` is the acquirer's authorisation result and `vnp_TransactionStatus` is the settlement-side status of the order, and they can disagree — a transaction can be authorised then left processing, or reported with a non-zero response code while money moves. Conflating them, or mistaking the acknowledgement for success, would grant allowance on unauthorised money and would be invisible until reconciliation.

## Scope & Exceptions
Applies to every IPN handled for any plan and any amount. The acknowledgement to VNPay is `00` for paid, processing, failed, and replayed notifications alike, precisely because it acks delivery; `01`, `04`, `97`, and `99` are reserved for "we could not attribute this notification at all" (see `docs/apis/payments/get-vnpay-ipn.md`).

## Enforcement
- `PaymentWebhookService` evaluates the conjunction once, after checksum, reference, and amount validation
- Every other terminal combination writes `status = 'failed'` with `response_code` retained for diagnosis

## Last Reviewed
2026-10-04, by Vegalife backend team

---

# Business Rule: The Notified Amount Must Equal the Recorded Amount

## Rule ID
`BR-PAY-003`

## Status
Active

## Statement
Before any outcome is recorded, `vnp_Amount / 100` must equal `payment_ledger.amount` for the matched row, exactly. On mismatch nothing is written and the webhook answers `RspCode 04`. The amount charged is frozen on the ledger row at checkout from `ai_plan.price_amount` and is never re-read from the plan during fulfilment.

## Rationale
The checksum only proves VNPay (or someone with the secret) sent *these* parameters — it says nothing about whether the parameters describe the order we think they do. A notification forged against a cheap order, or delivered for an order created before a price change, would otherwise grant a member the wrong plan for the wrong money. Freezing the amount at creation also means a price change between checkout and payment cannot reprice an in-flight order.

## Scope & Exceptions
Applies to every IPN. Amounts are compared in whole VND after dividing the gateway's hundredths value by 100, so 49 000 VND is `4900000` on the wire and `49000` on the ledger. No tolerance band, no rounding allowance.

## Enforcement
- `PaymentWebhookService` compares before the state transition, alongside the reference lookup
- `payment_ledger.amount` is written once, at checkout, and has no update path in this feature

## Last Reviewed
2026-10-04, by Vegalife backend team

---

# Business Rule: A Payment Succeeds at Most Once

## Rule ID
`BR-PAY-004`

## Status
Active

## Statement
For a given `payment_ledger` row, `pending → succeeded` is executed at most once. VNPay retries an IPN up to ten times over five minutes, and every replay of a terminal row is acknowledged with `RspCode 00` while performing **no** state change: no second `paid_at`, no re-stamping of `granted_at`, no second receipt email. A replay whose payload disagrees with the recorded outcome is logged at `WARN` and still acked `00` — a `succeeded` row is never downgraded by a later callback.

## Rationale
Webhook delivery is at-least-once by construction, so idempotency is not an optimisation, it is a correctness requirement: without it a single retry would stamp `granted_at` on the acquisition twice, email the member a duplicate receipt, and rewrite `paid_at`. `uq_ai_subscription_payment` makes the one-payment-one-acquisition link structural rather than a matter of luck. Acking `00` on replay is what stops VNPay from burning its retry budget on a notification we have already fully handled; acking a *conflict* instead would trigger ten more retries for no gain.

## Scope & Exceptions
Applies to all IPN replays and to the concurrent case: two deliveries of the same notification are serialised by a pessimistic lock on the ledger row, so only one can observe `pending`. The companion rule for the request side is `BR-PAY-005`. Refunds are out of scope for issue #16, so `refunded` is unreachable here; a future refund feature must extend this rule rather than reopen `succeeded`.

## Enforcement
- `PaymentWebhookService` locks the row (`SELECT ... FOR UPDATE`) and re-checks status inside the lock
- Only the `pending → succeeded` branch enqueues the receipt, so the outbox sees it once
- Unique index on `payment_ledger.txn_ref` (migration `V26`) makes a duplicate reference impossible
- `uq_ai_subscription_payment` (migration `V48`) allows at most one acquisition per payment

## Last Reviewed
2026-11-04, by Vegalife backend team

---

# Business Rule: One In-Flight Checkout per Member and Plan

## Rule ID
`BR-PAY-005`

## Status
Active

## Statement
A member has at most one *open* checkout per plan: when `POST /api/subscriptions/acquire` reaches the checkout branch and an acquisition for the same `(user, plan)` with `granted_at IS NULL AND payment_id IS NOT NULL` was created within `app.payments.checkout-ttl` (default 30 minutes), that acquisition and its `payment_ledger` row are reused — same `paymentId`, same `txnRef`, same amount, a freshly signed payment URL — instead of a second VNPay order. Reuse requires the ledger row to still be `pending`, because a failed payment also leaves `granted_at` NULL. Outside the TTL, or once the ledger has left `pending`, a new ledger row and acquisition are created.

## Rationale
The acquire endpoint is not naturally idempotent: a double-click, a retried POST, or a re-opened pricing page would otherwise stack several live VNPay orders for one purchase, any one of which could later be paid — leaving multiple acquisitions for one purchase and no way to tell which order the member thought they were paying. Reusing the open acquisition makes the request idempotent for its whole in-flight lifetime, which is what issue #16's "idempotent payment requests" asks for. The TTL matters because a stale pending row must never block someone from starting a fresh attempt after an abandoned payment page.

## Scope & Exceptions
Applies to authenticated checkout only, scoped to the caller's own user id — one member's open checkout never affects another's, and never another plan's. Claims (`payment_id IS NULL`) are not checkouts and are governed by BR-SUBS-008. The reuse window is configuration, not a product constant: raising it keeps orders alive longer, lowering it lets members start over sooner. The lookup now lives on the acquisition row because `payment_ledger` no longer carries `plan_id` (BR-PAY-010). Concurrency between two simultaneous first-time checkouts is guarded only by the unique `txn_ref` index (a race can still create two rows; both are recoverable because only the paid one grants).

## Enforcement
- `PaymentCheckoutService` reads the newest open acquisition for `(user, plan)` within the TTL, loads its ledger by `payment_id`, and reuses it only when the ledger is `pending`
- Migration `V48` adds `idx_ai_subscription_pending` for that lookup and drops `idx_payment_ledger_user_plan_pending`; `txn_ref` uniqueness backs the concurrent case

## Last Reviewed
2026-11-04, by Vegalife backend team

---

# Business Rule: Risk-Flagged and Reversed Transactions Never Fulfil

## Rule ID
`BR-PAY-006`

## Status
Active

## Statement
`vnp_ResponseCode = '07'` (transaction flagged by risk rules) and `vnp_TransactionStatus = '04'` (reversed) — like every outcome other than the double zero in `BR-PAY-002` — leave the acquisition ungranted: the ledger becomes `failed`, the gateway's `response_code` is stored, an `ERROR` log records the `txnRef` for manual review, and no receipt is enqueued. Money may genuinely have moved in these cases; that is precisely why they are surfaced for a human rather than resolved automatically.

## Rationale
These are the two outcomes where the gateway and reality can diverge most expensively: a fraud-flagged transaction may still be settled and later clawed back, and a reversal means funds were returned after an initial success. Fulfilling on either would hand out paid quota that gets charged back; auto-refunding on either would destroy money the business may be entitled to. Recording and escalating is the only option that is safe in both directions.

## Scope & Exceptions
Applies to every non-double-zero outcome, with `07` and `04` additionally required to emit an `ERROR`-level log with the `txnRef` and both codes. The ledger keeps `response_code` so the review is possible without re-querying the gateway. No automatic QueryDR reconciliation ships with issue #16 (see Non-goals).

## Enforcement
- `PaymentWebhookService`'s failure branch sets `status = 'failed'`, writes `response_code`, logs `ERROR` for `07`/`04`, and returns before any grant or email work

## Last Reviewed
2026-11-04, by Vegalife backend team

---

# Business Rule: The Receipt Is Emailed Exactly Once, Off the Request Thread

## Rule ID
`BR-PAY-007`

## Status
Active

## Statement
When and only when a ledger row transitions `pending → succeeded`, one receipt message is enqueued on the outbound message queue **in the same transaction** as the ledger update and the acquisition grant. The webhook never calls SMTP directly. A message is never enqueued for `pending`, `failed`, `refunded`, or replayed-success rows, and the enqueue is guarded by the same `pending → succeeded` transition as the grant, so a retry cannot produce a second receipt.

## Rationale
Issue #16 requires a receipt on successful payment, but the moment of payment is also the moment we must not fail: VNPay has already recorded the money, so if SMTP is down and the webhook returns a non-acknowledgement, VNPay retries a notification we have already applied. Queuing inside the transaction makes the receipt as durable as the payment record and as decoupled as ADR-005's queue already makes every other outbound email; putting the enqueue on the same transition as the grant makes "exactly once" a structural property instead of a counter someone has to maintain.

## Scope & Exceptions
Applies to the purchase receipt only (plan name, amount, currency, paid-at, reference). It does not cover renewal reminders, dunning, or refunds — none of which exist. The outbound queue's own at-least-once delivery and deduplication behaviour is unchanged and documented in `docs/feats/outbound-message-queue.md`.

## Enforcement
- `PaymentWebhookService` calls `EmailService.sendPaymentReceipt(...)` (the `OutboxEmailService` implementation) inside the fulfilment transaction
- `OutboundEmailPayload.Type.RECEIPT` carries plan name, amount, currency, paid-at, and reference to the renderer `email/payment-receipt.html`
- Rendering and transport happen later on the queue consumer thread via `EmailChannelAdapter`

## Last Reviewed
2026-11-04, by Vegalife backend team

---

# Business Rule: Renewal Date Is Paid Time Plus One Calendar Month

## Rule ID
`BR-PAY-008`

## Status
Superseded by ADR-021 — no replacement

## Statement
_Retired._ This rule set `ai_subscription.renewal_date` to `paid_at` plus one calendar month on fulfilment. There is no renewal date any more: the column is dropped, purchases never expire (BR-SUBS-007), and the only date stamped at fulfilment is `granted_at = paid_at`.

## Rationale
The renewal date existed to end a monthly purchased period. With a non-expiring allowance there is no period to end, and keeping a date that no rule reads would invite a consumer to believe access lapses when it does not.

## Scope & Exceptions
None — the rule is not enforced anywhere.

## Enforcement
- Removed: `SubscriptionService.activatePlan`, the `renewal_date` column and all reads of it

## Last Reviewed
2026-11-04, by Vegalife backend team

---

# Business Rule: Only an Active, Priced, VND Plan Can Reach Checkout

## Rule ID
`BR-PAY-009`

## Status
Active

## Statement
`POST /api/subscriptions/acquire` accepts a plan only when the `ai_plan` row exists and has `active = TRUE`. An unknown code is `404 "Plan not found"`; an inactive plan is `400 "Plan is not available"`. A plan with `price_amount = 0` is never sent to VNPay — it takes the claim branch instead (BR-SUBS-008). A priced plan reaches checkout only when `price_amount > 0` and `price_currency = 'VND'`; a non-VND price is rejected with a validation message before any ledger write.

## Rationale
VNPay settles in VND only, so a non-VND price cannot be represented honestly on the gateway; and charging for a deactivated plan is either a bug or a refund waiting to happen. The zero-price case is no longer an error — it is the free-tier claim — so the check branches rather than rejects. Making these explicit preconditions keeps the failure at the point where the member can still choose a different plan, and preserves `BR-SUBS-002`: the check reads the row, never a constant.

## Scope & Exceptions
Applies to every acquire call. It does not apply to reads — `GET /api/subscriptions` still lists every active plan including FREE. A deactivated plan is rejected for acquisition but remains resolvable for history and keeps contributing allowance to members who already hold it (BR-SUBS-009). Payment **amounts** stay governed by BR-PAY-003; this rule governs whether checkout may start at all.

## Enforcement
- `PlanAcquisitionService` loads the plan by code, rejects an inactive plan, and branches on `price_amount == 0`; `PaymentCheckoutService` applies the currency check before any ledger write
- API: `404` via `ResourceNotFoundException`, `400` via `ValidationException`, both through `GlobalExceptionHandler`

## Last Reviewed
2026-11-04, by Vegalife backend team

---

# Business Rule: The Ledger Records Money Only; the Acquisition Records the Plan

## Rule ID
`BR-PAY-010`

## Status
Active

## Statement
`payment_ledger` records only the money: `user_id`, amount, currency, status, provider, provider reference, gateway response fields and timestamps. It has no `plan_id` and no `subscription_id`. The plan a payment bought is recorded on `ai_subscription.plan_id`, and the payment is linked back through `ai_subscription.payment_id` (`ON DELETE CASCADE`, unique per payment, `NULL` for a claim). Any "which plan did this payment buy?" read goes through that join.

## Rationale
Two columns claiming the same link let a ledger row name a plan for a payment that never produced an acquisition, and made "points at a subscription" meaningless once purchases stopped being a lifecycle. The acquisition is the row that represents the thing bought, so it owns the plan; the ledger is the row that represents the money, so it owns the amount and the gateway result. Read endpoints follow the same split: acquisition history names plans, payment history reports money.

## Scope & Exceptions
Applies to every write and read of `payment_ledger`. The IPN still fulfils by looking up the acquisition on `payment_id`; it never needs the plan to settle a payment, only for the receipt. A payment may legitimately exist with no granted acquisition (pending or failed) — `GET /api/payments` reports it without a plan, and the acquisition history shows it with `grantedAt: null`. Legacy succeeded payments that never produced a subscription are kept as acquisitions with `payment_id` set and no grant (BR-SUBS-007). No exception for reporting: `GET /api/payments`, `GET /api/payments/{paymentId}` and `GET /api/admin/payments` are money-only.

## Enforcement
- Migration `V48` drops `payment_ledger.plan_id` and `payment_ledger.subscription_id` and adds `ai_subscription.payment_id` with `ON DELETE CASCADE` plus `uq_ai_subscription_payment`
- `PaymentWebhookService.fulfil` loads the acquisition by `paymentId` and reads the plan from `subscription.planId`
- `PaymentHistoryService` / `PaymentHistoryItemResponse` no longer resolve or expose a plan or a nested subscription

## Last Reviewed
2026-11-04, by Vegalife backend team
