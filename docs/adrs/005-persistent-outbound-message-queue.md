# ADR-005: Use a Persistent Outbound Message Queue for Email Delivery

## Status

Accepted

## Date

2026-09-26

## Deciders

Project owner (design confirmed 2026-09-26 through question rounds on
issue #49), backend implementation.

## Context

Email sending is currently inline: `AuthService` calls `EmailService`
inside its `@Transactional` methods — registration (`AuthService:158`),
forgot-password (`AuthService:261`), and resend (`AuthService:140`).
`SmtpEmailServiceImpl` talks to the SMTP server synchronously with
5-second connection/write timeouts. If SMTP is slow or down, the
exception propagates out of the business method, the transaction rolls
back, and the user gets a 500 — no account created (for registration),
no OTP queued for delivery, and no retry. Failures are also invisible:
there is no record of what was attempted, what failed, or how often.

Issue #49 asks for resilience: registration must succeed despite email
problems, delivery must be retried with increasing delays, undelivered
messages must survive for later delivery, and success/failure/retry
counts must be observable.

Constraints shaping the options:

- No new infrastructure — no message broker (RabbitMQ, Kafka, Redis
  Streams) is in the approved dependency matrix
  (`docs/arch/dependencies.md`).
- PostgreSQL is the only datastore; the project already runs scheduled
  background jobs (`OtpCleanupJob`, `TokenCleanupJob`) via Spring's
  `@Scheduled`.
- The existing `EmailService` port and its two call flows must keep
  working; existing email tests must continue to pass (issue #49 AC).
- OTP verification codes are stored as SHA-256 hashes (BR-AUTH-019);
  anything the queue persists is a new plaintext exposure surface.

## Decision

Replace inline SMTP sends with a **persistent outbound message queue
(a transactional outbox)** in PostgreSQL: business methods write an
`outbound_message` row in the same transaction in which they create the
OTP (via a new `@Primary` `OutboxEmailService`), a scheduled background
drainer claims due rows with `FOR UPDATE SKIP LOCKED` and dispatches
them through a channel-adapter interface (the existing
`SmtpEmailServiceImpl` becomes the EMAIL channel adapter), retrying
fast (10s → 30s → 2m, three retries) and then deferring the message to
a 5-minute retry cadence (`DEFERRED`) until it succeeds, expires
(OTP `expires_at`) or exceeds the 24-hour maximum age.

The system provides at-least-once outbound delivery and prevents
normal concurrent duplicate processing, but cannot guarantee
exactly-once delivery across an external SMTP provider. Retries reuse
the same outbound message/OTP, so duplicate deliveries remain
functionally equivalent.

Deduplication is deliberately a business-layer concern, not a queue
concern: resending an OTP invalidates prior codes (BR-AUTH-021), so a
stale queued email for a superseded OTP is harmless — the code it
carries is already unusable. The queue carries no `dedup_key` and
performs no supersession logic.

## Considered options

- **Option A — keep the inline send, swallow SMTP exceptions** —
  registration would survive, but the user silently never receives the
  email and there is no retry or record of the loss.
- **Option B — retry inside the request (Spring Retry /
  Resilience4j)** — retries still happen on the request thread (worst
  case N × SMTP timeout on the user's call), and an exhausted retry
  still loses the message.
- **Option C — PostgreSQL outbox table + scheduled drainer** — durable
  in the same transaction as the business write, retries and deferred
  delivery are state in the table, no new infrastructure, matches the
  existing `@Scheduled` job precedent. **(Chosen.)**
- **Option D — external broker (RabbitMQ / Redis Streams)** — better
  push semantics and throughput, but adds infrastructure and a
  dependency outside the approved matrix for a volume this system does
  not have.

## Consequences

**Positive:**

- Registration, forgot-password, and resend never fail because SMTP is
  slow or down — the business transaction commits an outbox row
  instead (issue #49 AC).
- Delivery survives restarts: pending rows are re-claimed after any
  crash; retry state (`attempts`, `next_attempt_at`) lives in the
  table, not in memory.
- Failures are observable: per-event WARN/ERROR logs plus a periodic
  summary counter of sent / failed / retried (log-based metrics — no
  actuator, per project decision).
- The table is generic (`channel`, JSON `payload`), so a future
  in-app or SMS notification channel reuses the same queue.

**Negative / trade-offs:**

- **At-least-once only.** A crash between a successful SMTP send and
  the `COMPLETED` commit re-sends the email after the visibility
  timeout; there is no way to make the external SMTP hop exactly-once
  (see the statement above).
- Delivery is no longer synchronous: the user receives the email up to
  one poll interval (~5s) later instead of before the HTTP response.
- The queue payload holds the **plaintext OTP** (verification or
  password-reset) until it reaches a terminal state — while `otp_code`
  keeps only hashes. Mitigated by clearing `payload` on every terminal
  transition and by the 7-day retention purge; still a broader
  plaintext window than today.
- On resend, a stale queued email for a superseded OTP can still be
  delivered (the code inside is already invalidated by
  BR-AUTH-021) — accepted; the alternative is business logic inside
  the queue, which this ADR rejects.
- One more table to migrate, index, monitor, and purge; the drainer
  polls PostgreSQL every 5s even when the queue is empty (indexed
  `status`/`next_attempt_at` probe — negligible at this scale).

## Verification

- Issue #49's acceptance criteria are the primary check: registration
  succeeds with SMTP down, three increasing-delay retries then
  deferred delivery, background drain, log-based sent/failed/retried
  counts, existing email tests green.
- Revisit if queue depth (rows in `PENDING`/`DEFERRED` older than
  15 minutes) grows persistently, if duplicate-send incidents from the
  crash window become user-visible, or if payload plaintext exposure
  is deemed unacceptable — in which case move OTP material out of the
  payload (e.g. reference-by-id with a one-shot read) before adding
  more channels.
