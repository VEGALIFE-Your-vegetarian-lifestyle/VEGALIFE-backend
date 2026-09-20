# ADR-<number>: <Title of the decision>

<!-- Filename convention: docs/adrs/NNNN-short-title.md, zero-padded,
incrementing. The title IS the decision, not a topic label — "Use
PostgreSQL for the primary datastore," not "Database decision." -->

## Status

<!-- One of: Proposed | Accepted | Rejected | Superseded by ADR-<number> -->
Proposed

## Date

<!-- YYYY-MM-DD, the date this status last changed. -->

## Deciders

<!-- Who was involved in / approved this decision. -->

## Context

<!-- What problem forces this decision? What constraints (technical,
business, timeline) shape the options? State facts, not the decision. -->

## Decision

<!-- The decision, stated in one or two sentences, unambiguous. -->

## Considered options

<!-- List each option seriously considered, even if quickly rejected. -->

- **Option A** — <one line>
- **Option B** — <one line>

## Consequences

<!-- What becomes easier or harder as a result? Include negative
consequences honestly — an ADR that only lists upsides isn't trustworthy. -->

**Positive:**
-

**Negative / trade-offs:**
-

## Verification

<!-- How will we know this decision was right, or when to revisit it?
E.g. a metric, a timeline, a condition that would trigger reconsideration. -->

---

## Example

# ADR-0007: Use Redis for Session Storage

## Status
Accepted

## Date
2026-03-14

## Deciders
Backend team (Duy, Anh), tech lead sign-off (Minh)

## Context
The app currently stores sessions in-memory on each API instance. With
autoscaling now enabled, a user's session is lost whenever their request
lands on a different instance, causing random logouts under load. We need
a shared session store that survives instance restarts and scaling
events, and that fits our existing infra (already running Redis for
caching).

## Decision
Use the existing Redis cluster for session storage, with a 24-hour TTL
per session key, instead of introducing a new datastore.

## Considered options
- **Option A — Redis (existing cluster)** — no new infra, sub-millisecond
  reads, TTL support built in.
- **Option B — PostgreSQL sessions table** — durable, but adds write load
  to the primary DB and needs a cleanup job for expired sessions.
- **Option C — Sticky sessions (load balancer level)** — no code change,
  but breaks autoscaling assumptions and complicates failover.

## Consequences

**Positive:**
- No new infrastructure to operate.
- Session reads/writes stay fast (<5ms p99 in staging tests).
- TTL-based expiry is native to Redis, no cleanup job needed.

**Negative / trade-offs:**
- Sessions are lost if the Redis cluster is flushed or fails without
  persistence enabled — acceptable for now since sessions are not
  business-critical data, but worth revisiting if that changes.
- Adds session traffic to a cluster already used for caching; needs
  monitoring to catch contention early.

## Verification
Revisit if Redis memory usage from sessions exceeds 10% of cluster
capacity, or if session loss incidents occur more than once per quarter.
