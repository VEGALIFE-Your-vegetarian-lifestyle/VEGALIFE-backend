# Feature Spec: AI Subscription APIs (Own Subscription + Available Plans)

## Status

In progress

## Author / owner

zuyzz (issue #15), written by backend agent; owns the `/api/subscriptions`
response contract.

## Summary

Adds two read-only endpoints: an authenticated
`GET /api/subscriptions/me` that returns the caller's AI tier, quota usage,
current plan, and latest successful payment, and a public
`GET /api/subscriptions` that lists every active plan with its limits and
feature flags. Plan and feature data are stored in new database tables and
seeded by migration, never hardcoded in Java.

## Problem / motivation

Issue #15 introduces the AI subscription domain as a greenfield feature: the
repository has no `ai_subscription`, `ai_plan`, or payment table, and no
subscription Java code. The frontend (and any future AI feature — chat,
weekly meal plan, video summarization) has no way to answer two basic
questions: "what plan am I on and how much quota is left?" and "what plans
exist and what does each one include?". Without a source of truth in the
database, quota numbers would have to be hardcoded per client, which cannot
survive a price or limit change.

## Goals

- Let an authenticated member see their current AI tier, quota usage for the
  running month, renewal date, and latest successful payment in one call.
- Let any caller (including logged-out visitors) see the full plan comparison
  — limits, price, and per-feature flags — from a single public endpoint.
- Keep every limit, price, and feature flag in the database so a plan change
  is a data change, not a code change.

## Non-goals

- Upgrading, downgrading, cancelling, or pausing a subscription.
- A payment-history list endpoint — only the single latest successful payment
  is returned (a future issue adds the full history).
- Any payment provider integration, checkout, webhook, or refund flow. The
  `payment_ledger` table is created read-only here; nothing writes to it yet.
- Enforcing the quota on AI endpoints — no AI chat/meal-plan/summarization
  endpoint exists in the codebase yet, so nothing consumes the limit today.
- Admin CRUD for plans or features (seed data only in this issue).
- Trials, coupons, proration, invoices, or multi-currency display.

## Requirements

### Functional Requirements

- [ ] FR-001: `GET /api/subscriptions/me` with a valid JWT returns `200 OK`
      with `data` shaped `{tier, status, renewalDate, usage{used, limit,
      periodStart, periodEnd}, currentPlan{code, name, monthlyRequestLimit,
      price{amount, currency}}, latestPayment}`.
- [ ] FR-002: A request to `/api/subscriptions/me` without a valid, non-expired
      JWT returns `401 Unauthorized`.
- [ ] FR-003: When the caller has no `ai_subscription` row, the endpoint
      synthesizes the FREE default — `tier` and `currentPlan` from the
      `ai_plan` row with `code = 'FREE'`, `status = 'active'`,
      `renewalDate = null`, `latestPayment = null` — and writes no row.
- [ ] FR-004: `usage` covers the current UTC calendar month
      (`periodStart` = first instant of the month, `periodEnd` = first instant
      of the next month). `usage.used` is the sum of `ai_usage.request_count`
      for rows of this user whose `[period_start, period_end)` overlaps that
      window; `usage.limit` is `ai_plan.monthly_request_limit` of the caller's
      effective plan. No matching rows means `used = 0`, never `null`.
- [ ] FR-005: `latestPayment` is the caller's most recent `payment_ledger` row
      with `status = 'succeeded'`, newest `paid_at` first, mapped to
      `{planCode, amount, currency, status, provider, paidAt}`. It is `null`
      when no such row exists. `provider_reference` and internal ids are never
      returned.
- [ ] FR-006: `GET /api/subscriptions` returns `200 OK` without any
      credentials, with `data` as an array of every `active` plan ordered by
      `sort_order`, each shaped `{code, name, monthlyRequestLimit,
      price{amount, currency}, features[{key, enabled, description}]}`, where
      `features` pivots `ai_plan_feature` rows of that plan ordered by
      `feature_key`.
- [ ] FR-007: Security rules are exact-path: `GET /api/subscriptions` is
      `permitAll`, while `GET /api/subscriptions/me` remains authenticated —
      a wildcard `/api/subscriptions/**` is not used.
- [ ] FR-008: Neither endpoint performs writes: no `ai_subscription`,
      `ai_usage`, `ai_plan`, or `payment_ledger` row is created or updated by
      a read.
- [ ] FR-009: Both responses use the standard envelope
      (`success`, `message`, `data`) and are never `404` when data is absent
      (no subscription row → FREE default; no ledger rows →
      `latestPayment: null`).

### Non-Functional Requirements

- [ ] NFR-SEC-001: `/me` leaks only subscription facts — no email, no
      password/token material, no `provider_reference`, no ledger or
      subscription primary keys.
- [ ] NFR-SEC-002: A missing, expired, or invalid token on `/me` returns the
      existing `401` body `{"success":false,"message":"Unauthorized"}` from
      the `SecurityConfig` entry point, with no stack trace.
- [ ] NFR-MAINT-001: Limits, prices, and feature flags are read exclusively
      from `ai_plan` / `ai_plan_feature`; no plan constant appears in Java
      source or test fixtures as a source of truth.
- [ ] NFR-SCALE-001: `/me` issues a fixed number of queries (≤ 4) regardless
      of table size; the usage total is one aggregate query over an indexed
      (`idx_ai_usage_user_id`) predicate, not an in-memory scan.
- [ ] NFR-MAINT-002: DTOs are mapped with MapStruct and wrapped with the
      existing `ApiResponse` helper, matching `GET /api/profile` and
      `GET /api/categories` conventions.

## Design overview

Package-by-layer with a new `subscription` domain sub-package:

- Migration `V25__create_ai_subscription_tables.sql` creates `ai_plan`,
  `ai_plan_feature`, `ai_subscription`, and `payment_ledger`, then seeds two
  plans (FREE 20 req/month at 0 VND, PRO 500 req/month at a placeholder
  49,000 VND) and three feature keys (`ai_chat`, `weekly_meal_plan`,
  `video_summary`) with per-plan `enabled` flags.
- Entities `model/subscription/{AiPlan, AiPlanFeature, AiSubscription,
  PaymentLedger}`; `AiUsage` entity maps the existing `ai_usage` table from
  migration `V10` (it has no Java mapping today).
- Repositories `repository/subscription/` — plan lookup by code and by active
  order, `findByUserId` on subscriptions, latest-succeeded ledger query, and a
  summed-overlap aggregate on `ai_usage`.
- `service/subscription/SubscriptionService` with
  `getMySubscription(UUID userId)` and `getAvailablePlans()`, both
  `@Transactional(readOnly = true)`.
- `controller/subscription/SubscriptionController` — one `@GetMapping` on
  `/api/subscriptions/me` taking `@AuthenticationPrincipal UUID userId`, one
  on `/api/subscriptions`.
- `SecurityConfig` gains
  `.requestMatchers(HttpMethod.GET, "/api/subscriptions").permitAll()`
  before `.anyRequest().authenticated()`; the `/me` path is a different path
  and therefore still falls through to authentication.
- DTOs `dto/response/subscription/` — `SubscriptionMeResponse`,
  `SubscriptionUsageResponse`, `PlanSummaryResponse`, `PlanPriceResponse`,
  `PaymentResponse` (shared with a future payment-history endpoint),
  `AvailablePlanResponse`, `PlanFeatureResponse`.

Two behaviors worth stating as contracts for later issues: the quota
recorder that will feed `ai_usage` must write non-overlapping periods
(monthly rows are expected) so the overlap sum cannot double-count, and the
future payment flow writes `payment_ledger` rows so `latestPayment` stops
being `null`.

## Success metrics

- Both endpoints verified green in this change: `GET /api/subscriptions`
  answers `200` with 2 plans and no token; `GET /api/subscriptions/me`
  answers `200` with a valid token and `401` without one.
- A plan change requires only a migration/seed update — grep for a plan
  limit constant in `src/main/java` returns nothing (NFR-MAINT-001).

## Acceptance criteria

**As a** member using Vegalife's AI features, **I want to** see my current
plan, remaining monthly quota, renewal date, and latest payment at a glance,
**so that** I know when I am about to hit the free limit and whether I am on
the paid plan.

- [ ] Given an authenticated member, when they call
      `GET /api/subscriptions/me`, then the API returns `200 OK` with their
      tier, status, renewal date, usage (`used`, `limit`, `periodStart`,
      `periodEnd`), current plan summary, and latest payment.
- [ ] Given a member with no `ai_subscription` row, when they call `/me`,
      then the response is the FREE default synthesized from the seeded
      `ai_plan` row, `renewalDate` is `null`, and no database row is created.
- [ ] Given the caller has `ai_usage` rows overlapping the current month,
      when they call `/me`, then `usage.used` equals the sum of
      `request_count` and `usage.limit` equals the plan's
      `monthly_request_limit`; with no rows, `used` is `0`.
- [ ] Given the caller has no succeeded `payment_ledger` row, when they call
      `/me`, then `latestPayment` is `null`; given several, then the one with
      the newest `paid_at` wins.
- [ ] Given no credentials at all, when a caller calls
      `GET /api/subscriptions`, then the API returns `200 OK` with every
      active plan ordered by `sort_order`, each with its feature list.
- [ ] Given a request without a valid JWT, when `GET /api/subscriptions/me`
      is called, then the API returns `401 Unauthorized`; and calling
      `GET /api/subscriptions` with the same request still returns `200`.
- [ ] Neither endpoint writes to the database.

## Risks / open questions

- PRO's `price_amount` of 49,000 VND is a placeholder seed; product must
  confirm the real price (data-only fix either way).
- Nothing writes `ai_usage` yet, so `usage.used` will read `0` until the AI
  request-recording feature lands. The overlap-sum contract above is what
  that feature must satisfy.
- Nothing writes `payment_ledger` or `ai_subscription` yet, so
  `latestPayment` is `null` and every user reads as FREE until a
  payment/upgrade flow ships.
- The quota window is the UTC calendar month, which will drift from
  `renewalDate` for subscribers mid-month. Aligning usage to the billing
  period is a follow-up decision, not part of #15.
- `/api/subscriptions` is not cached; with two seeded rows this is
  irrelevant, but a future plan catalogue would want one.
