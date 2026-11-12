# ADR-023: Model Publication as `processing`, and Treat `flag` as the Blocking Reason

## Status

Accepted (supersedes ADR-011 decisions 2, 4 and 5; ADR-011's "one axis per
concern" principle is kept and strengthened)

## Date

2026-11-13

## Deciders

Project owner, backend implementation.

## Context

ADR-011 separated *visibility* (`status`) from the *filter verdict* (`flag`)
but left the two axes describing overlapping facts, and every writer had to
maintain both by hand. Five migrations later (`V19`, `V31`, `V36`, `V38`,
`V51`) the result is a state machine that cannot be derived from its own
columns:

- **The two axes are not independent.** `status` could not be computed from
  `flag`, and `flag` could not be computed from `status`: `published` +
  `REJECTED`, `created` + `PASSED` and `published` + `PENDING` are all
  reachable states, each meaning something different depending on which
  writer produced it.
- **`flag` carries two different ideas.** `PENDING` describes *work in
  flight*; `PASSED`/`REJECTED`/`NEEDS_REVIEW`/`DUPLICATE` describe *a
  verdict*. The first is a lifecycle fact, the second is a reason.
- **`processed` is dead.** It exists in the enum, the CHECK and the admin
  filter regex, but no code path writes it.
- **`hidden` is a second "not visible" state** with no rule of its own, and
  unhide returns a post to `created` — a draft the author never withdrew.
- **Three defects follow from the blend:**
  - `PendingFilterSweepJob` rewrote `flag`/`status` without checking whether
    the post had since been withdrawn, so a stale queue row could destroy a
    newer state.
  - `ContentFilterOutboundAdapter` applied a verdict to any post with a
    matching id, republishing a post the author had withdrawn or deleted.
  - A `hidden` post with `publish: true` was handled by two separate guards
    that could disagree.

## Decision

**`status` answers "where is this post in its life"; `flag` answers "why is
it not published". A published post therefore has no flag.**

### 1. Status vocabulary

`drafted`, `processing`, `published`, `unpublished`.

- `drafted` — no publish intent, or withdrawn by the author.
- `processing` — submitted; the verdict is outstanding, or a blocker is
  recorded in `flag`.
- `published` — the filter passed, or an admin approved it. Publicly visible.
- `unpublished` — an admin rejected it. Not visible; the only terminal
  refusal.

`created`, `processed` and `hidden` are retired. There is no admin hide and no
author hide: withdrawing (`publish: false`) is the author's way to unlist, and
it yields `drafted`.

### 2. `flag` is the blocking reason, and only exists while blocked

Stored values: `FILTER_FAILED`, `SIMILAR_CONTENT`, `FILTER_SKIPPED`.

- `FILTER_FAILED` — the filter refused the content, or was unsure (the two
  former bands collapse into one; the distinction was never actionable).
- `SIMILAR_CONTENT` — a near-duplicate of a published post.
- `FILTER_SKIPPED` — the filter could not run: the platform filter was
  disabled, the embedding provider gave a permanent rejection, the outbound
  message was abandoned, or the stale-pending sweep settled it. Distinct from
  a refusal so an outage never reads as a judgement.

`flag` is cleared whenever the post leaves `processing`. `PASSED` and
`PENDING` disappear: passing publishes, and being in flight is `processing`
with a NULL flag.

### 3. The invariant is a database constraint

```sql
CHECK (flag IS NULL OR status = 'processing')
```

The axes are now provably disjoint rather than kept apart by convention. A
published post cannot carry a verdict, a drafted post cannot carry a blocker,
and no writer can create the cross-axis pairs that motivated this ADR.

### 4. Everything that needs to be reviewed lives at `processing`

A filter verdict never unpublishes by itself. `FILTER_FAILED`,
`SIMILAR_CONTENT` and `FILTER_SKIPPED` all leave the post at `processing`,
where only an admin can resolve it: approve -> `published`, reject ->
`unpublished`. The admin review queue is therefore `status=processing`, not a
flag value.

### 5. A re-check takes the post out of view for its duration

Any re-check (content change, `publish: true`) moves the post to `processing`
and clears the previous blocker. A verdict is applied only when the post is
still `processing` and not deleted; otherwise it is discarded, which is what
fixes the withdraw-during-flight republish.

### 6. The duplicate target and the score are stored

- `post.flag_detail` (new, `VARCHAR(64)`) carries
  `SIMILAR_CONTENT_POST_<uuid>` for duplicates.
- `post.content_score` (ADR-016, `V38`) already carries the relevance score
  and is unchanged.

`flag` stays a closed, exactly-matchable vocabulary, so the admin
`?flag=` filter remains an equality predicate over an enum. `moderation_log`
is unchanged and keeps the audit trail.

### 7. Admin approval and rejection are the only visibility decisions

- approve: `processing`/`unpublished` -> `published`, clears `flag`,
  `flag_detail` and `rejection_reason`. A `drafted` post is refused (400) —
  nobody submitted it.
- reject: `processing` -> `unpublished`, clears `flag`/`flag_detail`, sets
  `rejection_reason`.
- hide/unhide is removed; `PATCH /api/posts/{id}/visibility` no longer exists.

### 8. Component banding is no longer expressed with `PostFlag`

`PostFlag` becomes a persisted blocker vocabulary; the filter pipeline gets
its own `FilterOutcome` (`PASS`, `FAIL`, `UNCERTAIN`). Writing
`PostFlag.PASSED` becomes a compile error instead of a runtime
`IllegalArgumentException` (the guard at `FilterVerdict:21` is deleted).

## Considered options

- **Option A — a generated `status` plus intent/hold/decision columns.**
  Fully derivable state, but it adds three columns and demotes `status` to a
  projection; rejected in favour of the smaller two-column model that still
  satisfies the invariant.
- **Option B — keep `flag` as the verdict and clear it nowhere (status quo).**
  Rejected: this is the blend the ADR exists to remove.
- **Option C — encode the duplicate target inside `flag`
  (`SIMILAR_CONTENT_POST_<uuid>`).** Rejected: `flag` is `VARCHAR(16)`, and
  making it a composite breaks the admin filter, the enum mapping and four
  readers for one field of extra data. A second column costs one `ALTER`.
- **Option D — keep `hidden` and teach unhide to restore.**
  Rejected: a fifth state that duplicates `drafted` from the author's side,
  and restoring requires evidence (`published_at` is nulled on hide) that no
  longer exists.
- **Option E — split machine-rejected from admin-rejected with a fourth
  state (`rejected`).** Rejected: `unpublished` + `rejection_reason` already
  distinguishes them, and a fourth status multiplies the transition table.

## Consequences

**Positive**

- `status` is a total function of the two columns; the cross-axis pairs
  (`published` + `REJECTED`, `created` + `PASSED`, `published` + `PENDING`)
  are now impossible, not merely unused.
- Every transition has exactly one writer, and approving no longer leaves a
  stale verdict behind.
- Withdrawing during a filter run, a lost outbound message, and a
  provider outage each land in a distinct, documented state.
- The admin queue is one predicate (`status=processing`) and the flag filter
  stays an equality match.

**Negative / trade-offs**

- **A re-check is visible to the author.** A published post edited and
  re-checked disappears from the feed until it passes. Accepted: the
  alternative lets unreviewed changes stay live with no way to tell whether
  a check is pending.
- **The refuse/unsure distinction is lost.** `FILTER_FAILED` covers both; the
  reviewer reads `content_score` plus the component detail instead. ADR-016's
  component escalations still surface as `FILTER_FAILED` on the same post.
- **A one-time, irreversible data rewrite.** `V56` maps `hidden` and
  machine-held `unpublished` rows; the mapping is recorded in the migration.
- **`created` is no longer a status value.** Frontend clients that compared
  against it must move to `drafted`; the transition table and the response
  fields are documented in `docs/apis/post/patch-posts-postid.md` and
  `docs/apis/post/post-posts.md`.

## Verification

- `V56` applies on a fresh database: every integration context runs Flyway
  from empty, so the constraints and the new columns are proven. The
  legacy-row backfill (`created`/`processed`/`hidden` and the old flag values)
  is **not** covered by an automated test — it needs a database that predates
  the migration. It was reviewed statement by statement against the value sets
  the earlier migrations (`V19`, `V31`, `V36`, `V38`, `V51`) could have left
  behind, and its pre-flight query is in the migration header so an operator
  can inspect the affected rows before applying it.
- A filter pass publishes and clears `flag`; a refusal or an outage leaves
  `processing` with the matching flag and never sets `unpublished`.
- Editing a published post moves it to `processing`; withdrawing while a run
  is in flight leaves it `drafted` and the late verdict is discarded.
- The stale-pending sweep settles to `FILTER_SKIPPED` at `processing`.
- `GET /api/admin/posts?status=processing` is the review queue;
  `?flag=SIMILAR_CONTENT` returns duplicates with `flag_detail` populated.
