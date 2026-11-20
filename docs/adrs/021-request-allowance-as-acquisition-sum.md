# ADR-021: Treat AI Request Entitlement as the Live Sum of Granted Acquisitions

## Status

Accepted

## Date

2026-11-04

## Deciders

Project owner (the confirmed-decisions table in the `feat/request-credits`
plan: keep `ai_plan`, no expiry, a five-request free tier claimed once,
`price_amount = 0` as the only claim signal, one acquire endpoint, one
lifetime counter, `remainingUsage` as the only exposed number), backend
implementation.

Supersedes BR-SUBS-001, BR-SUBS-003, BR-SUBS-004, BR-SUBS-005 and
BR-SUBS-006. The plan catalogue itself (BR-SUBS-002) survives, with
`monthly_request_limit` renamed to `request_quota`.

## Context

The AI plan feature was built around a monthly subscription lifecycle:

- `ai_plan.monthly_request_limit` described a per-month allowance.
- `ai_subscription` carried a lifecycle — `status`, `started_at`,
  `renewal_date`, `extended_from_id`, `cancelled_at` — one row in effect at a
  time, with a `scheduled` successor promoted by a daily sweep.
- `payment_ledger` carried both `plan_id` and `subscription_id`, so a payment
  could name its plan directly and point at the subscription it produced.
- `ai_usage` stored one row per member per UTC calendar month
  (`period_start`, `period_end`), and quota was the sum of overlapping rows in
  the current month.
- `AiQuotaGuard` resolved the effective plan from a row with
  `status IN ('active','past_due')`, falling back to `code = 'FREE'`.

That model answers a question nobody is asking. Purchases are one-off, there
is no automatic renewal, and members kept losing paid requests to a monthly
reset they never bought into. The lifecycle also brings machinery that only
exists to service itself: a cancel endpoint whose only effect is to lose
access, an eligibility endpoint guarding a gate whose denials all die with the
lifecycle, an expiry sweep, a status enum, and `Retry-After` on the AI 429
computed from the end of the UTC month.

Two concrete blockers forced the shape of the replacement:

**Nothing at payment time identifies the plan.** `vnp_TxnRef` is the ledger's
own UUID and `vnp_OrderInfo` is a cosmetic `"Upgrade to " + plan.code` string
that is never parsed back. Once `payment_ledger.plan_id` is dropped, an IPN
callback can only know which plan was bought if the plan was recorded
somewhere that the payment can point at.

**The free tier cannot be a code fallback.** Inferring FREE in
`AiQuotaGuard` and `SubscriptionService` means every account silently holds
an allowance it never acquired, and "claim once" has nowhere to be recorded.
The free tier has to be an explicit acquisition like any other.

## Decision

**1. One counter per member, not per-purchase balances.** `ai_usage` keeps
`request_count` and loses `period_start` / `period_end`; it becomes a lifetime
consumption counter with `UNIQUE (user_id)`. There is no credit table, no
per-purchase remainder, no consumption ordering rule. Consumption alone never
yields a remainder — the allowance always comes from the plan catalogue.

**2. Entitlement is a live SUM over acquisitions, with no snapshot.**

```
allowance(member) = Σ ai_plan.request_quota
                      over that member's ai_subscription rows
                      where granted_at IS NOT NULL

consumed(member)  = ai_usage.request_count

remainingUsage    = max(allowance − consumed, 0)
```

The SUM deliberately does **not** filter `ai_plan.active`: deactivating a
plan stops new acquisitions, it never revokes allowance already granted.
Nothing is stored on `user`, and no granted quota is copied onto the
acquisition row. A quota edit in `ai_plan` is therefore retroactive for every
existing holder, which is the intended behaviour.

**3. Claim-once is enforced by a partial unique index, not an application
check.** `uq_ai_subscription_one_claim` on `(user_id, plan_id) WHERE
payment_id IS NULL` makes a second claim of the same plan impossible at the
database. The claim path is one `INSERT ... ON CONFLICT DO NOTHING RETURNING
id`: one row means `CLAIMED`, zero rows means the member already claimed it
(`409`). An `exists()` pre-check would be a TOCTOU race and is rejected.
Paid purchases have no such constraint — buying the same plan twice stacks
two acquisitions and grants the quota twice.

**4. The foreign key is flipped, and it is load-bearing.** `ai_subscription`
gains `payment_id UUID REFERENCES payment_ledger(id) ON DELETE CASCADE`;
`payment_ledger` loses `subscription_id` and `plan_id`. The acquisition is now
the row that knows which plan was acquired and, when money was involved, which
payment paid for it. `ON DELETE CASCADE` is mandatory: with `SET NULL` a
deleted payment would turn its acquisition into `payment_id = NULL`, which the
new schema reads as "claimed for free".

**5. The acquisition row is created at checkout, before the money moves.**
Because nothing the gateway echoes identifies the plan, the plan must be
written down at the only moment it is known — checkout. `PaymentCheckoutService`
inserts the `payment_ledger` row and the `ai_subscription` row in one
transaction, with `granted_at = NULL`. The VNPay IPN then stamps `granted_at`
on the acquisition found by `payment_id`. The claim path stamps it
immediately. `granted_at IS NULL` never counts toward the allowance, so an
abandoned or failed checkout grants nothing while remaining visible in
history.

| `payment_id` | `granted_at` | Meaning |
|---|---|---|
| `NOT NULL` | `NULL` | checkout started, payment not confirmed (or failed) |
| `NOT NULL` | set | purchased — the grant is live |
| `NULL` | set | claimed — no money involved |

**6. `payment_ledger` becomes money-only.** It records amount, currency,
status, provider, reference and timestamps. It does not name a plan and does
not point at an acquisition. "Which plan did this payment buy?" is answered by
joining the acquisition on `payment_id`; "which payments exist?" is answered
by the ledger alone. This keeps the ledger honest for payments that never
fulfilled and stops two tables from claiming to own the same link.

**7. One endpoint acquires a plan.** `POST /api/subscriptions/acquire` takes
`{"planCode": ...}` and branches on `price_amount == 0`: a zero-priced active
plan is claimed (or `409`), a priced active plan starts a checkout
(`CHECKOUT_CREATED`). `POST /api/payments/checkout` is removed, as are
`POST /api/subscriptions/me/cancel` and
`POST /api/subscriptions/me/purchase/eligibility`.

**8. `remainingUsage` is the only usage number any API returns.**
`GET /api/subscriptions/me` returns `{"remainingUsage": N}` and nothing else.
The AI send responses replace `usage: {used, limit, windowStart, windowEnd}`
with `usage: {remainingUsage}`. With no reset period, the AI `429` loses its
`Retry-After` header and its "resets next month" message.

## Consequences

**Positive:**

- A purchased request never expires. The member's allowance is the sum of
  everything they have acquired, minus a single lifetime counter.
- Five lifecycle concepts disappear from the codebase and the schema: status,
  renewal date, scheduled successors, cancellation, and the expiry sweep.
- The purchase gate disappears with them. Re-buying a plan is legitimate and
  stacks; "cancel before switching" no longer exists.
- Claiming is exactly-once by construction and race-safe without a lock or a
  retry loop.
- The free tier is explicit history rather than an implicit code fallback, so
  an account with no acquisitions genuinely has zero remaining usage until it
  claims.
- Plan terms stay live data: an admin quota edit applies to every holder
  immediately, and deactivation is reversible without touching grants.
- One acquisition table answers both "what has this member acquired" and
  "which plan did this payment buy", so the member history endpoint is
  complete (claims included) instead of partial.

**Negative / trade-offs:**

- No snapshot means no price or quota lock-in. Lowering a plan's quota
  retroactively lowers every holder's allowance, and `remainingUsage` floors
  at 0. Historical acquisitions do not preserve the terms they were bought
  under. Accepted deliberately; the alternative (a `granted_quota` column)
  reintroduces the drift the live SUM exists to avoid.
- Entitlement reads are a join and an aggregate instead of a column read.
  Both are index-backed and scoped to one member, so the cost is bounded.
- The migration's backfill is the one irreversible step. After the lifecycle
  columns are dropped, `payment_id IS NULL` *means* "claimed", so any
  acquisition that failed to find its payment would silently become a free
  claim. The pre-flight count must be zero before the drop.
- Abandoned and failed checkouts leave acquisition rows with
  `granted_at = NULL` visible in history. That is deliberate — an abandoned
  payment is real history — but the rows accumulate.
- `ai_subscription` is now narrower than its contents: it is acquisition
  history, not a subscription. The name is kept to avoid a rename, and that
  naming debt is recorded.
- The ledger can no longer self-describe a payment. Support flows that ask
  "what did this buy?" must join the acquisition, and `GET /api/payments`
  stops returning a plan.
- Removing the status column means an operator can no longer count "active
  subscriptions"; that concept no longer exists.

## Alternatives considered

**Per-purchase credit balances (a credit table with a remaining count per
purchase).** Would allow expiry and partial consumption per purchase, and
would make refunds attributable to a specific grant. Rejected: nothing expires,
so a balance is a counter with extra steps; it needs an ordering rule ("which
balance do I spend first"), a write per request per balance, and a migration
of existing history. One lifetime counter plus a live SUM answers the only
question the API asks.

**Snapshot the granted quota on the acquisition.** Would freeze the terms a
member bought under and make admin edits non-retroactive. Rejected by the
owner: plan terms are live catalogue data. A snapshot also cannot express the
FREE tier's "five requests, always" without either duplicating the value or
going stale when the catalogue changes.

**Check claim-once in the application (`exists()` then insert).** Simpler to
read, and what the old lifecycle-style guards used. Rejected: two simultaneous
claims both observe "not claimed" and both insert. A database constraint is
the only place the guarantee can live without a pessimistic lock, and the
`ON CONFLICT` path turns the race into a clean `409` for the loser.

**Keep `payment_ledger.plan_id` and `subscription_id`.** Would let the ledger
name its plan and the payment list keep returning plan context. Rejected: it
creates two sources of truth for the same link, lets a ledger row claim a plan
for a payment that never fulfilled, and blocks the acquisition from being the
single history read. The link belongs on the row that represents the
acquisition.

**Use `ON DELETE SET NULL` for `ai_subscription.payment_id`.** Softer on
deletion. Rejected outright: `NULL` is the schema's "claimed for free" marker,
so deleting a payment would silently grant quota. `CASCADE` makes the
consequence explicit.

**Keep the monthly reset and only add non-expiring purchases on top.**
Smallest change to `ai_usage`. Rejected: two allowance models in one counter,
a `Retry-After` that is correct for one of them and a lie for the other, and a
member who buys 500 requests in March would still lose them in April.

**Drop `GET /api/payments` entirely and serve everything from acquisition
history.** One list, no duplicate read. Rejected: the payment-result page polls
`GET /api/payments/{id}` and the admin support view needs money-only filters
(user, status, date). The two endpoints answer different questions and are
kept deliberately — acquisition history is complete, payment history is
money-only.

## References

- Feature: [Request Credits — One Acquire Endpoint, Acquisition History,
  `remainingUsage`](../feats/request-credits.md)
- Business rules: [AI Subscriptions](../brs/subscriptions.md) (BR-SUBS-002
  survives; 001/003/004/005/006 superseded by this ADR; 007–011 added),
  [AI Chat](../brs/ai.md), [Payments](../brs/payments.md)
- API: [POST /api/subscriptions/acquire](../apis/subscriptions/post-acquire.md),
  [GET /api/subscriptions/me](../apis/subscriptions/get-me.md),
  [GET /api/subscriptions/me/history](../apis/subscriptions/get-history.md)
- [ADR-008: Integrate VNPay Through a Hand-Rolled Thin Client, Fulfilling Only
  From Its IPN Webhook](008-vnpay-integration.md)
