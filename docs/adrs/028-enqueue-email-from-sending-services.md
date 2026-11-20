# ADR-028: Enqueue Outbound Email From the Sending Services, Not Through a Port Implementation

## Status

Accepted

## Date

2026-11-19

## Deciders

Project owner, backend implementation.

## Context

ADR-005 introduced the persistent outbound queue and, with it, an
enqueue seam shaped as a second implementation of the `EmailService`
port:

```
EmailService
  ├─ OutboxEmailService      @Primary — writes an outbound_message row
  └─ SmtpEmailServiceImpl              — actually talks to SMTP
```

This had three consequences that only became visible once the other
channels were built:

1. **It inverted the meaning of the port.** `EmailService` read as
   "notify this user", but its two implementations did opposite jobs:
   one persisted a request, the other performed network I/O. Any caller
   could not tell which one it had.

2. **It forced a leak.** Because the queue implemented `EmailService`,
   the EMAIL channel adapter could not inject the port — resolving it
   would have handed the drainer the enqueuer and looped
   `row → adapter → new row` forever. The adapter therefore injected
   the concrete `SmtpEmailServiceImpl`, a rank-6 class, which pinned the
   adapter itself to rank 6 and left it in `infrastructure/` while its
   two siblings lived in `service/filter` and `service/media`.

3. **It duplicated a pattern that already existed.** `PostService#enqueueContentFilter`
   and `MediaService#enqueuePurge` already build their own
   `OutboundMessage` inline. Only the email channel went through an
   indirection class, and that indirection had a single caller shape:
   "serialise this payload, save the row".

## Decision

Delete the enqueue indirection. `AuthService` and `PaymentWebhookService`
build and save their own `outbound_message` rows, exactly as the
content-filter and media-purge channels do. `OutboxEmailService` is
removed.

`EmailService` survives, with its meaning narrowed to what its name
suggests: "render and send one email now". `SmtpEmailServiceImpl`
remains its only implementation.

With the queue no longer implementing the port, the EMAIL channel
adapter injects `EmailService` rather than the concrete SMTP class. That
drops its last rank-6 dependency, and the adapter moves to
`service/email` beside `EmailPayload` and `EmailService` — matching the
other two channel adapters, whose vendor work also sits behind a port or
a repository rather than a concrete infrastructure class.

`OutboundEmailPayload` moves to `service/email/EmailPayload`. It cannot
live in `infrastructure/`: `AuthService` is rank 5 and constructs it, so
a rank-6 home would be an upward dependency (ADR-018).

Payload retention is also changed — settled rows keep their payload
instead of nulling it. That is a behavioural change with its own
rationale and is recorded as an amendment to ADR-005, not here.

## Considered options

- **Keep `OutboxEmailService`** — preserves the status quo, but leaves
  the port meaning two different things, keeps the adapter's rank-6
  leak, and leaves the email channel as the only one with an enqueue
  class. Rejected.
- **Delete `EmailService` entirely and have the adapter inject
  `SmtpEmailServiceImpl`** — also removes the indirection, but keeps the
  adapter pinned at rank 6 and the asymmetry with its siblings intact.
  Rejected: the interface is the seam that makes the rank move legal.
- **Move `ContentFilterOutboundAdapter` and `MediaPurgeOutboundAdapter`
  up into `infrastructure/` to match** — makes the layout uniform in the
  other direction, but drags post and media repositories into the
  vendor-SDK layer for no benefit. Rejected.
- **Inline the enqueue in the callers and let the adapter inject the
  port** — chosen.

## Consequences

**Positive:**

- All three `OutboundChannelAdapter` implementations live beside their
  domain in `service/`, and `infrastructure/email` holds only the SMTP
  implementation and its `JavaMailSender` wiring.
- The queue has one way in: build the row. A future channel copies an
  existing one instead of adding another port implementation.
- `EmailService` has a single implementation and a single injector (the
  adapter), so there is no ambiguity and no self-reference hazard.
- `rank-check` stays at zero upward edges.

**Negative / trade-offs:**

- The enqueue block is now duplicated in three places (auth, payment,
  content filter, plus media purge). This is accepted: each caller sets
  a different channel, payload, recipient, and expiry, and the shared
  part is a four-field builder call that a shared helper would obscure
  rather than remove.
- `AuthService` and `PaymentWebhookService` gain two collaborators
  (`OutboundMessageRepository`, `ObjectMapper`) and now know the queue's
  row shape. That is the same knowledge the other two channels already
  carry; it is not new coupling, only relocated.
- `EmailService` still exists as a one-implementation interface. It is
  retained deliberately as the rank-5 seam in front of a rank-6
  implementation, not as speculative abstraction.

## Verification

- `mvnw.cmd -o test` and `mvnw.cmd -o verify -Pintegration-test` green,
  including the enqueue-shape integration test that asserts the row
  envelope for registration, forgot-password, resend, and receipts.
- `pwsh tools/rank-check.ps1` reports zero upward edges.
- Revisit if a fourth email-shaped enqueue appears in a service that has
  no other queue knowledge, or if `EmailService` grows a second
  implementation again (which would resurrect the self-reference
  hazard and force the adapter back to concrete injection).
