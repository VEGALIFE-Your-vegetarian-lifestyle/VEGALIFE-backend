# Feature Spec: Persistent Outbound Message Queue (Email)

## Status

In progress

## Author / owner

Project owner (issue #49), backend implementation.

## Summary

Make all outbound emails (registration verification, forgot-password
OTP, resend verification OTP) asynchronous and resilient: business
requests commit a durable queue row instead of talking to SMTP, a
background drainer delivers with retries, and delivery outcomes are
observable — so a transient SMTP outage can never block a user action.

## Problem / motivation

`AuthService` sends email inline inside its `@Transactional` methods
(`AuthService:158`, `:261`, `:140`) through a synchronous
`SmtpEmailServiceImpl` (5s SMTP timeouts). When SMTP is slow or down
the exception rolls back the whole business transaction: the user gets
a 500, no account is created on registration, and the email is lost
with no retry and no record. Issue #49 documents this as a poor
experience that a temporary email hiccup blocks the entire
registration.

## Goals

- A user action (register / forgot-password / resend) always succeeds
  when its database write succeeds, regardless of SMTP availability.
- Undelivered emails are retried with increasing delays, then kept for
  later delivery instead of being lost.
- Sent / failed / retried counts are visible to operators.

## Non-goals

- Exactly-once delivery (see ADR-005 — at-least-once only).
- Non-email channels (SMS, in-app push): the schema is generic, but
  only the EMAIL channel ships here.
- Queue-level deduplication / supersession of OTP emails — that stays
  a business-layer concern (BR-AUTH-021 invalidates stale codes).
- A new message broker or any new runtime dependency (no actuator, no
  RabbitMQ, no Spring Retry).
- Admin UI or API for browsing the queue.

## Requirements

### Functional Requirements

- [ ] FR-001: Register, forgot-password, and resend enqueue an
      `outbound_message` row (channel EMAIL, payload
      `{username, otp, expiryMinutes}`, `expires_at` = now + 10 min)
      in the same transaction that creates the OTP, and never call
      SMTP on the request thread.
- [ ] FR-002: With SMTP unreachable, each of the three flows still
      completes its documented success response; no SMTP timeout is
      ever added to request latency.
- [ ] FR-003: A failed send is retried after 10s, then 30s, then 2m
      (initial attempt + 3 retries, per issue #49), each attempt
      incrementing `attempts` and scheduling `next_attempt_at`.
- [ ] FR-004: After the 3rd retry fails (attempt 4), the message
      becomes `DEFERRED` and is re-attempted every 5 minutes until
      delivered, expired, or too old.
- [ ] FR-005: A background drainer polls every 5s (configurable,
      batch ≤ 10), claims due rows exclusively via
      `FOR UPDATE SKIP LOCKED`, and dispatches by channel (EMAIL →
      existing `SmtpEmailServiceImpl`).
- [ ] FR-006: Before each send, `now > expires_at` marks the message
      `EXPIRED` without sending (OTP is already dead — precedence over
      retry scheduling).
- [ ] FR-007: Messages older than the 24h maximum age are marked
      `FAILED`; every terminal transition (`COMPLETED`, `FAILED`,
      `EXPIRED`) clears `payload`.
- [ ] FR-008: A `PROCESSING` row idle past the 60s visibility timeout
      is reclaimed by the reaper: `PENDING` if `attempts < 4`, else
      `DEFERRED`.
- [ ] FR-009: A message that exhausts retries and passes its deadline
      is persisted (`FAILED`/`DEFERRED`) rather than lost — queue
      state survives restarts.
- [ ] FR-010: Delivery outcomes are logged as per-event WARN/ERROR
      plus a summary counter (sent, failed, retried) at most every
      60s; payloads and OTPs never appear in logs.
- [ ] FR-011: The drainer (and all `@Scheduled` jobs) only run when
      `app.scheduling.enabled` is true.
- [ ] FR-012: Terminal rows older than 7 days are purged by a daily
      cleanup job.

### Non-Functional Requirements

- [ ] NFR-SCALE-001: Claiming is safe under multiple app instances
      (row-level lock + `SKIP LOCKED`; no double-claim of one row).
- [ ] NFR-SEC-001: OTP plaintext exists only inside `payload` and only
      until a terminal transition or retention purge; it is never
      logged.
- [ ] NFR-MAINT-001: No new runtime dependencies and no new
      infrastructure; all tunables live under `app.outbound.*`.
- [ ] NFR-PERF-001: Enqueue costs one INSERT inside the existing
      business transaction (no measurable API latency change).

## Design overview

Transactional outbox — see `docs/adrs/005-persistent-outbound-message-queue.md`
for the decision and alternatives. In short: `V15__create_outbound_message.sql`
adds the queue table; `OutboxEmailService` (`@Primary` `EmailService`
implementation) writes rows; a `@Scheduled` drainer claims rows
(`PENDING`/`DEFERRED`, `next_attempt_at <= now`) and sends them through
a channel adapter, marking `COMPLETED` or scheduling the next retry;
exponential-ish fixed backoff (10s/30s/2m → 5m deferred), OTP-expiry
precedence, visibility-timeout recovery, payload clearing, retention
purge, and log-based counters.

## Success metrics

- With SMTP stopped in a test, registration/forgot/resend success
  responses remain 100% (0 email-caused 5xx) — verified by the
  GreenMail/down-SMTP tests, checked from the first release of this
  branch.
- Backlog drains within one 5s poll (+ send time) of SMTP recovery;
  no row stays `PENDING`/`DEFERRED` past 15 minutes while SMTP is
  healthy.
- sent/failed/retried counts are readable in logs within 60 seconds of
  the corresponding event.

## Acceptance criteria

**As a** registering user, **I want** sign-up to succeed even when the
mail server is having a bad day, **so that** a transient email outage
never blocks me from creating an account.

- [ ] Given SMTP is unreachable, when a user registers, then the
      account exists, the HTTP response is 201, and a queue row
      `PENDING` exists (issue #49 AC 1).
- [ ] Given a send fails, when each configured delay elapses, then the
      message is re-attempted 3 times at 10s, 30s, and 2m (issue #49
      AC 2).
- [ ] Given 3 retries are exhausted, when the 4th attempt fails, then
      the message is persisted as `DEFERRED` for later delivery instead
      of being lost (issue #49 AC 3).
- [ ] Given due rows in the queue, when the drainer runs, then pending
      emails are delivered (verified end-to-end with GreenMail)
      (issue #49 AC 4).
- [ ] Given sends, failures, and retries have occurred, when reading
      the logs, then counts for all three are visible (issue #49 AC 5).
- [ ] Given the whole feature is in place, when the existing test
      suite runs, then all pre-existing email tests still pass
      (issue #49 AC 6).
- [ ] Given a concurrent claim scenario, when two drainers claim
      simultaneously, then each message is claimed by exactly one
      (unit/integration test with disjoint assertion on `attempts`).

## Risks / open questions

- `JSONB` payload mapping has no precedent entity in this repo; if the
  H2-based unit profile rejects `@JdbcTypeCode(SqlTypes.JSON)`, fall
  back to `TEXT` (decided fallback — do not block on it).
- At-least-once can duplicate an email on a crash between send and
  `COMPLETED` commit — accepted per ADR-005, functionally harmless
  for OTP emails.
- A stale queued OTP email may still be delivered after a resend
  superseded the code — accepted (BR-AUTH-021 makes the code dead).
