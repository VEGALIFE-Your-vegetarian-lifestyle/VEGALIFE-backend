# Feature Spec: Payment History API (Member + Admin)

## Status

Approved

## Author / owner

Backend (issue #111, sub-issue of #16); drives the member billing-history
page and admin payment support.

## Summary

Let an authenticated member page through their own payment ledger, newest
first, with each row carrying the subscription it produced and the plan it
was for — plus a parallel admin endpoint that lists payments across all
users with filter support.

## Problem / motivation

Today the only payment views are one row at a time:

- `GET /api/subscriptions/me` returns the member's *current* tier and their
  latest succeeded payment — nothing about older payments.
- `GET /api/payments/{paymentId}` reads a single ledger row by id, which
  requires already knowing the id (checkout's response or the VNPay
  redirect). There is no way to *browse* what you have paid.
- Admins have no payment visibility at all: dispute and support handling
  requires direct database access.
- The ledger does not record which subscription a payment produced
  (`payment_ledger` has `user_id` + `plan_id` only), so even from the
  database, "which subscription did this payment create or extend" can only
  be guessed by correlating user, plan, and timestamps.

## Goals

- Members can page through their full payment history, newest first, and
  interpret each row without follow-up API calls (subscription and plan
  context inline).
- Admins can page through all payments, filtered by user, status, and date
  range, for support and dispute handling.
- A successful payment records the subscription id it produced, so history
  rows link cleanly to the subscription lifecycle
  (`docs/feats/subscription-lifecycle.md`).

## Non-goals

- No refund, chargeback, or any money movement — this feature reads the
  ledger; only the existing IPN webhook writes to it.
- No CSV/PDF export, no realtime push, no sorting options beyond the fixed
  `createdAt` descending order.
- No changes to checkout flow, IPN verification, or QueryDR behaviour
  (ADR-008) beyond recording the subscription id at fulfilment time.
- No changes to `GET /api/payments/{paymentId}` or
  `GET /api/subscriptions/me`.
- No member-side filters (status/date) — members get pagination only in
  this phase; admin gets the full filter set.

## Requirements

### Functional Requirements

- [ ] FR-001: Flyway migration `V28` adds
  `subscription_id UUID NULL REFERENCES ai_subscription(id) ON DELETE SET
  NULL` to `payment_ledger`, plus an index on `(user_id, created_at DESC)`
  backing the member list query. Existing ledger rows keep `NULL` (no
  backfill — historical payments cannot be reliably correlated).
- [ ] FR-002: When the IPN webhook fulfils a payment that activates or
  extends a plan, the ledger row records the subscription id returned by
  `SubscriptionService.activatePlan` (the returned `Optional` is no longer
  discarded). Rows that never activate a subscription — `pending`,
  `failed`, `refunded` — leave `subscription_id` `NULL`.
- [ ] FR-003: `GET /api/payments` (JWT Bearer) returns a page of the
  caller's own ledger rows ordered `createdAt` descending. Each item is
  `{id, amount, currency, status, provider, txnRef, paidAt, createdAt,
  subscription, plan}`; `subscription` is
  `{id, status, startedAt, renewalDate}` or `null` when the payment
  produced no subscription; `plan` is the full plan object
  (`PlanSummaryResponse`: `{code, name, monthlyRequestLimit, price}`),
  `null` only if the plan row has been removed.
- [ ] FR-004: `GET /api/admin/payments` (JWT Bearer, `ROLE_ADMIN`)
  returns the same item shape across all users, with an additional
  `userId` field on every row.
- [ ] FR-005: The admin endpoint accepts optional filters — `userId`
  (UUID), `status` (ledger status string), `createdFrom` / `createdTo`
  (ISO-8601 instants, both inclusive) — combined with AND, matching the
  filter style of `GET /api/admin/users`.
- [ ] FR-006: Both endpoints accept `page` (≥ 0, default 0) and `size`
  (1–100, default 20), validated the same way as `UserListRequest`. A
  page index beyond the last returns `200` with an empty `content` list —
  not an error.
- [ ] FR-007: Calling either endpoint without a token returns `401`;
  calling the admin endpoint authenticated as a non-admin returns `403`.
- [ ] FR-008: Both responses use the standard `ApiResponse` envelope with
  `PageResponse` as `data` (`content`, `page`, `size`, `totalElements`,
  `totalPages`, `first`, `last`) — the uniform pagination contract of all
  24 list endpoints in this codebase.
- [ ] FR-009: Rendering a page performs the ledger page query plus at
  most two batch loads (distinct plan ids, distinct subscription ids for
  the page) — never a row-by-row lookup.

### Non-Functional Requirements

- [ ] NFR-SEC-001: The member query's predicate includes the caller's id
  (taken from `@AuthenticationPrincipal`, enforced at the data-access
  layer), so another user's rows can never be read or enumerated.
- [ ] NFR-SEC-002: The admin endpoint inherits the existing
  `/api/admin/**` → `hasRole("ADMIN")` rule in `SecurityConfig`; no
  security configuration change is needed or made.
- [ ] NFR-SEC-003: Both endpoints are read-only — they never write to
  `payment_ledger`, `ai_subscription`, or any other table (BR-PAY-004).
- [ ] NFR-MAINT-001: One item DTO (`PaymentHistoryItemResponse`) serves
  both endpoints; the plan projection reuses
  `SubscriptionMapper.toPlanSummary` so `plan` cannot drift from
  `GET /api/payments/{paymentId}`'s shape.
- [ ] NFR-MAINT-002: Filtering uses a `PaymentLedgerSpecifications`
  class following the existing `UserSpecifications` /
  `PostSpecifications` convention; no bespoke query strings.
- [ ] NFR-PERF-001: Per page: exactly one page query plus at most two
  `IN` batch loads — query count is constant regardless of page size.

## Design overview

- **Migration**: `V28` (V27 is taken by subscription lifecycle) adds the
  nullable `subscription_id` column with `ON DELETE SET NULL` and the
  `(user_id, created_at DESC)` index.
- **Entity**: `PaymentLedger` gains a `subscriptionId` field.
- **Webhook**: `PaymentWebhookService.fulfil` captures the
  `Optional<AiSubscription>` returned by `activatePlan` and persists the
  id in the same save that already writes the ledger row.
- **Read path**: a new read-only `PaymentHistoryService`
  (`service/payment/`) exposes `listMyPayments(userId, request)` and
  `listPayments(adminRequest)`. `PaymentLedgerRepository` gains
  `findByUserId(...)` and `extends JpaSpecificationExecutor<PaymentLedger>`;
  predicate assembly lives in a new `PaymentLedgerSpecifications`
  (`repository/subscription/`) mirroring `UserSpecifications`.
- **Controllers**: `PaymentController` gains `GET /api/payments` (the
  exact path matches ahead of `GET /api/payments/{paymentId}`; the
  default `authenticated()` rule already covers it). A new
  `AdminPaymentController` (`controller/admin/`) exposes
  `GET /api/admin/payments`; 403 comes from the existing security rule.
- **DTOs**: `PaymentListRequest` (`dto/request/payment/`: `page`,
  `size`), `AdminPaymentListRequest` (`dto/request/admin/`: plus
  `userId`, `status`, `createdFrom`, `createdTo`), and
  `PaymentHistoryItemResponse` (`dto/response/payment/`). `userId` on the
  item carries a field-level `@JsonInclude(NON_NULL)` so member responses
  omit it while admin responses always include it — one DTO, both AC
  shapes, without a subclass hierarchy (composition over inheritance).
- **Hydration**: after the page loads, collect distinct `planId`s and
  `subscriptionId`s, batch-fetch each in one query, and map. Subscriptions
  are not user-scoped further at this point: they were loaded from rows
  the predicate already scoped.

## Success metrics

- Support/dispute handling of a payment no longer requires database
  access: an admin can locate any payment by user, status, or date range
  through the endpoint.
- Every rendered page is self-sufficient — the frontend resolves plan and
  subscription names from the list response alone, with zero follow-up
  calls per row.
- Query count per page stays ≤ 3 regardless of page size (verified by
  tests and code review).

## Acceptance criteria

**As a** member, **I want to** page through everything I have paid with
the subscription and plan each payment produced or covered, **so that** I
can review my billing history without knowing payment ids in advance —
and **as an** admin, **I want to** find any payment by user, status, or
date, **so that** I can handle support and disputes without database
access.

- [ ] Given an authenticated member, when they call `GET /api/payments`,
  then the response is `200` with their own rows only, newest first,
  each row containing ledger fields (`id`, `amount`, `currency`,
  `status`, `provider`, `txnRef`, `paidAt`, `createdAt`), a
  `subscription` object (or `null`), and the full `plan` object.
- [ ] Given a ledger row that never produced a subscription (or one
  written before this feature), when it appears in a page, then
  `subscription` is `null` and the rest of the row still renders.
- [ ] Given a successful IPN fulfilment that activates or extends a plan,
  when the webhook saves the ledger row, then `subscription_id` on that
  row equals the id of the subscription `activatePlan` returned.
- [ ] Given `pending`, `failed`, or `refunded` rows, when they are
  listed, then their `subscription_id` is `NULL`.
- [ ] Given an authenticated admin, when they call
  `GET /api/admin/payments` with `userId`, `status`, `createdFrom`,
  and/or `createdTo`, then the result contains only rows matching every
  supplied filter, across all users, and every row carries `userId`.
- [ ] Given a valid filter combination that matches no rows (or a `page`
  index beyond the last), when the endpoint is called, then the response
  is `200` with an empty `data.content` list (see Risks: the issue's
  wording says `items`, the codebase's envelope says `content`).
- [ ] Given no authentication, when either endpoint is called, then the
  response is `401`.
- [ ] Given an authenticated non-admin, when they call the admin
  endpoint, then the response is `403`.
- [ ] Unit tests cover member scoping, admin filters, pagination
  boundaries, `subscription` null-vs-populated mapping, and webhook
  subscription recording; the `401`/`403` contract — enforced by
  `SecurityConfig`, not by service code — is covered by an integration
  test (see Risks: the issue asks for 403 in unit tests).

## Risks / open questions

- The issue's AC says an empty result returns "an empty `items` list",
  but every list endpoint in this codebase returns
  `ApiResponse<PageResponse<T>>` with `data.content`. Decision: use
  `data.content`; flagged here and in the PR description so the issue
  author can confirm.
- The issue's AC says unit tests cover `403`, but 403 is enforced by
  Spring Security's `SecurityConfig` (`/api/admin/**` →
  `hasRole("ADMIN")`), which unit tests of the service cannot reach.
  Decision: cover `401`/`403` with an integration test and state the
  deviation in this spec and the PR.
- The issue notes say "migration V28", but V27 has since been taken by
  subscription lifecycle — implemented as V28 regardless; numbering
  confirmed at implementation time.
- `ON DELETE SET NULL` means a hard-deleted `ai_subscription` (not
  expected: subscriptions are archived by status, not deleted) would
  degrade affected history rows to `subscription: null` rather than
  breaking the join. Accepted.
- Field-level `@JsonInclude(NON_NULL)` for `userId` is a localized
  convention this codebase has not used before. It was chosen over a
  second DTO (rejected: duplication) or a member/admin subclass
  (rejected: inheritance where composition is the house rule) to keep
  one item shape serving both endpoints; reviewers may flag it.
