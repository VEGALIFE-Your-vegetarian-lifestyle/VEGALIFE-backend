# ADR-010: Enforce Non-Overlapping Scheduled Menus with a GiST Exclusion Constraint

## Status

Accepted

## Date

2026-10-10

## Deciders

zuyzz (backend), project owner.

## Context

A member has exactly one weekly meal plan in effect at any time, so two
`scheduled` menus for the same user must never cover the same date
(BR-MENU-003). The `menu` table stores an inclusive `[start_date, end_date]`
range per row. The constraint has to hold for every writer — the API today and
the create/update/status issues (#17/#18/#19) that follow — and must not rely
on application code catching every concurrent insert.

`EXCLUDE USING gist` with a range operator requires the range bound type
(`uuid` here, for `user_id`) to be GiST-indexable, which needs the
`btree_gist` extension on PostgreSQL.

## Decision

Add a partial GiST exclusion constraint on `menu` that forbids overlapping
`[start_date, end_date]` ranges per `user_id` for rows whose `status` is
`scheduled`, and enable `btree_gist` to support it:

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE menu ADD CONSTRAINT uq_menu_scheduled_no_overlap
  EXCLUDE USING gist (
    user_id WITH =,
    daterange(start_date, end_date, '[]') WITH &&
  ) WHERE (status = 'scheduled');
```

Non-`scheduled` rows (`drafted`, `cancelled`, `completed`) are unaffected, so
nothing constrains plan history or in-progress drafts.

## Considered options

- **Option A — GiST exclusion constraint (chosen)** — enforced by the
  database for every writer, correct under concurrency, and free at read time.
- **Option B — Application-level `@Transactional` intersection check** — no
  extension needed, but races between concurrent inserts of two overlapping
  scheduled rows unless serialized, and it depends on every future writer
  remembering to check.
- **Option C — No constraint, decide overlap at read time** — cheapest now,
  but it pushes ambiguity into "what's planned this week?" queries and lets
  contradictory data accumulate.

## Consequences

**Positive:**

- The invariant holds regardless of the code path that writes a scheduled
  menu, including concurrent inserts.
- Read queries stay simple — they never have to disambiguate overlapping
  scheduled plans.

**Negative / trade-offs:**

- Requires the `btree_gist` extension on the target database. On PostgreSQL 16
  this is available in the standard `contrib` set; the migration validates on
  the Testcontainers Postgres image. Production availability (Render Postgres)
  is an open item to confirm before deploy.
- A violating insert surfaces as a `DataIntegrityViolationException`; the write
  endpoints must map it to a clear 409 (there is no Spring Data domain
  exception for exclusion constraints), which is the responsibility of
  #17/#18/#19.
- The constraint is partial (`WHERE status = 'scheduled'`), so a status
  transition into `scheduled` can now fail at commit time if it would overlap;
  the status endpoint must validate before transitioning.

## Verification

- The migration applies cleanly on the Testcontainers Postgres image in the
  integration profile, and a persistence test proves overlapping scheduled
  rows are rejected while drafts and completed rows are allowed.
- If `btree_gist` cannot be created on the production database, fall back to
  Option B (transactional intersection check + 409) and record the fallback
  here.
