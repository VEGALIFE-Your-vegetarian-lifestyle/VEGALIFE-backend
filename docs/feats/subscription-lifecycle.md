# Feature Spec: AI Subscription Lifecycle (Cancel, Extension, History, Expiry)

## Status

In review

## Author / owner

zuyzz (issue #112)

## Summary

Adds the subscription lifecycle to the AI plan feature: members can cancel
their active plan immediately, extend the same plan before it expires,
browse their full subscription history, and a daily sweep expires rows
whose renewal date has passed — turning `ai_subscription` from a
write-once purchase record into a multi-row lifecycle per member.

## Problem / motivation

Issue #112. `ai_subscription` today has a `UNIQUE(user_id)` constraint, so a
member can hold at most one row ever: after a cancelled or lapsed
subscription, the member can never subscribe again without a schema change.
There is no cancel endpoint (the enum has `cancelled` but nothing writes
it), no way to extend a plan before renewal, no history of past
subscriptions, and no mechanism that ever marks a lapsed row as ended —
`GET /api/subscriptions/me` reports an `active` row with a `renewal_date` in
the past indefinitely. Payments (issue #111) now create real subscriptions,
so the missing lifecycle is already user-visible.

## Goals

- Let a member cancel immediately, losing paid access at once, with the
  action being repeat-safe (second cancel is a 409, not a second write).
- Let a member purchase the same plan while it is active, producing one
  scheduled extension that carries the current renewal date forward by one
  month instead of starting a competing active row.
- Keep at most one subscription row in effect per member at all times.
- Surface the member's full subscription history (all rows, newest first,
  paginated).
- Automatically move rows past their renewal date to a terminal/promoted
  state once a day, so reads stop reporting stale `active` rows.

## Non-goals

- Payment or refund handling — checkout, IPN fulfilment, and the ledger are
  untouched beyond re-asserting the purchase gate (see `docs/feats/subscription-purchase.md`).
- Un-cancel / resume of a cancelled subscription (a new purchase after
  cancel is a fresh subscription).
- Proration, downgrade, or switching to a different plan while one is
  active — those return 409 by design.
- Automatic renewal charging. The sweep only changes status; it never
  creates a payment.
- Aligning the AI quota calendar window with `renewal_date` (deliberately
  out of scope per BR-SUBS-001).
- Admin/ops tooling for subscription rows.

## Requirements

### Functional Requirements

- [ ] FR-001: A new Flyway migration (next free version at implementation
      time — V27 as of this spec; re-verify before writing, see Risks)
      drops the `UNIQUE(user_id)` constraint on `ai_subscription`
      (`ai_subscription_user_id_key`), adds nullable `extended_from_id
      UUID REFERENCES ai_subscription(id)` and `cancelled_at TIMESTAMPTZ`,
      widens the status check constraint to
      `('active','cancelled','past_due','scheduled','expired')`, and adds
      an index on `(status, renewal_date)`.
- [ ] FR-002: The `AiSubscription` entity drops the unique/`@UniqueConstraint`
      declaration on `userId`, gains `extendedFromId` and `cancelledAt`
      fields, and the `Status` enum gains `scheduled` and `expired`
      (stored as strings — no PostgreSQL enum type change needed).
- [ ] FR-003: `POST /api/subscriptions/me/cancel` (JWT) sets the member's
      row in effect to `cancelled` with `cancelled_at = now` and, in the
      same transaction, every `scheduled` row of that member to `cancelled`
      with the same `cancelled_at`. Returns 200.
- [ ] FR-004: Cancelling when no row is in effect (FREE member, or a second
      cancel) returns 409 with message `Subscription already cancelled` and
      writes nothing.
- [ ] FR-005: Cancellation takes effect immediately: the next
      `GET /api/subscriptions/me` reads FREE (BR-SUBS-003 as amended by
      BR-SUBS-005). There is no un-cancel endpoint; a later purchase starts
      a new subscription.
- [ ] FR-006: A single shared purchase gate decides eligibility from the
      member's row in effect: (1) none in effect → allowed (new purchase);
      (2) row in effect, same plan, no `scheduled` successor → allowed
      (extension); (3) row in effect, different plan → 409
      `Cancel your current subscription before purchasing a different plan`;
      (4) row in effect, same plan, `scheduled` successor exists → 409
      `A renewal is already scheduled for this plan`. Branches 3 and 4 are
      the two faces of one rule: an in-effect row permits exactly one
      pending extension of its own plan and nothing else.
- [ ] FR-007: `POST /api/subscriptions/me/purchase/eligibility` (JWT,
      body `{"planCode": "PRO"}`) validates the plan first (unknown → 404
      `Plan not found`; inactive → 400 `Plan is not active`; price ≤ 0 or
      non-VND → 400 per BR-PAY-009), then runs the FR-006 gate and returns
      200 `{allowed: true}` or the 409. It is read-only: no row is created,
      updated, or locked for write.
- [ ] FR-008: IPN fulfilment (`activatePlan`) re-runs the FR-006 gate
      inside its transaction before writing. On violation it writes no
      subscription row and logs WARN with userId, planId, and reason —
      the payment stays succeeded (BR-PAY-001). On branch 1 it inserts an
      `active` row with `started_at = paidAt` and `renewal_date = paidAt +
      1 month` (UTC, per BR-PAY-008). On branch 2 it inserts a `scheduled`
      row with `extended_from_id` = current row id, `started_at` =
      current row's `renewal_date`, `renewal_date` = current
      `renewal_date + 1 month`, leaving the current row untouched.
- [ ] FR-009: `GET /api/subscriptions/me/history?page&size` (JWT) returns
      the member's `ai_subscription` rows ordered by `createdAt` descending
      inside a `PageResponse`; `page` defaults to 0 (≥ 0), `size` defaults
      to 20 (1–100), an out-of-range page returns 200 with empty content.
      Each item exposes `planCode`, `planName`, `status`, `startedAt`,
      `renewalDate`, `cancelledAt`, `createdAt` — never internal ids
      (`id`, `extendedFromId`, `userId`).
- [ ] FR-010: A daily scheduled job (`SubscriptionExpirySweepJob`, cron
      `0 0 0 * * *` UTC, gated on `app.scheduling.enabled`, thin job →
      service, following `PendingFilterSweepJob`) processes every row in
      effect with `renewal_date <= now`: if a `scheduled` successor exists
      for that member, the old row becomes `expired` and the successor
      becomes `active` with its dates unchanged; otherwise the old row
      becomes `expired`. The run is query-based and therefore idempotent
      and self-healing after downtime.
- [ ] FR-011: Reads treat a row as in effect iff its status is `active` or
      `past_due`; `cancelled`, `expired`, and `scheduled` are not in
      effect. A member with no row in effect (no rows at all, only
      terminal rows, or an orphan `scheduled` row) reads as FREE via the
      synthesized fallback — `GET /api/subscriptions/me` still returns 200,
      never 404.
- [ ] FR-012: An `active` row whose `renewal_date` has passed but which the
      sweep has not processed yet still counts as in effect (reads stay
      `active` for at most ~24 hours after lapse — accepted by BR-SUBS-006).

### Non-Functional Requirements

- [ ] NFR-SEC-001: All three endpoints require a JWT; the target member is
      always `@AuthenticationPrincipal UUID userId` — no member id is ever
      accepted from the client, and responses expose no internal row ids
      (no IDOR beyond what `get-me.md` already establishes).
- [ ] NFR-MAINT-001: The purchase gate exists in exactly one place; the
      eligibility endpoint and `activatePlan` call the same method — no
      duplicated branch logic to drift.
- [ ] NFR-SCALE-001: The sweep and history queries are index-backed
      (`(status, renewal_date)` index from FR-001; history paginated in
      the database); a sweep run touches only rows actually due, so
      runtime does not grow with total subscription count.
- [ ] NFR-MAINT-002: Concurrent gate/cancel/sweep writes on one member's
      rows take a pessimistic lock (pattern: `findByIdForUpdate` in
      `PaymentLedgerRepository`) so the at-most-one-in-effect invariant
      holds application-side — see Risks for why the schema does not
      enforce it.

## Design overview

Four touch points inside the existing layered architecture, no new
packages:

- **Migration V27** (see FR-001) frees `user_id` for multiple rows per
  member — the precondition for every other requirement — and adds the
  sweep/history index.
- **`SubscriptionService`** gains `cancelMySubscription`,
  `checkPurchaseEligibility` (the shared gate, also called by
  `PaymentCheckoutService.activatePlan`), and `getMySubscriptionHistory`;
  `getMySubscription` switches from "first row" to "row in effect, else
  FREE". The gate runs under a pessimistic write lock on the member's rows;
  the cancel cascade is one transaction.
- **`SubscriptionController`** gains the three JWT endpoints
  (`POST /me/cancel`, `GET /me/history`, `POST /me/purchase/eligibility`),
  each thin, returning `ApiResponse` (409s via the existing
  `DuplicateResourceException` → `GlobalExceptionHandler`, unchanged).
- **`SubscriptionExpirySweepJob`** in `scheduled/` delegates to a
  `SubscriptionService` sweep method; configuration property
  `app.scheduling.enabled` already exists for the other jobs.

Checkout and the IPN endpoint keep their routes, contracts, and ledger
behaviour; only the fulfilment write becomes branch-dependent (FR-008),
which amends FR-011 of `docs/feats/subscription-purchase.md`.

State machine per member (at most one row in effect at any instant):

```
(none) --purchase--> active --cancel--> cancelled
                      |  ^                  (terminal)
                      |  +--sweep promotes scheduled--+
                      +--same-plan purchase--> scheduled --sweep--> active
                      +--sweep (no successor)--> expired   (terminal)
```

## Success metrics

- Within 2 weeks of launch, 100% of rows with status `active` and
  `renewal_date < now - 25h` are `expired` (or their successor is
  `active`), verified by a daily count query in the sweep logs — anything
  non-zero beyond 25h is a bug.
- Zero members observed with more than one row in effect simultaneously
  (query: `COUNT(*) > 1` per user grouped by status in
  `('active','past_due')`), checked daily for the first month.
- Cancel is exercisable end-to-end by QA within the release: cancel →
  `/me` reads FREE → purchase again succeeds (regression suite green).

## Acceptance criteria

**As a** subscriber, **I want to** cancel or extend my AI plan and see my
subscription history, **so that** I control what I pay for and can verify
what I have paid for.

- [ ] Given an active subscription, when I call `POST
      /api/subscriptions/me/cancel`, then it returns 200, the active row
      and every `scheduled` successor become `cancelled` with
      `cancelled_at` set, no row remains in effect, and a repeat call
      returns 409 `Subscription already cancelled` without writing.
- [ ] Given no row in effect, when I purchase a plan after payment is
      verified, then a new `active` row is created with
      `started_at = paidAt` and `renewal_date = paidAt + 1 month`.
- [ ] Given an active PRO subscription with no successor, when payment for
      PRO is verified, then a new `scheduled` row is created with
      `extended_from_id` pointing at the current row, `renewal_date =
      current renewal_date + 1 month`, and the current row is unchanged.
- [ ] Given an active PRO subscription with a `scheduled` successor, when
      the gate runs (eligibility endpoint or IPN), then it returns 409
      `A renewal is already scheduled for this plan` and writes nothing.
- [ ] Given an active PRO subscription, when eligibility for a different
      plan (e.g. FREE) is checked, then it returns 409
      `Cancel your current subscription before purchasing a different plan`.
- [ ] Given a member with several subscription rows, when I call `GET
      /api/subscriptions/me/history`, then rows come back newest-first in
      `PageResponse` with all lifecycle fields and no internal ids.
- [ ] Given an active row whose `renewal_date` has passed and a `scheduled`
      successor, when the daily sweep runs, then the old row becomes
      `expired`, the successor becomes `active`, and their dates are
      unchanged; with no successor the old row alone becomes `expired`.
- [ ] Given a cancelled member, when I call `GET /api/subscriptions/me`,
      then it returns 200 with the synthesized FREE plan, never 404.

## Risks / open questions

- **Migration number**: this spec assumes V27 (latest is V26). A sibling
  feature branch may claim V27 first; re-verify the next free version when
  writing the migration, and keep the `out-of-order` behaviour of
  BR-MEDIA-006 in mind if two branches land in parallel.
- **Invariant is application-side only**: dropping `UNIQUE(user_id)`
  without a partial unique index on `status IN ('active','past_due')`
  means the schema will accept two in-effect rows. The issue explicitly
  scoped the migration to its four changes, so the spec does not invent a
  partial index; correctness rests on the shared gate plus the
  pessimistic lock (NFR-MAINT-002). Flagged for reviewer agreement.
- **IPN gate violation**: payment succeeded but the subscription write is
  skipped (WARN only). The eligibility endpoint exists to make this
  unreachable in practice; if it ever fires, ops must reconcile the
  payment manually — refund path is out of scope.
- **Eligibility body uses `planCode`, not `planId`**: the confirmed
  direction originally sketched `planId`, but `AvailablePlanResponse`
  exposes no ids and checkout already keys on `planCode` — deviation,
  called out here for sign-off.
- **≤24h expiry lag** (FR-012) is accepted by the issue's daily-cron
  design; any consumer needing exact-time gating would be reading
  `renewal_date` itself.

---

## Related

- Issue: #112
- Feature spec (amended by this one): `docs/feats/subscription-purchase.md`
- Business rules: `docs/brs/subscriptions.md` (BR-SUBS-003 amended;
  BR-SUBS-004/005/006 added)
- API refs: `docs/apis/subscriptions/post-cancel.md`,
  `docs/apis/subscriptions/get-history.md`,
  `docs/apis/subscriptions/post-purchase-eligibility.md`
