# Business Rules: AI Subscriptions

Constraints governing AI plans, quota accounting, and subscription reads. ID format `BR-SUBS-<NNN>`.

---

# Business Rule: AI Quota Window Is the Current UTC Calendar Month

## Rule ID
`BR-SUBS-001`

## Status
Active

## Statement
A member's AI quota for a read is counted over the current UTC calendar month: the window opens at `00:00:00 UTC` on the first day of the month and closes at `00:00:00 UTC` on the first day of the next month. `used` is the sum of `ai_usage.request_count` for rows of that user whose `[period_start, period_end)` interval **overlaps** that window — not rows nested inside it, and not rows whose period starts after the window opened. Usage rows the quota recorder writes must themselves be non-overlapping (monthly rows are the expected shape), because overlapping persisted periods would be double-counted by this sum.

## Rationale
The overlap predicate is what makes the sum correct for a caller whose usage rows and the calendar window disagree at the edges: a row spanning a month boundary still contributes its portion, and a row entirely outside contributes nothing. A stricter `period_start >= windowStart AND period_start < windowEnd` predicate would silently drop a boundary-spanning row. Non-overlapping persisted periods are the complementary half of the same guarantee — the read side can only assume the write side does not overlap itself.

## Scope & Exceptions
Applies to every read of AI quota, starting with `GET /api/subscriptions/me`. It does **not** align with `ai_subscription.renewal_date`: for a mid-month subscriber the calendar window drifts from the billing period, and aligning the two is a deliberate follow-up decision, not a silent behaviour change. No exception for any tier — FREE and PRO use the same window.

## Enforcement
- `SubscriptionService` computes the window from the current instant in UTC and issues one aggregate query against `ai_usage` (index `idx_ai_usage_user_id`), never an in-memory scan
- The quota recorder (a future feature) is the write-side half: it must emit non-overlapping `[period_start, period_end)` rows
- API: `usage.periodStart` / `usage.periodEnd` are returned so the caller sees the window actually used; `used` is `0`, never `null`, when no row matches

## Last Reviewed
2026-10-04, by Vegalife backend team

---

# Business Rule: Plan Limits and Prices Are Data-Driven

## Rule ID
`BR-SUBS-002`

## Status
Active

## Statement
Every plan limit and price served by the subscription APIs comes from the `ai_plan` table. No plan code, monthly request limit, price, or currency exists as a constant in Java source — including in tests, which seed or read the rows instead of restating their values. Changing the PRO price or the FREE monthly limit is a data change.

## Rationale
Plan terms are commercial facts that change for business reasons. A constant in Java means a price change becomes a code change with a release cycle, and — worse — lets different layers of the application disagree: a hardcoded `20` in one service and a seeded `20` in the database is a discrepancy that surfaces only as a wrong number in a response. Tests that restate the value have the same defect in reverse: they pass while the seed drifts.

## Scope & Exceptions
Applies to every endpoint that returns plan data (`GET /api/subscriptions`, the `currentPlan` and `usage.limit` parts of `GET /api/subscriptions/me`) and to any future feature that gates behaviour on tier. Plan **seed** data lives in the migration that creates the tables — that is data, not a constant. No exception for `code = 'FREE'`: even the FREE fallback resolves through the `ai_plan` row rather than through inlined limits.

## Enforcement
- `SubscriptionService` resolves the effective plan exclusively through `AiPlanRepository` queries
- Seed rows are created by `V25__create_ai_subscription_tables.sql`; a plan change is a new migration or a data update
- Feature spec `NFR-MAINT-001` records the check: grep for a plan limit constant in `src/main/java` must return nothing

## Last Reviewed
2026-10-04, by Vegalife backend team

---

# Business Rule: Members Without a Subscription Row Are FREE by Default

## Rule ID
`BR-SUBS-003`

## Status
Active

## Statement
A member with no `ai_subscription` row **in effect** reads as an active FREE subscriber: `tier` and `currentPlan` come from the `ai_plan` row with `code = 'FREE'`, `status = 'active'`, and `renewalDate = null`. A row is *in effect* iff its status is `active` or `past_due`; rows in `cancelled`, `expired`, or `scheduled` status do not count — so a cancelled member, a member whose rows have all expired, and a member holding only an unactivated `scheduled` extension all read as FREE. Reading a subscription **never creates, updates, or upserts** a row — the FREE default is synthesized in memory and discarded with the response. `GET /api/subscriptions/me` therefore answers `200`, never `404`, regardless of what tables contain.

## Rationale
`ai_subscription` rows are written only by purchase fulfilment and the lifecycle actions (cancel, extension, sweep) — never by a read. Backfilling a row per user at read time would make a read endpoint a write endpoint: it needs a transaction that can fail, it races under concurrent first requests, and it manufactures rows that a payment flow must then reconcile. The same reasoning covers a member whose rows are all terminal or who holds only a `scheduled` row: nothing is in effect, so synthesizing the FREE default is the correct reading of "no current subscription". Synthesizing costs nothing and keeps the read path read-only, which is also what makes it safe to scale.

## Scope & Exceptions
Applies to `GET /api/subscriptions/me` and to any future read that resolves a member's effective tier. Absence of a row in effect means FREE in three shapes: no rows at all, only terminal rows (`cancelled` / `expired`), or only an unactivated `scheduled` row. An `active` row whose `renewal_date` has passed but which the daily sweep has not yet processed still counts as in effect — reads show `active` for at most ~24 h past lapse (BR-SUBS-006). The `latestPayment` field follows the same shape for its own data: no `payment_ledger` row means `null`, not an error. Administrative tooling that intentionally creates subscriptions is outside this rule.

## Enforcement
- `SubscriptionService.getMySubscription` resolves the member's row in effect (status `active` or `past_due`) and falls back to the `FREE` plan lookup when the query returns empty; the method is `@Transactional(readOnly = true)` so any accidental write fails rather than commits
- Feature specs record the contract: `FR-003` / `FR-008` / `FR-009` (`docs/feats/subscription-api.md`) and `FR-011` (`docs/feats/subscription-lifecycle.md`) — no row written, no `404` on absent data
- API: `200 Subscription retrieved successfully` with `renewalDate: null` and `latestPayment: null`

## Last Reviewed
2026-10-07, by Vegalife backend team

---

# Business Rule: One In-Effect Subscription Row and Its Purchase Gate

## Rule ID
`BR-SUBS-004`

## Status
Active

## Statement
At most one `ai_subscription` row of a member is in effect (`active` or `past_due`) at any instant, and while one is, the only further row a purchase may create is that row's single `scheduled` successor. One shared purchase gate decides from the row in effect:
1. **None in effect** — any purchasable plan may be purchased; fulfilment creates a new `active` row (`started_at = paidAt`, `renewal_date = paidAt + 1 month`, BR-PAY-008).
2. **Row in effect, same plan, no `scheduled` successor** — the same plan may be purchased once more; fulfilment creates a `scheduled` row with `extended_from_id` pointing at the current row, `started_at` = current `renewal_date`, `renewal_date` = current `renewal_date + 1 month`, leaving the current row untouched.
3. **Row in effect, different plan** — refused with `409`: "Cancel your current subscription before purchasing a different plan".
4. **Row in effect, same plan, `scheduled` successor exists** — refused with `409`: "A renewal is already scheduled for this plan".

The gate exists in exactly one method. The read-only eligibility endpoint and IPN fulfilment both call it; when fulfilment (BR-PAY-001, the authority) finds a violation it writes no subscription row and logs WARN with userId, planId, and reason — the payment stays succeeded.

## Rationale
Two rows in effect at once would make cancel ambiguous (which row ends access?), history read misleading, and the `/me` response order-dependent — the invariant the whole lifecycle reads through. The single-successor limit prevents stacking extensions faster than time passes: buying the same plan twelve times in one sitting would be prepaying, a product decision that was never designed. Refusing a different plan while one is active avoids silently inventing proration or downgrade semantics. Running the gate in one method (rather than re-deriving branches in the endpoint and in fulfilment) is what keeps the eligibility answer the frontend sees identical to the decision fulfilment later makes.

## Scope & Exceptions
Applies to every user-triggered write path of `ai_subscription`: the eligibility endpoint (which checks and writes nothing), and IPN fulfilment (which writes). Plan-level checks — unknown plan (`404 Plan not found`), inactive plan, price ≤ 0, non-VND (BR-PAY-009) — are a separate concern that runs **before** this gate. Reads (`GET /api/subscriptions/me`, history) do not gate; the cancel endpoint does not gate (BR-SUBS-005 supersedes it for that action). Checkout itself still only creates a `payment_ledger` row — it never touches `ai_subscription`. Administrative tooling is outside this rule. The invariant is enforced application-side: the issue scopes the migration to four named changes, so no partial unique index guards it — correctness rests on the gate plus the pessimistic write lock on the member's rows (pattern: `findByIdForUpdate` in `PaymentLedgerRepository`).

## Enforcement
- `SubscriptionService` owns the single gate method; the eligibility endpoint and `PaymentCheckoutService.activatePlan` both call it (feature spec NFR-MAINT-001)
- Gate and cancel run under a pessimistic write lock so concurrent purchases cannot both pass branch 1
- API: `409` messages exactly as stated; `docs/apis/subscriptions/post-purchase-eligibility.md`
- Feature spec FR-006 / FR-007 / FR-008 (`docs/feats/subscription-lifecycle.md`)

## Last Reviewed
2026-10-07, by Vegalife backend team

---

# Business Rule: Cancellation Is Immediate, Cascading, and Repeat-Refused

## Rule ID
`BR-SUBS-005`

## Status
Active

## Statement
`POST /api/subscriptions/me/cancel` sets the member's row in effect to `cancelled` with `cancelled_at = now`, and in the same transaction every `scheduled` row of that member to `cancelled` with the same `cancelled_at`. The effect is immediate: the next read of `GET /api/subscriptions/me` treats the member as FREE (BR-SUBS-003). There is no grace period, no un-cancel endpoint, and no refund handling — a later purchase starts a brand-new subscription. When no row is in effect (already cancelled, expired, or FREE), the endpoint refuses with `409`: "Subscription already cancelled", writing nothing.

## Rationale
Immediate, permanent loss of access is the only cancellation semantics that needs no follow-up machinery: no scheduled downgrade, no access window to honour. Cascading to every `scheduled` row is required for that permanence — a surviving `scheduled` successor would be promoted to `active` by the sweep once its `renewal_date` arrives, silently resurrecting access nobody asked to keep. The 409-on-repeat guard makes the endpoint idempotent to retry: a double-click cannot double-write, and the message tells the caller the desired end state already holds. Cancelling forfeits any remaining paid period — deliberate, because proration is a non-goal of the lifecycle feature.

## Scope & Exceptions
Applies to `POST /api/subscriptions/me/cancel` for the authenticated member only (the target row set is derived from the JWT, never from a parameter — no IDOR). Applies to a row in `active` status; a `past_due` row in effect is treated identically for completeness; `cancelled` / `expired` / FREE members receive the 409. `scheduled` rows are never a cancel target on their own — they are reached only as cascade members of an in-effect row. Amounts already paid are governed by the payment rules (BR-PAY-001 onward); this rule deliberately says nothing about refunds.

## Enforcement
- `SubscriptionService.cancelMySubscription`: one transaction, pessimistic lock, cascade update of the in-effect row plus all `scheduled` rows of the member
- API: `200` with `{"status": "cancelled", "cancelledAt": ...}`; `409` via the existing `DuplicateResourceException` → `GlobalExceptionHandler`, no handler changes
- Feature spec FR-003 / FR-004 / FR-005 (`docs/feats/subscription-lifecycle.md`); `docs/apis/subscriptions/post-cancel.md`

## Last Reviewed
2026-10-07, by Vegalife backend team

---

# Business Rule: Subscriptions Expire Within 24 Hours of Their Renewal Date

## Rule ID
`BR-SUBS-006`

## Status
Active

## Statement
Once a row's `renewal_date` has passed, it must not remain in effect past the next run of the daily expiry sweep (`0 0 0 * * *` UTC, gated on `app.scheduling.enabled`). For every `active` / `past_due` row with `renewal_date <= now`: if that member has a `scheduled` successor, the old row becomes `expired` and the successor becomes `active` with **both rows' dates unchanged**; otherwise the old row alone becomes `expired`. Reads may therefore still show `active` for at most ~24 hours after lapse — in that window the member keeps access, in the member's favour. The sweep is a query over `(status, renewal_date)`, so it is idempotent and catches up automatically after downtime: rows that lapsed while the scheduler was off are processed on the next run.

## Rationale
A daily sweep matches the issue's design and keeps the cost proportional to rows actually due, not to total subscriptions (BR-SCALE-001). Promoting an already-paid `scheduled` successor rather than creating anything preserves BR-PAY-001: the sweep never invents a paid period, it only activates the period the member's earlier payment purchased. Expiring instead of renewing keeps the payment system the sole source of subscription starts — automatic renewal charging is a non-goal. The ≤24 h lag is the accepted trade-off for a cron over a per-row event; a member is never disadvantaged (their access outlives payment by hours, never the reverse).

## Scope & Exceptions
Applies to rows in `active` or `past_due` status with `renewal_date <= now`, and only through the sweep job — no read path, endpoint, or payment callback performs expiry as a side effect. The sweep never creates rows, never touches `cancelled` or `expired` rows, never alters dates, and never writes to `payment_ledger`. When the scheduler is disabled (`app.scheduling.enabled = false`, e.g. some local/test environments) nothing expires — consumers in that configuration must read `renewal_date` themselves.

## Enforcement
- `SubscriptionExpirySweepJob` (thin, following `PendingFilterSweepJob`) delegates to a `SubscriptionService` sweep method; daily cron, UTC, `app.scheduling.enabled`-gated
- Migration V27 adds the `(status, renewal_date)` index backing the due-rows query (feature spec FR-001 / NFR-SCALE-001)
- Feature spec FR-010 / FR-012 (`docs/feats/subscription-lifecycle.md`)

## Last Reviewed
2026-10-07, by Vegalife backend team
