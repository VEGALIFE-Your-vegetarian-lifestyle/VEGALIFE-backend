# Feature Spec: Request Credits — One Acquire Endpoint, Acquisition History, `remainingUsage`

## Status

Approved — design final (plan v14); implementation not started.

## Author / owner

Backend (branch `feat/request-credits`); design recorded in
[ADR-021](../adrs/021-request-allowance-as-acquisition-sum.md).

## Summary

Replace the monthly AI subscription lifecycle with a lifetime request
allowance. A member's entitlement is the sum of the `request_quota` of every
plan they have acquired, minus one lifetime consumption counter. Plans are
acquired through a single endpoint — a zero-priced plan is claimed once, a
priced plan starts a VNPay checkout — and the acquisition row, not the payment
ledger, records which plan was acquired. Every usage-bearing response reports
only `remainingUsage`.

## Problem / motivation

The current model is a monthly subscription with a lifecycle:

- `ai_plan.monthly_request_limit` is a per-month allowance.
- `ai_subscription` is one row in effect at a time, with `status`,
  `renewal_date`, `extended_from_id`, `cancelled_at`, a `scheduled` successor
  and a daily expiry sweep.
- Quota is the sum of `ai_usage` rows overlapping the current UTC calendar
  month, so unused requests are destroyed at every month boundary.
- The FREE tier is synthesized by a code fallback on `code = 'FREE'`, so every
  account implicitly holds an allowance it never acquired.
- `POST /api/subscriptions/me/purchase/eligibility` guards a gate whose only
  denials are "cancel your current plan first" and "a renewal is already
  scheduled" — neither of which should exist for one-off purchases.
- `payment_ledger` carries `plan_id` and `subscription_id`, making it a second
  source of truth for a link that belongs to the acquisition.

Members pay once and lose requests they paid for. The lifecycle also exists to
service renewal, cancellation and expiry that the product does not offer.
Meanwhile, the VNPay callback cannot identify a plan on its own:
`vnp_TxnRef` is the ledger's UUID and `vnp_OrderInfo` is cosmetic, so once
`payment_ledger.plan_id` is dropped the plan must be recorded at checkout on
the row fulfilment will find.

## Goals

- Make acquired requests non-expiring: no monthly reset, no renewal, no
  expiry sweep.
- Give every account an explicit, claimable five-request FREE tier, once per
  plan per account — never an implicit fallback.
- Express entitlement as one live number per member, derived from acquisition
  rows and a lifetime consumption counter.
- Serve claims and purchases through one endpoint, with one decision table.
- Keep a complete acquisition history (claims included) and a separate,
  money-only payment history.
- Make the acquisition row the single link between a plan and a payment.

## Non-goals

- Refunds, chargebacks, or any money movement beyond the existing VNPay
  checkout and IPN.
- Usage logging per request — no `ai_request_log`.
- Proration, downgrade credits, or transfers between members.
- A new `user` column: per-member state is derived from `ai_subscription`.
- Snapshotting granted quota on the acquisition row; plan terms stay live.
- Renaming the `ai_subscription` table to match its new meaning.
- Changing the VNPay wire contract, the IPN response codes, or ADR-008's
  fulfilment authority.

## Requirements

### Functional Requirements

- [ ] FR-001: Flyway migration `V48` (latest applied is
      `V47__replace_platform_settings.sql`; re-verify against `origin/dev`
      immediately before writing — collisions have happened here) alters four
      tables and creates or drops none: `ai_plan`, `ai_subscription`,
      `payment_ledger`, `ai_usage`.
- [ ] FR-002: `ai_plan` gains a nullable `description TEXT` and renames
      `monthly_request_limit` to `request_quota`. The `FREE` row is set to
      `request_quota = 5` with a description stating it is claimable once per
      account. No `claimable` column is added: claimability is
      `active = TRUE AND price_amount = 0`.
- [ ] FR-003: `ai_subscription` gains
      `payment_id UUID REFERENCES payment_ledger(id) ON DELETE CASCADE`,
      backfilled exactly from the existing
      `payment_ledger.subscription_id` link while that column still exists.
      The pre-flight query
      `SELECT count(*) FROM ai_subscription WHERE payment_id IS NULL` must
      return `0` before the lifecycle columns are dropped.
- [ ] FR-004: `ai_subscription` drops `status`, `renewal_date`,
      `extended_from_id`, `cancelled_at` and `updated_at`, drops
      `idx_ai_subscription_status_renewal_date`, renames `started_at` to
      `granted_at`, and makes `granted_at` nullable. `created_at` and `id`
      are kept.
- [ ] FR-005: `ai_subscription` gains the indexes that replace the lifecycle:
      `uq_ai_subscription_one_claim` on `(user_id, plan_id) WHERE payment_id
      IS NULL`, `uq_ai_subscription_payment` on `(payment_id) WHERE payment_id
      IS NOT NULL`, `idx_ai_subscription_user_id`,
      `idx_ai_subscription_user_created_at (user_id, created_at DESC)`, and
      `idx_ai_subscription_pending (user_id, plan_id, created_at DESC) WHERE
      granted_at IS NULL AND payment_id IS NOT NULL`.
- [ ] FR-006: Before `payment_ledger.plan_id` is dropped, every `succeeded`
      ledger row that produced no `ai_subscription` row is preserved as an
      acquisition with its `plan_id` and `payment_id` set and
      `granted_at = NULL` — visible in history, granting no quota.
      `payment_ledger` then drops `subscription_id` and `plan_id` (with
      `plan_id`'s FK to `ai_plan`), and drops
      `idx_payment_ledger_user_plan_pending`. No columns are added;
      `idx_payment_ledger_user_paid_at` survives.
- [ ] FR-007: `ai_usage` becomes a lifetime counter: existing rows for a
      member are collapsed into the row with the lowest `id`, summing
      `request_count`; the other rows are deleted; `period_start` and
      `period_end` are dropped; the old three-column unique constraint is
      replaced by `uq_ai_usage_user UNIQUE (user_id)`.
- [ ] FR-008: Entitlement is derived, never stored:

      ```
      allowance     = Σ ai_plan.request_quota over the member's
                      ai_subscription rows with granted_at IS NOT NULL
                      (NOT filtered by ai_plan.active)

      consumed      = ai_usage.request_count

      remainingUsage = max(allowance − consumed, 0)
      ```

      `remainingUsage` is the only usage number any API returns.
- [ ] FR-009: `POST /api/subscriptions/acquire` (JWT, body
      `{"planCode": "..."}`) handles both claim and purchase. The plan is
      loaded by code (`404` if unknown) and must be active (`400 "Plan is not
      available"`). If `price_amount == 0` the member claims it; otherwise a
      checkout is created. The response always carries `remainingUsage` and an
      `action` of `CLAIMED` or `CHECKOUT_CREATED`.
- [ ] FR-010: Claiming inserts one acquisition with `payment_id = NULL` and
      `granted_at = NOW()` using a single
      `INSERT ... ON CONFLICT (user_id, plan_id) WHERE payment_id IS NULL DO
      NOTHING RETURNING id`. One row returns `200 CLAIMED`; zero rows means the
      plan was already claimed and returns `409 "Plan already claimed"`. No
      `exists()` pre-check. Two simultaneous claims yield exactly one row.
- [ ] FR-011: Checkout inserts the `payment_ledger` row and its
      `ai_subscription` row (`payment_id` set, `granted_at = NULL`) in one
      transaction, then returns `paymentId`, `txnRef`, `paymentUrl`, `amount`,
      `currency`, `status = "pending"`, `planCode`, `action =
      "CHECKOUT_CREATED"` and `remainingUsage`. Checkout-only fields are
      `NON_NULL`.
- [ ] FR-012: The reusable-pending lookup moves to the acquisition row: the
      newest acquisition for `(user, plan)` with `granted_at IS NULL AND
      payment_id IS NOT NULL` inside `app.payments.checkout-ttl` is reused only
      if its ledger row is still `pending`; the ledger is loaded by
      `payment_id`. Same `txnRef` and amount, freshly signed URL. A failed
      payment also has `granted_at = NULL`, so the ledger status must still be
      consulted.
- [ ] FR-013: IPN fulfilment loads the acquisition by
      `payment_id = ledger.id` and stamps `granted_at = paid_at`. It no longer
      calls `activatePlan` and no longer writes `ledger.subscription_id`; the
      receipt's plan comes from `subscription.plan_id`. Ledger fields
      (`status`, `paid_at`, `provider`, `provider_reference`,
      `response_code`, `bank_code`) are unchanged.
- [ ] FR-014: `GET /api/subscriptions/me/history?page&size` returns the
      member's acquisition rows newest first. Each item exposes `planCode`,
      `planName`, `grantedAt` (nullable), `createdAt`, `source`
      (`PURCHASE` | `CLAIM`), `paymentId`, `amount`, `currency` and `status`.
      The lifecycle fields `renewalDate` and `cancelledAt` are gone, and
      `status` now reports the linked payment's status (`pending`,
      `succeeded`, `failed`, `refunded`) — `null` for a claim. Pending and
      failed checkouts appear with `grantedAt: null`.
- [ ] FR-015: `GET /api/subscriptions/me` returns only
      `{"remainingUsage": N}`. `tier`, `status`, `renewalDate`, `usage`,
      `currentPlan` and `latestPayment` are removed with no replacement.
- [ ] FR-016: `GET /api/payments`, `GET /api/payments/{paymentId}` and
      `GET /api/admin/payments` stay as money-only reads. They lose `plan` and
      the nested `subscription` block; the admin variant keeps `userId` and its
      filters (`userId`, `status`, `createdFrom`, `createdTo`).
- [ ] FR-017: `POST /api/payments/checkout`,
      `POST /api/subscriptions/me/cancel` and
      `POST /api/subscriptions/me/purchase/eligibility` are removed. The
      acquire endpoint replaces checkout; cancellation and the purchase gate
      have no meaning without a lifecycle.
- [ ] FR-018: `RequestAllowanceService` gates on `remainingUsage > 0` before the
      provider call and increments `ai_usage.request_count` with one atomic upsert
      (`ON CONFLICT (user_id) DO UPDATE`). The `FREE` fallback and
      `resolvePlan` are deleted, and so is `AiQuotaGuard`. An exhausted
      allowance returns `429` with no
      `Retry-After` header and a message telling the member to claim or buy
      more requests; the send responses carry `usage: {remainingUsage}`.
- [ ] FR-019: `AdminPlanService` no longer protects `code = 'FREE'`: the
      `FREE_PLAN_CODE` constant, the `updatePlan` rejection of
      `active = false` and the `deactivatePlan` rejection are all removed.
      Admin DTOs and mappers expose `description` (`@Size(max = 500)`) and
      `requestQuota` instead of `monthlyRequestLimit`.

### Non-Functional Requirements

- [ ] NFR-MAINT-001: The claim guarantee lives in the database
      (`uq_ai_subscription_one_claim`), not in an application check, so it
      holds across instances and without a lock.
- [ ] NFR-MAINT-002: Entitlement is computed in exactly one service
      (`RequestAllowanceService`) and reused by the quota guard, the acquire
      response and `/me`. No other code re-derives an allowance.
- [ ] NFR-MAINT-003: The allowance query must never add `AND p.active`.
      Deactivation stops new acquisition only; filtering on `active` would
      silently delete members' credits. The reason is recorded in code.
- [ ] NFR-PERF-001: Allowance is one aggregate over
      `ai_subscription` joined to `ai_plan` for one member; consumption is one
      primary-key lookup on `ai_usage.user_id`; history is one page query plus
      at most two batch loads (plans, payments).
- [ ] NFR-SEC-001: Every member-scoped read takes the user id from
      `@AuthenticationPrincipal`; no member id is accepted from the client.
      Payment and acquisition reads never expose another member's rows.
- [ ] NFR-DATA-001: No hard delete of `ai_plan` or `ai_subscription` rows.
      Plans are deactivated; acquisitions are history and the only
      plan-to-payment link. `ON DELETE CASCADE` on `payment_id` exists so a
      deleted payment cannot degrade into a free claim.

## Design overview

One derived number, one acquisition table, two read shapes.

```
acquire ── price = 0 ──> claim  ──> ai_subscription(payment_id NULL, granted_at NOW())
        └─ price > 0 ──> checkout ─> payment_ledger(pending)
                                      + ai_subscription(payment_id, granted_at NULL)
                                            │
                                   IPN ─────┘ stamps granted_at = paid_at
```

The acquisition row state table:

| `payment_id` | `granted_at` | Meaning | Counts toward allowance |
|---|---|---|---|
| `NULL` | set | claimed — no money involved | yes |
| `NOT NULL` | set | purchased — payment confirmed | yes |
| `NOT NULL` | `NULL` | checkout started, payment pending or failed | no |

Acquire decision table:

| Plan state | Action | Response `action` |
|---|---|---|
| active, `price_amount = 0`, not yet claimed | insert acquisition, `payment_id = NULL` | `CLAIMED` (`200`) |
| active, `price_amount = 0`, already claimed | `409 "Plan already claimed"` | — |
| active, `price_amount > 0` | create checkout (ledger + acquisition) | `CHECKOUT_CREATED` (`200`) |
| exists but not active | `400 "Plan is not available"` | — |
| unknown code | `404 "Plan not found"` | — |

Response shapes:

```json
{ "planCode": "FREE", "action": "CLAIMED", "remainingUsage": 5 }
```

```json
{
  "planCode": "PRO", "action": "CHECKOUT_CREATED", "remainingUsage": 12,
  "paymentId": "…", "txnRef": "…", "paymentUrl": "https://sandbox.vnpayment.vn/…",
  "amount": 49000, "currency": "VND", "status": "pending"
}
```

The code path is the same decision table, not a chain of special cases:

```java
AiPlan plan = planRepository.findByCode(code).orElseThrow(notFound);
if (!plan.isActive()) throw new ValidationException("Plan is not available");
if (plan.getPriceAmount() == 0) return claimService.claimOrThrow(userId, plan);
return checkoutService.create(userId, plan, ip);
```

Acquisition history is one table read. Purchases are found through the
acquisition's `payment_id`, claims have none, and `source` tells the UI which
is which. The payment endpoints stay money-only because a payment legitimately
exists before an acquisition is fulfilled and the result page polls
`GET /api/payments/{paymentId}`.

**No snapshot.** Quota is read live from `ai_plan.request_quota`:

| Admin action | Effect on a member who already holds the plan |
|---|---|
| PRO quota 500 → 800 | instantly gains 300 |
| PRO quota 500 → 100 | allowance drops to 100; `remainingUsage` floors at 0 |
| FREE quota 5 → 0 | the free tier becomes worth nothing |
| Plan deactivated | **no change** — the allowance query ignores `active` |

### API impact

Every endpoint in this domain reads at least one altered table, so none is
untouched. Impact is classified by what the caller experiences: `GONE` = 404,
`NEW` = a route that did not exist, `BREAKING` = fails loudly (400 / missing
field / parse failure), `SILENT` = the same response meaning something
different.

| Method | Path | Impact | What changes |
|---|---|---|---|
| `POST` | `/api/subscriptions/acquire` | `NEW` | claim or checkout, decided by `price_amount` |
| `POST` | `/api/payments/checkout` | `GONE` | folded into `acquire` |
| `POST` | `/api/subscriptions/me/cancel` | `GONE` | nothing to cancel — grants never expire |
| `POST` | `/api/subscriptions/me/purchase/eligibility` | `GONE` | the gate it reports on no longer exists |
| `GET` | `/api/subscriptions` | `BREAKING` | `monthlyRequestLimit` → `requestQuota`; `+ description` |
| `GET` | `/api/admin/plans` | `BREAKING` | same rename; `+ description` |
| `POST` | `/api/admin/plans` | `BREAKING` | request body: `monthlyRequestLimit` → `requestQuota`; `+ description` |
| `PATCH` | `/api/admin/plans/{planId}` | `BREAKING` | same; **plus** `active:false` on FREE now returns `200` instead of `400` |
| `DELETE` | `/api/admin/plans/{planId}` | `SILENT` | same `200` — but now succeeds on FREE instead of `400` |
| `GET` | `/api/subscriptions/me` | `BREAKING` | **now `{remainingUsage}` only** |
| `GET` | `/api/subscriptions/me/history` | `BREAKING` | lifecycle fields → `source`, `paymentId`, `amount`, `currency`, `status` |
| `GET` | `/api/payments` | `BREAKING` | **loses `plan` and `subscription`** — money only |
| `GET` | `/api/payments/{paymentId}` | `BREAKING` | **loses `plan`** |
| `GET` | `/api/admin/payments` | `BREAKING` | **loses `plan`** — same DTO as `GET /api/payments` |
| `GET` | `/api/payments/vnpay/ipn` | `SILENT` | wire format frozen by VNPay; now stamps `granted_at` instead of activating a plan |
| `POST` | `/api/payments/vnpay/ipn` | `SILENT` | same |
| `POST` | `/api/ai/messages` | `BREAKING` | `usage: {used, limit, windowStart, windowEnd}` → `usage: {remainingUsage}` |
| `POST` | `/api/ai/messages/stream` | `BREAKING` | same, inside the SSE `done` event |
| any AI send over quota | — | `SILENT` | `429` loses `Retry-After` and the "resets next month" message |
| `GET` | `/api/ai/conversations`, `/{id}` | — | **untouched** — reads no altered table |

Tally: **3 gone, 1 new, 11 breaking, 3 silent, 1 untouched pair.** The three
silent cases need deliberate checks: `DELETE /api/admin/plans/{id}` is the one
a frontend can reach (the admin UI mirrors the old "cannot remove FREE" guard
and must drop it at the same time); the two IPN verbs are server-only.

### Migration summary

`V48` — **0 tables created, 0 tables dropped, 4 tables altered.** No change to
`user`.

| Table | Change |
|---|---|
| `ai_plan` | `+ description`; `monthly_request_limit` → `request_quota`; `FREE` set to 5 |
| `ai_subscription` | `+ payment_id` (FK); drop 5 lifecycle columns; `started_at` → nullable `granted_at`; 5 indexes |
| `payment_ledger` | drop `plan_id` and `subscription_id`; drop the pending index |
| `ai_usage` | collapse to one row per member; drop `period_start` / `period_end`; `UNIQUE (user_id)` |

Existing accounts have no acquisition rows, so `remainingUsage` is `0` until
they explicitly claim the free plan. That is intended: the old implicit FREE
fallback is gone.

## Success metrics

- No AI request ever resets: a member's `remainingUsage` changes only on a
  send, a claim, or a purchase.
- Zero `ai_subscription` rows with `granted_at IS NOT NULL` and
  `payment_id IS NULL` for a plan the member already held — the partial unique
  index makes a duplicate claim impossible.
- A plan quota edit is reflected in every holder's `remainingUsage` on the next
  read, with no backfill job.
- The migration's pre-flight count is `0` on a copy of the production database
  before the lifecycle drop (the irreversible gate).

## Acceptance criteria

**As a** member, **I want to** claim the free allowance and buy more requests
that never expire, **so that** I know exactly how much AI usage I have left —
and **as an** administrator, **I want to** manage plan terms without breaking
grants **so that** pricing and quotas stay live data.

- [ ] Given a member who has never claimed the FREE plan, when they call
      `POST /api/subscriptions/acquire` with `{"planCode":"FREE"}`, then it
      returns `200 {action:"CLAIMED", remainingUsage:5}`, one acquisition row
      exists with `payment_id NULL` and `granted_at` set, and a repeat call
      returns `409 "Plan already claimed"` without writing.
- [ ] Given two simultaneous claims for the same plan by the same member, when
      both are executed, then exactly one returns `CLAIMED` and the other
      returns `409`.
- [ ] Given an active priced plan, when the member acquires it, then it
      returns `200 {action:"CHECKOUT_CREATED", paymentId, txnRef, paymentUrl,
      amount, currency, status:"pending", remainingUsage}`, and one
      `payment_ledger` row and one `ai_subscription` row with
      `granted_at NULL` exist in the same transaction.
- [ ] Given a pending checkout for the same plan inside the TTL, when the
      member acquires it again, then the same `paymentId` and `txnRef` are
      returned with a freshly signed URL, and no second acquisition row is
      created.
- [ ] Given a checkout acquisition whose IPN reports the double zero, when
      fulfilment completes, then `granted_at` is stamped with `paid_at` and
      `remainingUsage` rises by that plan's `request_quota`.
- [ ] Given a member with several granted acquisitions and a consumed count,
      when any usage-bearing endpoint is called, then it reports
      `max(SUM(request_quota) − request_count, 0)` and never a per-plan
      breakdown.
- [ ] Given a member whose consumed count exceeds their allowance, when they
      read `/api/subscriptions/me`, then `remainingUsage` is `0` and the AI
      send returns `429` without a `Retry-After` header.
- [ ] Given an administrator who deactivates the FREE plan, then `PATCH
      active:false` and `DELETE` both return `200`, the plan disappears from
      `GET /api/subscriptions`, acquire returns `400 "Plan is not available"`,
      and every existing holder's `remainingUsage` is unchanged.
- [ ] Given an administrator who lowers a plan's `request_quota`, when a
      holder reads `/me`, then the lower quota is already reflected with no
      migration or job.
- [ ] Given a member with a claim and a purchase, when they call
      `GET /api/subscriptions/me/history`, then both rows appear newest first
      with `source` `CLAIM` / `PURCHASE`, and the claim has `paymentId`,
      `amount`, `currency` and `status` all `null`.
- [ ] Given an abandoned checkout, when the member reads history, then the row
      appears with `grantedAt: null` and grants nothing.
- [ ] Given a member calling `GET /api/payments` or
      `GET /api/admin/payments`, then each row contains only ledger fields
      (`id`, `userId` for admin, `amount`, `currency`, `status`, `provider`,
      `txnRef`, `paidAt`, `createdAt`) with no `plan` and no `subscription`.
- [ ] Given either removed route (`POST /api/payments/checkout`,
      `POST /api/subscriptions/me/cancel`,
      `POST /api/subscriptions/me/purchase/eligibility`), then it returns
      `404`.
- [ ] Given a database at V47 with multiple monthly `ai_usage` rows per member
      and existing ledgers, when V48 applies, then each member has one
      `ai_usage` row summing all months, no lifecycle columns remain, and the
      pre-flight count was `0`.

## Risks / open questions

- **`GET /api/payments` loses the plan.** It is money-only now; plan context
  lives in the acquisition history. Two frontend screens likely need to merge
  or switch source. This is breaking and grep-findable.
- **Two history reads, kept deliberately.**
  `GET /api/subscriptions/me/history` (acquisitions, complete, includes claims)
  and `GET /api/payments` (payments, money-only). They answer different
  questions, and the payment-result page polls `/{id}`.
- **The backfill is irreversible.** Once the lifecycle columns are gone,
  `payment_id IS NULL` means "claimed". Any acquisition that failed to find its
  payment would become a free claim granting quota nobody paid for. The
  pre-flight count must be `0` before the drop.
- **A paid plan can no longer be granted free.** `price_amount = 0` is the only
  claim signal; there is no `claimable` flag and no admin override.
- **`GET /api/subscriptions/me` returns only `remainingUsage`.**
  `currentPlan`, `status`, `renewalDate` and `latestPayment` are gone with no
  replacement; clients that need a plan read the acquisition history.
- **Failed and abandoned checkouts accumulate** as acquisition rows with
  `grantedAt: null`. Deliberate — an abandoned checkout is real history — but
  the rows are never cleaned up.
- **`payment_ledger` no longer self-describes.** "Which plan did this payment
  buy?" needs the acquisition join; support tooling that reads the ledger alone
  will be wrong.
- **`ON DELETE CASCADE` on `payment_id` is load-bearing.** `SET NULL` would
  turn a deleted payment into a free claim, so payments must not be hard
  deleted either.
- **Live plan terms are retroactive.** A quota decrease can reduce a member's
  remaining usage mid-flight; `remainingUsage` floors at 0 but the lost value
  is not compensated. This is the price of no snapshot.
- **No hard delete of plans or acquisitions.** Both are required for the live
  SUM and for history.
- **`remainingUsage` is the only usage number exposed.** `used` and the limit
  are not derivable from any response; support must query the database.
- **Historical months are collapsed** into one lifetime total, so
  month-by-month usage is unrecoverable after V48.
- **`monthlyRequestLimit` → `requestQuota`** is a breaking admin-API rename and
  a frontend change, not only a docs change.
- **The table name is narrower than its contents.** `ai_subscription` is now
  acquisition history; the rename was rejected to avoid churn.
- **Migration number collision.** Re-verify V48 against `origin/dev`
  immediately before writing.
- **No refunds**, unchanged.
- **The AI `429` loses `Retry-After`.** Nothing resets, so there is no correct
  value; the header and the "resets at the start of the next UTC month"
  message must both go.

## Related

- ADR: [`021-request-allowance-as-acquisition-sum.md`](../adrs/021-request-allowance-as-acquisition-sum.md)
- Business rules: [`docs/brs/subscriptions.md`](../brs/subscriptions.md)
  (BR-SUBS-001/003/004/005/006 superseded; BR-SUBS-007–011 added),
  [`docs/brs/ai.md`](../brs/ai.md), [`docs/brs/payments.md`](../brs/payments.md)
- API refs: [`post-acquire.md`](../apis/subscriptions/post-acquire.md),
  [`get-me.md`](../apis/subscriptions/get-me.md),
  [`get-history.md`](../apis/subscriptions/get-history.md),
  [`get-subscriptions.md`](../apis/subscriptions/get-subscriptions.md),
  [`get-payments.md`](../apis/payments/get-payments.md),
  [`plan-crud.md`](../apis/admin/plan-crud.md)
- Supersedes the lifecycle rules of
  [`subscription-lifecycle.md`](subscription-lifecycle.md); the payment
  mechanics (VNPay, IPN) remain governed by
  [`subscription-purchase.md`](subscription-purchase.md) and
  [ADR-008](../adrs/008-vnpay-integration.md).
