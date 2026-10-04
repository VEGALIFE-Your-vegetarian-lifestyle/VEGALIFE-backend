# ADR-008: Integrate VNPay Through a Hand-Rolled Thin Client, Fulfilling Only From Its IPN Webhook

## Status
Accepted

## Date
2026-10-04

## Deciders
zuyzz (issue #16), Vegalife backend team

## Context

Issue #16 adds the first paid path in the system: a member on FREE buys PRO
through VNPay. Two things about that integration constrain everything else
that follows.

**The client.** We need to build a signed payment URL and verify a webhook
signature. That is
HMAC-SHA512 over a sorted, URL-encoded parameter string plus a fixed
parameter list — roughly two hundred lines. The usual reason to reach for an
SDK is that the protocol is large or fiddly; this slice of it is not. On the
other side of the trade-off, a checkout dependency would be third-party code
handling merchant credentials and signatures, and Maven Central has no
official VNPay library — what exists there is community-maintained. This
project already has a hard rule about adding dependencies: `docs/arch/dependencies.md`
is an approved/not-approved matrix, and an unvetted payment SDK does not
belong in it.

**The truth.** A payment webhook is an unauthenticated, externally
triggered write that VNPay retries ten times in five minutes, and the
browser redirect that follows payment is entirely under the payer's control.
Something has to be the authoritative answer to "did this money arrive",
and whatever it is must be verifiable without trusting the caller, safe to
retry, and safe to race. The two candidate channels — the redirect and the
webhook — differ precisely on those axes.

## Decision

Integrate VNPay with a **hand-rolled thin client** confined to
`infrastructure/payment/vnpay/` (config, signer, client, gateway
implementation behind a gateway-neutral `PaymentGateway` seam), and treat
**the IPN webhook as the sole source of truth for fulfilment** — the
redirect lands on a frontend display-only page and no backend return
endpoint exists.

## Considered options

**Client choice**

- **Option A — Hand-rolled thin client (chosen)** — ~200 LOC of HMAC plus a
  fixed parameter list, no new dependency, full control over what leaves the
  process, testable against VNPay's real staging from our own tests.
- **Option B — Community Maven SDK** — less code to write, but a new
  third-party dependency in the payment path, unmaintained-by-us, and it
  must pass the dependency matrix; most such libraries only implement part
  of the protocol anyway.
- **Option C — VNPay's official library distribution** — not published to
  Maven Central; would mean vendoring a jar from the merchant portal, which
  is worse to audit and update than Option A.

**Fulfilment source**

- **Option A — IPN webhook only (chosen)** — verifiable with the shared
  secret, survives the user closing the tab, idempotent by design once we
  ack correctly.
- **Option B — Fulfil on the redirect / a backend return endpoint** — the
  parameters are payer-controlled, so anyone could upgrade an account for
  free; also fails when the tab never returns.
- **Option C — QueryDR polling as the primary path** — authoritative but
  turns a push protocol into a polling job, adds latency, and needs a
  scheduler and reconciliation window we are not building this sprint.

## Consequences

**Positive:**
- No new dependency in the payment path; `docs/arch/dependencies.md` is
  unchanged.
- Every VNPay-specific name, endpoint, and response code sits in one
  package, so a second gateway is a new `PaymentGateway` implementation
  rather than a refactor (NFR-MAINT-001/002).
- Fulfilment cannot be forged by a crafted redirect, and the outcome
  survives a closed tab (BR-PAY-001).
- Our own sandbox test exercises the real signing code against
  VNPay staging instead of against a mock of it.

**Negative / trade-offs:**
- We own the protocol correctness: a VNPay-side parameter or hashing change
  is our bug to find, with no upstream library to upgrade to. Mitigated by
  keeping the wire format in one class and by the real-call sandbox test.
- The secure-hash secret is now handled by our own code rather than a
  library's, so a leak is our exposure. Mitigated by NFR-SEC-001
  (environment-only, never logged, never returned).
- Two public surfaces must be reachable: the checkout path (authenticated)
  and the IPN path (`permitAll` on an exact path), which is an
  intentionally unauthenticated write endpoint — its safety rests entirely
  on checksum, reference, and amount validation (BR-PAY-002/003).
- No reconciliation job ships: if VNPay exhausts its retries without ever
  notifying us, a taken payment stays `pending` until a human or a future
  sweeper resolves it. Acknowledged as a gap in the feature spec.

## Verification

- The sandbox integration tests make real HTTPS calls to
  `sandbox.vnpayment.vn` and fail (not skip) when
  `VNPAY_TMN_CODE`/`VNPAY_SECURE_HASH_SECRET` are present but wrong — this
  is the standing check that our signing still matches theirs.
- Self-signed IPN tests prove that a bad checksum, unknown reference, or
  amount mismatch leaves the database byte-identical (BR-PAY-002/003/004).
- Revisit if a second gateway is ever needed (then `PaymentGateway` gets a
  second implementation), if VNPay publishes an official SDK, or if a
  missing-notification incident occurs — any of which reopens the relevant
  option above.
