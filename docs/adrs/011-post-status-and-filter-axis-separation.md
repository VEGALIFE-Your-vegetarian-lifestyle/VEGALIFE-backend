# ADR-011: Keep Post Visibility in `status` and the Filter Verdict in `flag` Only

## Status

Accepted (supersedes the `status = flagged` part of ADR-007)

## Date

2026-10-10

## Deciders

Project owner, backend implementation.

## Context

ADR-007 introduced post content filtering and, in its decision 5, added a
new `post.status` value `flagged`: when a filter run returned `REJECTED`
or `NEEDS_REVIEW`, the post moved to status `flagged` so it would leave
the public feed. ADR-007's own model already separated two axes —
`post.status` as the lifecycle/visibility field and `post.flag` as the
filter state — but this one value put a filter-specific concern onto the
`status` axis anyway.

That duplication now causes concrete problems:

- **Two places encode the same fact.** A rejected post is both `status =
  flagged` and `flag = REJECTED`; any rule that asks "is this post
  rejected?" has to know which axis to read, and the two can drift.
- **`status` no longer means one thing.** Downstream code tests
  `status == flagged` to mean both "the filter did not pass" and "this
  post is not publicly visible" (`PostService.applyPublishState`,
  `shouldEnqueueFilter`), conflating visibility with verdict.
- **Admin moderation (issue #5) wants the verdict, not a status.**
  Reviewing "posts that failed filtering" is a question about `flag`; the
  admin post list could not filter on it because the verdict was partly
  mirrored into `status`.
- **`status` grew a value the lifecycle never needed.** The lifecycle
  states are draft (`created`), live (`published`), and admin-withheld
  (`hidden`); a rejected post is simply not published.

`unpublished` already exists in the `post.status` enum and CHECK but no
code wrote it — it is a ready, correctly-named replacement for the
visibility role `flagged` was playing.

## Decision

**Represent the filter verdict only in `post.flag`; represent
visibility only in `post.status`. Retire `status = 'flagged'`.**

Concretely:

1. **Remove `flagged` from `post.status`.** The allowed values become
   `created`, `processed`, `published`, `unpublished`, `hidden` (migration
   `V31`). `post.flag` is unchanged: `PENDING` / `PASSED` / `REJECTED` /
   `NEEDS_REVIEW`, or NULL for "never filtered".
2. **A non-`PASSED` verdict sets `status = 'unpublished'`.** Both writers
   do this: `ContentFilterOutboundAdapter` on `REJECTED` / `NEEDS_REVIEW`,
   and `PendingFilterSweepJob` on a stale `PENDING`. The verdict itself
   still lives in `flag`.
3. **`status` remains the sole public-visibility gate.** The feed and the
   single-post read continue to select `status = 'published'`; nothing
   else changed about what is publicly visible.
4. **`PostService` reads the new state.** `applyPublishState` withdraws an
   `unpublished` post to `created` on `publish: false` (formerly the
   `flagged` case), and `shouldEnqueueFilter` re-queues a content change
   on a `published` or `unpublished` post.
5. **The admin post list filters by both axes.** `GET /api/admin/posts`
   gains a `flag` query filter (exact verdict) alongside its existing
   `status` filter, and the `status` filter drops the retired `flagged`
   value. This is the moderation surface issue #5 needs.

## Considered options

- **Option A — keep `flagged` on `status` (status quo).** No migration,
  but keeps the verdict duplicated across two axes and keeps `status`
  overloaded; blocks the admin verdict filter. Rejected.
- **Option B — drop `flagged`, map non-passed verdicts to `unpublished`
  (chosen).** One axis per concern; reuses an already-declared status
  value; public visibility is unchanged; unblocks admin filtering by
  `flag`. Requires a data migration for existing `flagged` rows.
- **Option C — drop the `status`-based gate entirely and gate visibility
  on `flag` (feed excludes `flag != PASSED`).** Makes status purely
  descriptive, but moves the public-feed rule onto the filter axis and
  reworks feed/read queries and BR-POST-010. Larger and riskier for no
  requirement that justifies it. Rejected.
- **Option D — map non-passed verdicts to `created`.** Avoids the
  "unpublished" word, but collapses a rejected post into the same status
  as a never-submitted draft, losing the distinction the moderation flow
  cares about. Rejected.
- **Option E — keep `flagged` but stop using it, leaving it unused.**
  Leaves a misleading value in the schema and enum and still requires the
  migration to clean data. Rejected.

## Consequences

**Positive:**

- One axis per concern: `status` = who can see it, `flag` = filter/
  moderation verdict. Rules read exactly one field.
- Admin moderation can filter by the verdict directly (`flag`), which the
  open "Moderate posts API" issue (issue #5) requires.
- Public visibility is provably unchanged: the feed still keys on
  `status = 'published'`.

**Negative / trade-offs:**

- **A one-time data migration is required.** `V31` rewrites existing
  `status = 'flagged'` rows to `unpublished` before shrinking the CHECK;
  it cannot be reverted in place once applied (the forward migration
  already rewrote the data).
- **`unpublished` now means "was published or was submitted, now not
  visible".** No code path distinguishes a withdrawn post from a
  filter-rejected one by `status` alone — the discriminator is `flag`
  (NULL/`PASSED` vs `REJECTED`/`NEEDS_REVIEW`). This is the intended
  reading, but any future report on "why is this not public" must look at
  `flag`, not `status`.
- **`processed` stays unused.** It remains in the enum/CHECK as a legacy
  value; this ADR does not remove it.

## Verification

- `V31` applies cleanly on a fresh database and on a database that had
  `status = 'flagged'` rows; the `post.status` CHECK no longer accepts
  `flagged`.
- A rejected or needs-review filter run, and the stale-pending sweep,
  leave the post at `status = 'unpublished'` with the matching `flag`,
  and out of the public feed.
- `GET /api/admin/posts?flag=REJECTED` returns the filter-rejected posts;
  `?status=unpublished` returns every non-public withdrawn/rejected post.
- Revisit if more than two non-public lifecycle meanings are ever needed
  on `status`, or if a report requirement emerges for which the
  `flag`-based discriminator is insufficient.
