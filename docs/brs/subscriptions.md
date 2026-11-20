# Business Rules: AI Subscriptions

Constraints governing AI plans, quota accounting, and subscription reads. ID format `BR-SUBS-<NNN>`.

BR-SUBS-001/003/004/005/006 described the monthly subscription lifecycle and are superseded by [ADR-021](../adrs/021-request-allowance-as-acquisition-sum.md). BR-SUBS-002 survives with `request_quota` as the column name; BR-SUBS-007 onward are the standing rules of the acquisition model.

---

# Business Rule: AI Quota Window Is the Current UTC Calendar Month

## Rule ID
`BR-SUBS-001`

## Status
Superseded by ADR-021 — replaced by BR-SUBS-007

## Statement
_Retired._ This rule defined AI quota over the current UTC calendar month, counting `ai_usage.request_count` for rows whose `[period_start, period_end)` interval overlapped that window. The window, the per-month usage rows, and the overlap predicate all no longer exist: `ai_usage` is one lifetime row per member and the allowance never resets (BR-SUBS-007).

## Rationale
The window existed to expire an allowance the member had not used by the end of the month. Acquired requests are now non-expiring, so a periodic boundary has nothing left to mean — and keeping one would destroy requests the member acquired.

## Scope & Exceptions
None — the rule is not enforced anywhere.

## Enforcement
- Removed: window computation and the overlap aggregate in `SubscriptionService`, `usage.periodStart` / `usage.periodEnd` in the API, and `ai_usage.period_start` / `period_end`

## Last Reviewed
2026-11-04, by Vegalife backend team

---

# Business Rule: Plan Limits and Prices Are Data-Driven

## Rule ID
`BR-SUBS-002`

## Status
Active

## Statement
Every plan quota and price served by the subscription APIs comes from the `ai_plan` table. No plan code, request quota, price, or currency exists as a constant in Java source — including in tests, which seed or read the rows instead of restating their values. Changing the PRO price or a plan's `request_quota` is a data change.

## Rationale
Plan terms are commercial facts that change for business reasons. A constant in Java means a price change becomes a code change with a release cycle, and — worse — lets different layers of the application disagree: a hardcoded `5` in one service and a seeded `5` in the database is a discrepancy that surfaces only as a wrong number in a response. Tests that restate the value have the same defect in reverse: they pass while the seed drifts.

## Scope & Exceptions
Applies to every endpoint that returns plan data (`GET /api/subscriptions`, `GET /api/admin/plans`) and to the entitlement calculation, which reads `ai_plan.request_quota` live for every granted acquisition (BR-SUBS-010). Plan **seed** data lives in the migration that creates the tables — that is data, not a constant. There is no exception for the FREE tier: it is an ordinary `ai_plan` row claimed through the acquire endpoint, not a code fallback.

## Enforcement
- `PlanAcquisitionService` / `RequestAllowanceService` resolve plans exclusively through `AiPlanRepository` queries
- Seed rows are created by `V25__create_ai_subscription_tables.sql` and adjusted by `V48`; a plan change is a new migration or an admin data update
- Feature spec `NFR-MAINT-001` records the check: grep for a plan quota constant in `src/main/java` must return nothing

## Last Reviewed
2026-11-04, by Vegalife backend team

---

# Business Rule: Members Without a Subscription Row Are FREE by Default

## Rule ID
`BR-SUBS-003`

## Status
Superseded by ADR-021 — replaced by BR-SUBS-007 and BR-SUBS-008

## Statement
_Retired._ This rule made a member with no in-effect `ai_subscription` row read as an active FREE subscriber, with `tier`, `currentPlan` and `status` synthesized from the `code = 'FREE'` plan row at read time. There is no synthesized tier any more: `GET /api/subscriptions/me` returns only `remainingUsage`, and an account with no acquisition rows has an allowance of `0` until it explicitly claims the free plan.

## Rationale
The fallback existed because the FREE tier was not a purchasable or claimable thing — it was the absence of a subscription. Making the free tier an explicit acquisition removes the need to infer a tier at all, and stops every account from implicitly holding an allowance it never acquired (which made "claim once" impossible to define).

## Scope & Exceptions
None — the rule is not enforced anywhere.

## Enforcement
- Removed: the `code = 'FREE'` fallback in `SubscriptionService` and `AiQuotaGuard.resolvePlan`, and the `tier` / `currentPlan` / `status` fields of `GET /api/subscriptions/me`

## Last Reviewed
2026-11-04, by Vegalife backend team

---

# Business Rule: One In-Effect Subscription Row and Its Purchase Gate

## Rule ID
`BR-SUBS-004`

## Status
Superseded by ADR-021 — replaced by BR-SUBS-008

## Statement
_Retired._ This rule allowed at most one in-effect subscription row per member and defined a shared purchase gate: no row in effect → new purchase; same plan with no successor → one scheduled extension; different plan → `409`; same plan with a scheduled successor → `409`. With no renewal and no lifecycle, acquisitions stack: buying a plan again legitimately adds a second acquisition and its quota. The gate, the eligibility endpoint and both `409` denials are deleted.

## Rationale
The invariant existed to keep "which row is in effect" unambiguous for cancellation, expiry and the `/me` read. None of those concepts survive; entitlement is now a SUM over every granted row, which is order-independent by construction. A purchase gate on top would deny legitimate re-purchases.

## Scope & Exceptions
None — the rule is not enforced anywhere.

## Enforcement
- Removed: the shared gate method and its pessimistic lock, `POST /api/subscriptions/me/purchase/eligibility`, `activatePlan`, and the `409` messages "Cancel your current subscription before purchasing a different plan" and "A renewal is already scheduled for this plan"

## Last Reviewed
2026-11-04, by Vegalife backend team

---

# Business Rule: Cancellation Is Immediate, Cascading, and Repeat-Refused

## Rule ID
`BR-SUBS-005`

## Status
Superseded by ADR-021 — no replacement

## Statement
_Retired._ This rule defined `POST /api/subscriptions/me/cancel`: the in-effect row and every `scheduled` successor became `cancelled` immediately, and a repeat call returned `409 "Subscription already cancelled"`. There is nothing to cancel once a grant never expires; a member simply stops acquiring. The endpoint, the `cancelled` status and `cancelled_at` are deleted.

## Rationale
Cancellation was the way to stop a renewal. There is no renewal, so cancelling would have to mean revoking requests the member already acquired — the opposite of a non-expiring allowance, and a refund question this feature does not answer.

## Scope & Exceptions
None — the rule is not enforced anywhere.

## Enforcement
- Removed: `SubscriptionService.cancelMySubscription`, `POST /api/subscriptions/me/cancel`, and the `status` / `cancelled_at` columns

## Last Reviewed
2026-11-04, by Vegalife backend team

---

# Business Rule: Subscriptions Expire Within 24 Hours of Their Renewal Date

## Rule ID
`BR-SUBS-006`

## Status
Superseded by ADR-021 — no replacement

## Statement
_Retired._ This rule required the daily expiry sweep to move rows past `renewal_date` to `expired`, promoting a `scheduled` successor when one existed. Nothing expires now: `renewal_date`, the `scheduled` status, the successor link and `SubscriptionExpirySweepJob` are all deleted, and no scheduled job touches allowance.

## Rationale
The sweep existed to end a monthly period. Acquired requests have no period to end, and an expiring sweep would be the exact behaviour this feature removes.

## Scope & Exceptions
None — the rule is not enforced anywhere.

## Enforcement
- Removed: `SubscriptionExpirySweepJob`, the `(status, renewal_date)` index, and the `renewal_date` / `status` / `extended_from_id` columns

## Last Reviewed
2026-11-04, by Vegalife backend team

---

# Business Rule: Entitlement Is the Live Sum of Granted Acquisitions and Never Expires

## Rule ID
`BR-SUBS-007`

## Status
Active

## Statement
A member's request allowance is the live sum of the `request_quota` of every plan they have acquired:

```
allowance      = Σ ai_plan.request_quota over the member's ai_subscription
                 rows where granted_at IS NOT NULL (never filtered by active)
consumed       = ai_usage.request_count        (lifetime, one row per member)
remainingUsage = max(allowance − consumed, 0)
```

Nothing is stored on `user` and no granted quota is snapshotted on the acquisition row. An acquisition with `granted_at IS NULL` — a pending or failed checkout — grants nothing. Purchases and claims never expire: there is no monthly window, no renewal, no expiry sweep and no reset. `remainingUsage` is the only usage number any API returns.

## Rationale
One stored consumption counter plus a live sum is the smallest model that can answer "how many requests does this member have left?" without a credit table, a consumption ordering rule, or an expiry comparison. The subtraction has exactly one variable per side: the allowance comes only from `ai_plan`, consumption only from `ai_usage`. Deriving the allowance instead of storing it keeps plan terms honest — a quota edit applies immediately rather than drifting per row.

## Scope & Exceptions
Applies to every AI send (JSON and SSE), `GET /api/subscriptions/me`, and the `remainingUsage` field of the acquire endpoint. A member with no granted acquisitions has an allowance of `0`; this includes every pre-existing account until it claims the free plan. `consumed` may exceed `allowance` after a quota decrease; `remainingUsage` floors at `0` and the excess is not remembered. An inactive plan still contributes its quota (BR-SUBS-009). No exception for FREE or PRO.

## Enforcement
- `RequestAllowanceService` is the only implementation of the two queries and the subtraction; `AiChatService`, the acquire response and `/me` all call it (feature spec NFR-MAINT-002)
- Allowance is one aggregate over `ai_subscription` joined to `ai_plan` for one member (index `idx_ai_subscription_user_id`); consumption is one lookup by `ai_usage.user_id`
- Consumption is written by exactly one atomic upsert (`RequestAllowanceService.record`) and only after a successful reply (BR-AI-002)
- Migration `V48` collapses existing monthly usage rows into one lifetime total

## Last Reviewed
2026-11-04, by Vegalife backend team

---

# Business Rule: A Plan Is Claimed at Most Once per Member; Purchases May Repeat

## Rule ID
`BR-SUBS-008`

## Status
Active

## Statement
A zero-priced plan (`price_amount = 0`) may be claimed once per member. This is enforced by the partial unique index `uq_ai_subscription_one_claim ON ai_subscription (user_id, plan_id) WHERE payment_id IS NULL`, not by an application check. The claim is one `INSERT ... ON CONFLICT ... DO NOTHING RETURNING id`: one row returns `200 CLAIMED`, zero rows means the plan was already claimed and returns `409 "Plan already claimed"`. Priced acquisitions carry a `payment_id` and are outside the index, so buying the same plan again is legitimate and stacks another acquisition and its quota.

## Rationale
Claims have no payment to distinguish them, so "the member already holds this free plan" has to be a database fact rather than a race-prone read-then-write. An `exists()` pre-check would let two simultaneous requests both observe "not claimed" and both insert; the partial index turns the loser into a clean zero-row result with no lock or retry loop. Purchases deserve the opposite behaviour: a member buying the same plan twice has paid twice and should receive twice the quota.

## Scope & Exceptions
Applies to claims only (`payment_id IS NULL`), which in practice means `price_amount = 0` plans — claimability is `active AND price_amount = 0`, with no `claimable` column. It does not apply to `granted_at`, so a pending purchase cannot collide with a claim. Reactivating a plan does not let prior claimers claim it again; the index covers the row they already have. Free claims are not reversible: there is no refund or revoke path for quota already granted.

## Enforcement
- `PlanClaimService` issues the single `ON CONFLICT (user_id, plan_id) WHERE payment_id IS NULL DO NOTHING RETURNING id` statement; no `exists()` pre-check exists
- Migration `V48` creates `uq_ai_subscription_one_claim` and `uq_ai_subscription_payment` (one payment produces at most one acquisition)
- Concurrent-claim test required: two parallel claims → one `CLAIMED`, one `409`

## Last Reviewed
2026-11-04, by Vegalife backend team

---

# Business Rule: Deactivation Never Revokes Granted Allowance

## Rule ID
`BR-SUBS-009`

## Status
Active

## Statement
An administrator may deactivate any plan, including FREE, by `PATCH /api/admin/plans/{planId}` with `active: false` or `DELETE /api/admin/plans/{planId}` (a soft deactivation). Deactivation stops new acquisition: the plan disappears from `GET /api/subscriptions`, and acquiring it returns `400 "Plan is not available"`. It never revokes allowance already granted, because the allowance query does not filter `ai_plan.active`. Existing acquisition rows are untouched and prior claimers cannot claim the plan again even if it is reactivated.

## Rationale
`active` is a catalogue visibility flag, not an entitlement flag. If deactivation removed granted quota, retiring a product would silently delete what members already acquired — and because the quota is summed live, a single `AND p.active` in the allowance query would cause exactly that, invisibly. Separating the two means an admin can stop selling a plan without touching anyone's balance.

## Scope & Exceptions
Applies to every plan, with no FREE exception: the three `FREE_PLAN_CODE` guards in `AdminPlanService` are deleted. Deactivation is reversible (`active: true`); reactivation makes the plan acquirable again but does not restore a claim a member already used. Not covered: deleting a plan outright — hard delete is forbidden outright (BR-SUBS-011).

## Enforcement
- `AdminPlanService` has no FREE guard; `PATCH active:false` and `DELETE` both succeed on FREE (`200`)
- The allowance aggregate in `RequestAllowanceService` deliberately has **no** `AND p.active` predicate, and must never gain one (feature spec NFR-MAINT-003)
- `POST /api/subscriptions/acquire` filters on `active` **before** branching to claim or checkout, so deactivation blocks new acquisitions only

## Last Reviewed
2026-11-04, by Vegalife backend team

---

# Business Rule: Plan Quota Edits Are Retroactive

## Rule ID
`BR-SUBS-010`

## Status
Active

## Statement
Editing `ai_plan.request_quota` changes the allowance of every member who already holds that plan, on their next read, with no migration, backfill job, or per-row update. No granted quota is snapshotted on the acquisition row. Raising a quota adds the difference immediately; lowering it lowers the allowance, and `remainingUsage` floors at `0` when the new allowance is below what the member has consumed. Price edits affect only future checkouts, because the amount is frozen on the ledger at creation (BR-PAY-003).

## Rationale
Quota is a product term, not a contract sold at a point in time. A live sum means the catalogue is the single source of truth and there is no drift between "what the plan says" and "what a holder gets". A snapshot would require deciding what a mid-life edit means for every historical row, and would make the admin UI show one value while members hold another.

## Scope & Exceptions
Applies to every plan with at least one granted acquisition, including inactive plans (BR-SUBS-009). It does not apply to consumption: `ai_usage.request_count` is never adjusted by a quota edit. Setting a quota to `0` makes the plan worth nothing to existing holders; that is a deliberate consequence, not a bug. Historical acquisitions do not preserve the quota that applied when they were bought.

## Enforcement
- The allowance is a live `SUM(ai_plan.request_quota)` joined per request; there is no `granted_quota` column to fall out of date
- `AdminPlanService` writes only the `ai_plan` row; no fan-out update exists
- Tests assert that a quota change is visible to an existing holder without any other write

## Last Reviewed
2026-11-04, by Vegalife backend team

---

# Business Rule: Plans and Acquisition Rows Are Never Hard-Deleted

## Rule ID
`BR-SUBS-011`

## Status
Active

## Statement
`ai_plan` rows and `ai_subscription` rows are never hard-deleted. Plans are retired by `active = false` (BR-SUBS-009). Acquisitions are permanent history and the only record linking a plan to a payment; there is no endpoint or scheduled job that deletes them. `ai_subscription.payment_id` uses `ON DELETE CASCADE`, so a deleted `payment_ledger` row would take its acquisition with it — payments must therefore not be hard-deleted either.

## Rationale
The allowance is computed by joining acquisitions to plans, and the member's history is the acquisition table itself. Deleting either breaks a member's balance or erases evidence of a paid grant. The cascade is a safety net for data cleanup, not a licence to delete payments: with `SET NULL` a deleted payment would turn its acquisition into `payment_id = NULL`, which the schema reads as "claimed for free" and would grant quota that nobody paid for.

## Scope & Exceptions
Applies to `ai_plan`, `ai_subscription` and `payment_ledger`. `DELETE /api/admin/plans/{planId}` is a soft deactivation, not a delete, despite the verb. Pending and failed checkout acquisitions are retained even though they grant nothing (BR-SUBS-007) — they are history and are visible in `GET /api/subscriptions/me/history`. No exception for test fixtures or administrative cleanup.

## Enforcement
- `AdminPlanService.deactivatePlan` sets `active = false`; no repository delete method is exposed for plans or acquisitions
- Migration `V48` defines `payment_id UUID REFERENCES payment_ledger(id) ON DELETE CASCADE`
- Feature spec NFR-DATA-001 records the rule

## Last Reviewed
2026-11-04, by Vegalife backend team
