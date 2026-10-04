# Feature Spec: Payment Status API

## Status

In progress

## Author / owner

Backend (issue #106, sub-issue of #16); drives the frontend payment result page.

## Summary

Let an authenticated member read the ledger state of one of their own payments by
our payment id, so the frontend result page can poll for the outcome after VNPay
redirects the browser back.

## Problem / motivation

After VNPay redirects the browser to the frontend result page, the frontend has no
way to ask the backend whether the payment succeeded:

- The redirect's query parameters are display-only and attacker-controlled — they
  cannot be trusted as proof of payment (BR-PAY-001).
- `GET /api/subscriptions/me` reflects the member's tier and the latest *succeeded*
  payment for the plan they are on now; it says nothing about the state of a
  specific `pending` row, so a purchase that is still awaiting the IPN is invisible
  there.
- The IPN webhook is asynchronous (BR-PAY-001, ADR-008), so immediately after the
  redirect the ledger row is routinely still `pending`. Without a status endpoint
  the result page can only guess.

## Goals

- Expose `GET /api/payments/{paymentId}` returning the ledger state of a payment
  the caller owns, so the result page can poll every ~2–3 s until `status` leaves
  `pending` or a ~30 s timeout.
- Return a stable, self-describing payload (`paymentId`, `status`, `plan`, `amount`,
  `currency`, `createdAt`, `paidAt`) that needs no redirect parameters to render.
- Keep the read strictly passive: a ledger read with nothing else on the wire.

## Non-goals

- No QueryDR or any VNPay server-to-server call — ADR-008 makes the IPN webhook the
  sole source of truth for fulfilment.
- No parsing or trusting of the redirect's query parameters.
- No unauthenticated access, and no payment-history list endpoint —
  `GET /api/subscriptions/me` stays the tier source.
- No changes to IPN handling or fulfilment.
- Frontend polling/timeout behaviour and its display rules are out of scope here
  (tracked on the frontend side).

## Requirements

### Functional Requirements

- [ ] FR-001: An authenticated member calling `GET /api/payments/{paymentId}` for a
  payment they own receives `200` with
  `{paymentId, status, plan, amount, currency, createdAt, paidAt}` read from
  `payment_ledger`.
- [ ] FR-002: `plan` is the full plan object `{code, name, monthlyRequestLimit,
  price:{amount, currency}}` — the same shape as `PlanSummaryResponse` — resolved
  through the ledger's plan reference, not a bare plan-code string.
- [ ] FR-003: A payment id that belongs to a different user returns `404`; the
  response must not reveal whether the id exists.
- [ ] FR-004: An unknown payment id returns `404`.
- [ ] FR-005: Calling the endpoint without authentication returns `401`.
- [ ] FR-006: While a payment is `pending`, the endpoint reports `status = "pending"`
  and performs no gateway call — a `payment_ledger` read only.
- [ ] FR-007: After the IPN webhook fulfils a payment, the endpoint reports the
  ledger's final `status` and a non-null `paidAt`.
- [ ] FR-008: The response is wrapped in the standard `ApiResponse` envelope
  (`success`, `message`, `data`).

### Non-Functional Requirements

- [ ] NFR-SEC-001: Ownership is enforced in the data-access predicate (the row is
  fetched by id **and** caller), so another user's id can never be read, enumerated,
  or distinguished from a non-existent one.
- [ ] NFR-SEC-002: The endpoint never writes to `payment_ledger`, `ai_subscription`,
  or any other table — polling is read-only and cannot double-fulfil (BR-PAY-004).
- [ ] NFR-MAINT-001: The plan projection reuses `SubscriptionMapper.toPlanSummary`
  so `plan` cannot drift from `GET /api/subscriptions/me`'s `currentPlan`.
- [ ] NFR-PERF-001: One primary-key lookup plus one plan lookup per poll; no gateway,
  no queue, no external call — safe to poll every 2–3 s.

## Design overview

Adds one read-only path alongside the existing payment endpoints:

- `PaymentController` gains `GET /api/payments/{paymentId}`; the path already falls
  under the default `authenticated()` rule in `SecurityConfig` (only
  `/api/payments/vnpay/ipn` is `permitAll`), so no security change is needed.
- A read-only service method loads the ledger row with an ownership-scoped query
  (`id` + `userId`), 404s when absent, resolves `plan_id` → `AiPlan`, and maps both
  through `SubscriptionMapper` into a new `PaymentStatusResponse`.
- No new repository or table; `PaymentLedgerRepository` gains one derived finder.
- No new business rule: read-only ownership-scoped access is this endpoint's own
  contract, while the underlying constraints are already BR-PAY-001 (IPN is the sole
  source of truth) and ADR-008 (no QueryDR).

## Success metrics

- The frontend result page renders a definitive outcome (or a clean timeout) for
  every payment without reading any redirect query parameter.
- No QueryDR / server-to-server gateway calls originate from this path (verified by
  the absence of gateway interaction in the endpoint's tests and by code review).

## Acceptance criteria

**As a** member who just returned from VNPay, **I want to** ask the backend for the
state of the payment I just started, **so that** the result page shows an accurate
outcome instead of trusting attacker-controllable redirect parameters.

- [ ] Given an authenticated member, when they call
  `GET /api/payments/{paymentId}` for a payment they own, then the response is
  `200` with `{paymentId, status, plan, amount, currency, createdAt, paidAt}` read
  from `payment_ledger`, where `plan` is the full plan object
  `{code, name, monthlyRequestLimit, price}` resolved from the ledger's plan
  reference (same shape as `PlanSummaryResponse`).
- [ ] Given a payment id that belongs to another user, when an authenticated member
  requests it, then the response is `404` (no enumeration of other users' payments).
- [ ] Given an unknown payment id, when an authenticated member requests it, then
  the response is `404`.
- [ ] Given no authentication, when the endpoint is called, then the response is
  `401`.
- [ ] Given a payment still `pending`, when the endpoint is polled, then `status` is
  `pending` and no gateway call is made (ledger read only — never QueryDR,
  ADR-008).
- [ ] Given a payment fulfilled by IPN, when the endpoint is polled afterwards, then
  `status` is the ledger's final status and `paidAt` is set.

## Risks / open questions

- A ledger row whose `ai_plan` row has been deleted cannot produce a `plan` object.
  Decision: treat it as `404` (the same defensive posture as other missing-resource
  reads) rather than failing with `500`. Plans are seeded and not hard-deleted, so
  this should not occur in practice.
- Polling frequency is the frontend's choice; the backend imposes no rate limit
  beyond what the deployment already applies. If polling ever becomes a load
  concern, that is a separate change.
- Refunded payments are reported as `refunded` with their original `paidAt`; no
  extra refund-specific fields are exposed.
