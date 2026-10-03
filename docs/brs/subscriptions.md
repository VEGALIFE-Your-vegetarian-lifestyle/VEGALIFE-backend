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

# Business Rule: Plan Limits, Prices, and Features Are Data-Driven

## Rule ID
`BR-SUBS-002`

## Status
Active

## Statement
Every plan limit, price, and per-feature flag served by the subscription APIs comes from the `ai_plan` and `ai_plan_feature` tables. No plan code, monthly request limit, price, currency, or feature key exists as a constant in Java source — including in tests, which seed or read the rows instead of restating their values. Changing the PRO price or the FREE monthly limit is a data change.

## Rationale
Plan terms are commercial facts that change for business reasons. A constant in Java means a price change becomes a code change with a release cycle, and — worse — lets different layers of the application disagree: a hardcoded `20` in one service and a seeded `20` in the database is a discrepancy that surfaces only as a wrong number in a response. Tests that restate the value have the same defect in reverse: they pass while the seed drifts.

## Scope & Exceptions
Applies to every endpoint that returns plan data (`GET /api/subscriptions`, the `currentPlan` and `usage.limit` parts of `GET /api/subscriptions/me`) and to any future feature that gates behaviour on tier. Plan **seed** data lives in the migration that creates the tables — that is data, not a constant. No exception for `code = 'FREE'`: even the FREE fallback resolves through the `ai_plan` row rather than through inlined limits.

## Enforcement
- `SubscriptionService` resolves the effective plan and its features exclusively through `PlanRepository` / `PlanFeatureRepository` queries
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
A member with no `ai_subscription` row reads as an active FREE subscriber: `tier` and `currentPlan` come from the `ai_plan` row with `code = 'FREE'`, `status = 'active'`, and `renewalDate = null`. Reading a subscription **never creates, updates, or upserts** a row — the FREE default is synthesized in memory and discarded with the response. `GET /api/subscriptions/me` therefore answers `200`, never `404`, regardless of what tables contain.

## Rationale
`ai_subscription` rows are only written by a purchase or upgrade flow, neither of which exists yet — but the read API ships first and must work for every existing member on day one. Backfilling a row per user at read time would make a read endpoint a write endpoint: it needs a transaction that can fail, it races under concurrent first requests, and it manufactures rows that a future payment flow must then reconcile. Synthesizing costs nothing and keeps the read path read-only, which is also what makes it safe to scale.

## Scope & Exceptions
Applies to `GET /api/subscriptions/me` and to any future read that resolves a member's effective tier. Applies equally before and after a payment flow exists — once upgrades ship, a row exists and is used instead, but absence still means FREE. The `latestPayment` field follows the same shape for its own data: no `payment_ledger` row means `null`, not an error. Administrative tooling that intentionally creates subscriptions is outside this rule.

## Enforcement
- `SubscriptionService.getMySubscription` falls back to the `FREE` plan lookup when `findByUserId` returns empty, and is `@Transactional(readOnly = true)` so any accidental write fails rather than commits
- Feature spec `FR-003` / `FR-008` / `FR-009` record the contract: no row written, no `404` on absent data
- API: `200 Subscription retrieved successfully` with `renewalDate: null` and `latestPayment: null`

## Last Reviewed
2026-10-04, by Vegalife backend team
