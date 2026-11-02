# ADR-020: End the Outbound Retry on a Permanent Embedding Rejection, and Honour `Retry-After`

## Status

Accepted

## Date

2026-11-02

## Deciders

Project owner (direction, this session: "write the ADR, then fix"; 429 policy
"wait it out, but honour `Retry-After`"), backend implementation.

Supersedes decision 9 of [ADR-014](014-embedding-provider-gemini.md) in the
narrow sense that a *permanent* rejection no longer travels the generic
`FAILED@24h` path. ADR-014's intent — an embedding failure never fails the
member's publish — is unchanged.

## Context

On 2026-11-02 the outbound drainer logged two failures in the same pass:

```
Outbound message cf54a9db-… delivery failed on attempt 3/4, retrying in PT2M:
Gemini embedding call failed for model gemini-embedding-001

Outbound message 2f95b0dd-… failed after 116 attempts, deferred until 14:35Z:
Gemini embedding call failed for model gemini-embedding-001
```

Neither line names a cause, and one message had reached **116 attempts** —
roughly ten hours at the `[10s, 30s, 2m]` then 5-minute deferred cadence. Two
defects produced that, and a third question could not be answered at all.

**The failure could not be attributed.** `GeminiEmbeddingModel` caught every
`RuntimeException` and rethrew one flat `EmbeddingClientException` holding only
a message. The upstream status and Google's error body survived solely inside
the *cause*, which the drainer never logged. A 429, a 400-invalid-key, a 403, a
404 and a read timeout were byte-identical in the log. This violated FR-013 of
[the embedding provider migration](../feats/embedding-provider-migration.md)
("logged at ERROR with the upstream status"), which had been specified and never
implemented.

**A permanent rejection was retried like a transient one.** The drainer treated
every `RuntimeException` from an adapter as retryable, so a rejection that no
retry could ever satisfy consumed the fast ladder and then deferred every five
minutes until `app.outbound.max-age` (24h). Only then did
`PendingFilterSweepJob` move the post to `NEEDS_REVIEW`.

**The retry loop could sustain its own throttle.** `processDue` claims up to
`batch-size: 10` rows per 5-second poll and delivers them back to back, and
`scheduleRetry` discarded any `Retry-After` the provider supplied. A backed-up
queue therefore re-issued calls faster than a free-tier quota could recover, so
each retry re-armed the limit it was waiting on.

Three recorded positions disagreed about what should happen.

- `ContentFilterOutboundAdapter`'s javadoc: failures propagate so the queue
  retries, "permanent failure is the existing FAILED@24h handling".
- ADR-014 decision 9: an embedding failure queues the post for review
  immediately, and *the review queue must record why* so an outage is
  distinguishable from real moderation work.
- ADR-016 decision 16: the component scorer (recipe text) already catches the
  failure and queues for review — the opposite of the post-body path in the same
  pipeline.

Two live probes narrowed the cause. Long input is **not** rejected:
`gemini-embedding-001` returned `200` at 20,000 characters and at 40,000
characters with a byte-identical vector, silently truncating to its 2,048-token
limit. `gemini-embedding-2` is also not involved: it returns `200` with the
`task_type` field this codebase always sends. The observed provider shapes are
invalid key → **400** `INVALID_ARGUMENT` / `API_KEY_INVALID`, missing key →
**403** `PERMISSION_DENIED`, unknown model → **404** `NOT_FOUND`.

That last row is what makes classification a decision rather than a
transcription: the credential failure arrives as a **400**, so "4xx means the
request was bad" would blame the post for an expired key.

## Decision

**1. Every provider failure is classified at the client boundary.** The
exception carries a machine-readable reason, the upstream HTTP status when there
was one, and the provider's retry hint when it gave one. The log message names
the reason and status; the goal is not a pretty string but the ability to
distinguish "wait" from "act" from one line. Classification reads Google's
`error.status` / `details[].reason` in addition to the HTTP code, because a bad
credential and bad content are both `400`.

**2. A permanent rejection ends the message immediately.** `AUTH`,
`INVALID_REQUEST` and `NOT_FOUND` are terminal: no retry, no fast ladder, no
24-hour wait. `TIMEOUT`, `UPSTREAM_ERROR` (5xx), `RATE_LIMITED` and an
unclassifiable `UNKNOWN` are retryable — treating an unreadable failure as
permanent would silently drop work.

**3. A retryable failure is rescheduled at or after the provider's `Retry-After`,
never before it.** The drainer takes the maximum of the configured delay and the
provider's hint, so a recovery hint of 40 seconds is not undercut by a 10-second
retry. This is the fix for the self-inflicted throttle.

**4. A 429 is waited out — until waiting is pointless.** If `now + Retry-After`
would land beyond `createdAt + max-age`, the message is terminal now rather than
sleeping to a deadline it cannot meet. If the provider gives no hint, normal
backoff applies and the retry proceeds; "unknown delay" is not "permanent". The
owner explicitly chose to preserve daily-quota recovery rather than treating all
429s as review-worthy.

**5. Terminal delivery for the content filter means review with a recorded
reason.** The post moves to `flag=NEEDS_REVIEW`, `status=unpublished`, and a
`moderation_log` row records `action=FILTER_UNAVAILABLE` with the classified
cause in `reason` and a NULL `actor_id` (the system, not a human). `moderation_log`
already provides `actor_id`, `reason` and both indexes, so no schema change is
needed. The message then completes instead of being marked `FAILED`, because its
business purpose — "get this post judged" — was discharged by the review queue.

**6. The queue learns about consequences without importing them.** The drainer
does not know what a `CONTENT_FILTER` row means. A channel adapter may implement
an optional hook invoked when its message is abandoned; the default is a no-op.
This keeps the channel → consequence mapping in the adapter that owns the domain,
and leaves `EMAIL` and `MEDIA_PURGE` unaffected. Permanence is **declared** by the
adapter, never inferred by the drainer: an undeclared failure keeps its retries,
because an SMTP or provider outage is indistinguishable from a bad message at that
boundary and silently dropping work is the worse mistake.

**7. The filter paths become consistent.** The post-body path adopts the
behaviour the component scorer already had (ADR-016 D16), rather than the reverse.

## Considered options

**Keep the 24h `FAILED` path, fix only the logging and `Retry-After`.** Smallest
change and no schema or policy question. Rejected: a permanently rejected post
still spends a day invisible while the member believes it published, and ADR-014
D9's "record why" requirement stays unimplemented. It also leaves the two filter
paths disagreeing.

**Treat every 429 as terminal.** Simple and makes a quota outage evident
immediately. Rejected by the owner: free-tier quota recovers daily, and sending
a post to human review because a *transient* limit was hit manufactures exactly
the outage-shaped review queue D9 warns against.

**Retry forever and let `max-age` be the only stop.** Simplest to reason about,
and what the code does today. Rejected: it is the 116-attempt behaviour, it keeps
hammering a provider that has already refused, and the eventual outcome is still
review — reached a day late and with no reason recorded.

**Retry with a global circuit breaker on the provider.** Would stop the
self-throttle without per-message classification, and is attractive for
multi-instance deployments. Not adopted now: it needs shared state the codebase
does not have (the queue is durable and multi-worker; an in-process breaker would
be per-instance), and it does not satisfy the attribution requirement. Worth
revisiting if queue volume grows.

## Consequences

**Positive:**

- One log line now answers "rate limited or broken?" — the question that started
  this.
- A rejected post reaches the admin queue in seconds instead of 24 hours, with a
  reason that distinguishes "the filter could not run" from "the filter judged
  this".
- The queue stops undercutting its own quota, so a transient outage drains once
  the provider recovers instead of rolling forward indefinitely.
- The two filter paths behave the same way under provider failure.

**Negative / trade-offs:**

- More code at the boundary: a classifier plus a place to hold the reason. That is
  the cost of diagnosability.
- A provider that changes its error body shape degrades to status-code
  classification — still correct for 403/404, still wrong for a 400 credential
  failure. The classifier therefore treats an unrecognised 400 as
  `INVALID_REQUEST` (terminal) but names the reason code it saw, so a misread is
  visible in the log rather than silent.
- The review queue now receives posts that were never read by a model. An admin
  cannot tell from `post.flag` alone; they need the `moderation_log` row. This
  satisfies D9 by recording, not by widening the flag enum — which matters
  because ADR-011 keeps the verdict axis narrow.
- An operator can no longer distinguish "filtered and reviewed" from "failed and
  queued" by counting `FAILED` queue rows; that count drops.
- Free-tier daily-quota exhaustion still holds a post for up to 24 hours if the
  hint fits inside the window. Accepted deliberately over inventing review work.

## Verification

- Unit tests pin one distinct message per provider shape (429, 400-invalid-key,
  403, 404, timeout), and assert `Retry-After` survives into the reschedule.
- A drainer test asserts a declared permanent failure produces no retry and invokes
  the abandonment hook, that a retry never precedes the provider's hint, and that
  an undeclared failure still retries (the EMAIL and MEDIA_PURGE contract).
- An adapter test asserts the post lands `NEEDS_REVIEW` / `unpublished` with a
  `moderation_log` row naming the cause, and that the message completes.
- Live shapes recorded 2026-11-02: invalid key `400 API_KEY_INVALID`, no key
  `403 PERMISSION_DENIED`, unknown model `404 NOT_FOUND`, over-limit input `200`
  (truncated), `gemini-embedding-2` with `task_type` `200`.

Revisit if: the provider starts returning 429 for credential problems, the
review queue becomes majority "filter unavailable" (an outage would then be
disguised as work, which is the failure D9 exists to prevent), or a multi-instance
deployment needs a shared breaker.

## References

- Bug: [#175](https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/175)
- [ADR-014: Serve Filter Embeddings From Gemini](014-embedding-provider-gemini.md)
- [ADR-016: Moderate Components Without Per-Occurrence Verdict Storage](016-component-level-moderation.md)
- [ADR-011: Keep Post Visibility in `status` and the Filter Verdict in `flag` Only](011-post-status-and-filter-axis-separation.md)
- [ADR-005: Persistent Outbound Message Queue](005-persistent-outbound-message-queue.md)
- [Feature: Embedding Provider Migration](../feats/embedding-provider-migration.md)
