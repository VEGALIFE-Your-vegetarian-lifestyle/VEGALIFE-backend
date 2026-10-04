# Business Rules: Payments (VNPay Checkout & IPN)

Constraints governing payment checkout, webhook fulfilment, and the receipt email for AI plan purchases. ID format `BR-PAY-<NNN>`. Companion to the plan/quota rules in `subscriptions.md` (`BR-SUBS-001` … `BR-SUBS-003`).

Enforcement rows describe the contract implemented by issue #16; the implementation state of that work is tracked in `docs/feats/subscription-purchase.md`.

---

# Business Rule: The Payment Webhook Is the Sole Source of Truth for Fulfilment

## Rule ID
`BR-PAY-001`

## Status
Active

## Statement
A subscription is upgraded only while handling a checksum-valid VNPay IPN notification on `GET|POST /api/payments/vnpay/ipn`. The browser redirect after payment — VNPay's `vnp_ReturnUrl`, landing on a **frontend** result page — is display-only: the backend exposes no return endpoint, and no code path that runs on redirect, query string, or client-supplied flag may write `payment_ledger` or `ai_subscription`.

## Rationale
`vnp_ReturnUrl` is requested by us but navigated by the payer, so every parameter on it is attacker-controlled: anyone can craft a URL that "returns successfully" without paying. A webhook is not unforgeable either, but it is verifiable — the HMAC-SHA512 signature can only be produced with the shared secret — so it is the one channel that can carry an authoritative outcome. Splitting the roles also means the outcome survives the user closing the tab before the redirect lands.

## Scope & Exceptions
Applies to every payment-initiating and payment-resolving path in the system, including any future gateway. The frontend result page may render whatever it likes from the redirect's query parameters, but must read the member's real tier from `GET /api/subscriptions/me` before claiming success. No exception for the "user obviously paid" case: if VNPay never delivers a notification, the payment stays `pending` (see the reconciliation gap in `docs/feats/subscription-purchase.md`).

## Enforcement
- `PaymentController` maps only `POST /api/payments/checkout` and `GET|POST /api/payments/vnpay/ipn`; there is no `/vnpay/return` route
- `PaymentWebhookService` is the only caller of `SubscriptionService.activatePlan(...)`
- `SecurityConfig` permits the IPN path on an exact match, not a wildcard

## Last Reviewed
2026-10-04, by Vegalife backend team

---

# Business Rule: A Payment Counts as Paid Only on a Double Zero

## Rule ID
`BR-PAY-002`

## Status
Active

## Statement
A notification fulfils a payment only when `vnp_ResponseCode = '00'` **and** `vnp_TransactionStatus = '00'` are both present in the verified payload. Either one alone is not a successful payment. The HTTP `200` + `RspCode 00` acknowledgement returned to VNPay means *"notification received and recorded"*, never *"payment succeeded"*.

## Rationale
VNPay uses two independent codes on purpose: `vnp_ResponseCode` is the acquirer's authorisation result and `vnp_TransactionStatus` is the settlement-side status of the order, and they can disagree — a transaction can be authorised then left processing, or reported with a non-zero response code while money moves. Conflating them, or mistaking the acknowledgement for success, would upgrade accounts on unauthorised money and would be invisible until reconciliation.

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
The checksum only proves VNPay (or someone with the secret) sent *these* parameters — it says nothing about whether the parameters describe the order we think they do. A notification forged against a cheap order, or delivered for an order created before a price change, would otherwise upgrade a member for the wrong money. Freezing the amount at creation also means a price change between checkout and payment cannot reprice an in-flight order.

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
For a given `payment_ledger` row, `pending → succeeded` is executed at most once. VNPay retries an IPN up to ten times over five minutes, and every replay of a terminal row is acknowledged with `RspCode 00` while performing **no** state change: no second `paid_at`, no re-run of the subscription upsert, no second receipt email. A replay whose payload disagrees with the recorded outcome is logged at `WARN` and still acked `00` — a `succeeded` row is never downgraded by a later callback.

## Rationale
Webhook delivery is at-least-once by construction, so idempotency is not an optimisation, it is a correctness requirement: without it a single retry upgrades the subscription twice (harmless today only by luck of the upsert), emails the member a duplicate receipt, and rewrites `paid_at`, breaking `GET /api/subscriptions/me`'s "latest payment" ranking. Acking `00` on replay is what stops VNPay from burning its retry budget on a notification we have already fully handled; acking a *conflict* instead would trigger ten more retries for no gain.

## Scope & Exceptions
Applies to all IPN replays and to the concurrent case: two deliveries of the same notification are serialised by a pessimistic lock on the ledger row, so only one can observe `pending`. The companion rule for the request side is `BR-PAY-005`. Refunds are out of scope for issue #16, so `refunded` is unreachable here; a future refund feature must extend this rule rather than reopen `succeeded`.

## Enforcement
- `PaymentWebhookService` locks the row (`SELECT ... FOR UPDATE`) and re-checks status inside the lock
- Only the `pending → succeeded` branch enqueues the receipt, so the outbox sees it once
- Unique index on `payment_ledger.txn_ref` (migration `V26`) makes a duplicate reference impossible

## Last Reviewed
2026-10-04, by Vegalife backend team

---

# Business Rule: One In-Flight Checkout per Member and Plan

## Rule ID
`BR-PAY-005`

## Status
Active

## Statement
A member has at most one *open* checkout per plan: when `POST /api/payments/checkout` is called and a `pending` `payment_ledger` row for the same `(user, plan)` exists and was created within `app.payments.checkout-ttl` (default 30 minutes), that row is reused — same `txnRef`, same amount, a freshly signed payment URL — instead of a new row and a second VNPay order. Outside the TTL, or once the row has left `pending`, a new row is created.

## Rationale
The checkout endpoint is not naturally idempotent: a double-click, a retried POST, or a re-opened pricing page would otherwise stack several live VNPay orders for one purchase, any one of which could later be paid — leaving multiple succeeded rows for one upgrade and no way to tell which order the member thought they were paying. Reusing the open row makes the request idempotent for its whole in-flight lifetime, which is what issue #16's "idempotent payment requests" asks for. The TTL matters because a stale pending row must never block someone from starting a fresh attempt after an abandoned payment page.

## Scope & Exceptions
Applies to authenticated checkout only, scoped to the caller's own user id — one member's open checkout never affects another's, and never another plan's. The reuse window is configuration, not a product constant: raising it keeps orders alive longer, lowering it lets members start over sooner. Concurrency between two simultaneous first-time checkouts is guarded only by the unique `txn_ref` index (a race can still create two rows; both are recoverable because only the paid one fulfils) — see the risk noted in the feature spec.

## Enforcement
- `PaymentCheckoutService` reads the newest open row for `(user, plan)` within the TTL before inserting
- Migration `V26` adds a partial index supporting that lookup; `txn_ref` uniqueness backs the concurrent case

## Last Reviewed
2026-10-04, by Vegalife backend team

---

# Business Rule: Risk-Flagged and Reversed Transactions Never Fulfil

## Rule ID
`BR-PAY-006`

## Status
Active

## Statement
`vnp_ResponseCode = '07'` (transaction flagged by risk rules) and `vnp_TransactionStatus = '04'` (reversed) — like every outcome other than the double zero in `BR-PAY-002` — leave the subscription untouched: the ledger becomes `failed`, the gateway's `response_code` is stored, an `ERROR` log records the `txnRef` for manual review, and no receipt is enqueued. Money may genuinely have moved in these cases; that is precisely why they are surfaced for a human rather than resolved automatically.

## Rationale
These are the two outcomes where the gateway and reality can diverge most expensively: a fraud-flagged transaction may still be settled and later clawed back, and a reversal means funds were returned after an initial success. Fulfilling on either would hand out paid quota that gets charged back; auto-refunding on either would destroy money the business may be entitled to. Recording and escalating is the only option that is safe in both directions.

## Scope & Exceptions
Applies to every non-double-zero outcome, with `07` and `04` additionally required to emit an `ERROR`-level log with the `txnRef` and both codes. The ledger keeps `response_code` so the review is possible without re-querying the gateway. No automatic QueryDR reconciliation ships with issue #16 (see Non-goals).

## Enforcement
- `PaymentWebhookService`'s failure branch sets `status = 'failed'`, writes `response_code`, logs `ERROR` for `07`/`04`, and returns before any subscription or email work

## Last Reviewed
2026-10-04, by Vegalife backend team

---

# Business Rule: The Receipt Is Emailed Exactly Once, Off the Request Thread

## Rule ID
`BR-PAY-007`

## Status
Active

## Statement
When and only when a ledger row transitions `pending → succeeded`, one receipt message is enqueued on the outbound message queue **in the same transaction** as the ledger update and the subscription upsert. The webhook never calls SMTP directly. A message is never enqueued for `pending`, `failed`, `refunded`, or replayed-success rows, and the enqueue is guarded by the same `pending → succeeded` transition as the upgrade, so a retry cannot produce a second receipt.

## Rationale
Issue #16 requires a receipt on successful payment, but the moment of payment is also the moment we must not fail: VNPay has already recorded the money, so if SMTP is down and the webhook returns a non-acknowledgement, VNPay retries a notification we have already applied. Queuing inside the transaction makes the receipt as durable as the payment record and as decoupled as ADR-005's queue already makes every other outbound email; putting the enqueue on the same transition as the upgrade makes "exactly once" a structural property instead of a counter someone has to maintain.

## Scope & Exceptions
Applies to the purchase receipt only (plan name, amount, currency, paid-at, reference). It does not cover renewal reminders, dunning, or refunds — none of which exist. The outbound queue's own at-least-once delivery and deduplication behaviour is unchanged and documented in `docs/feats/outbound-message-queue.md`.

## Enforcement
- `PaymentWebhookService` calls `EmailService.sendPaymentReceipt(...)` (the `OutboxEmailService` implementation) inside the fulfilment transaction
- `OutboundEmailPayload.Type.RECEIPT` carries plan name, amount, currency, paid-at, and reference to the renderer `email/payment-receipt.html`
- Rendering and transport happen later on the queue consumer thread via `EmailChannelAdapter`

## Last Reviewed
2026-10-04, by Vegalife backend team

---

# Business Rule: Renewal Date Is Paid Time Plus One Calendar Month

## Rule ID
`BR-PAY-008`

## Status
Active

## Statement
On fulfilment, `ai_subscription.renewal_date` is set to `paid_at` plus one calendar month, in UTC. It is **not** aligned to the quota window of `BR-SUBS-001`, and the quota window is not changed to align with it.

## Rationale
The renewal date answers "when does this purchased period end", which is anchored to when the money arrived — a member who pays on the 14th buys a period ending on the 14th of the next month. `BR-SUBS-001` already documents that the quota window deliberately stays on the UTC calendar month and that aligning the two is a separate product decision; changing either side silently here would move every member's quota boundary as a side effect of a payment feature.

## Scope & Exceptions
Applies to the first purchase and to any subsequent upgrade of an existing subscription row (the row is upserted, `started_at` retains the original value, `renewal_date` moves forward from the new `paid_at`). Timezone is UTC throughout: no local-time arithmetic. Proration, mid-period upgrades, and automatic renewal are out of scope.

## Enforcement
- `SubscriptionService.activatePlan(userId, planId, paidAt)` computes `renewal_date = paidAt + 1 month` in UTC and is the only writer
- `GET /api/subscriptions/me` continues to report the quota window independently (`BR-SUBS-001`)

## Last Reviewed
2026-10-04, by Vegalife backend team

---

# Business Rule: Only Active, Priced VND Plans Are Purchasable

## Rule ID
`BR-PAY-009`

## Status
Active

## Statement
`POST /api/payments/checkout` accepts a plan only when the `ai_plan` row exists, has `active = TRUE`, has `price_amount > 0`, and has `price_currency = 'VND'`. An unknown code is `404`; a plan that exists but fails any of the other three conditions is `400` with a validation message. The seeded FREE plan (0 VND) is therefore never purchasable, and no non-VND plan can reach VNPay.

## Rationale
VNPay settles in VND only, so a non-VND price cannot be represented honestly on the gateway; and charging for a free or deactivated plan is either a bug or a refund waiting to happen. Making it an explicit precondition rather than a runtime surprise keeps the failure at the point where the member can still choose a different plan, and preserves `BR-SUBS-002`: the check reads the row, never a constant, so enabling or retiring a plan is a data change with no code change.

## Scope & Exceptions
Applies to every checkout call. It does not apply to reads — `GET /api/subscriptions` still lists every active plan including FREE. If product later wants a discounted or free paid tier, this rule is the single place to change, and the gateway's zero-amount handling must be settled at the same time.

## Enforcement
- `PaymentCheckoutService` loads the plan by code and applies the four conditions before any ledger write
- API: `404` via `ResourceNotFoundException`, `400` via `ValidationException`, both through `GlobalExceptionHandler`

## Last Reviewed
2026-10-04, by Vegalife backend team
