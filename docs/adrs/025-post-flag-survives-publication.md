# ADR-025: Keep the Post Verdict After Publication, and Name the Passing One

## Status

Accepted

## Date

2026-11-13

## Deciders

Project owner, backend implementation.

## Context

ADR-023 modelled a post as two axes: `status` (where it is in its life) and
`flag` (why it is held). To keep the axes provably disjoint it made `flag`
disappear the moment a post left `processing`, and ADR-024 inherited that rule
when it turned `flag_detail` into a JSON snapshot. Concretely:

- a filter **pass** published the post and set `flag = NULL`,
  `flag_detail = NULL`;
- an **admin approval** published the post and deleted whatever blocker was
  there;
- an **admin rejection** unpublishes the post and also deleted the blocker.

That erased the one thing a reviewer asks later: *was this post cleared by the
filter, or did a human override the filter?* `published` answers neither. The
audit trail in `moderation_log` records the human decision but not the verdict it
overrode, and no admin API exposes the log, so the question could only be
answered by a database query.

The gap is visible in the review workflow: an admin looking at two published
posts cannot tell a genuine pass from a false positive they approved by hand, so
the filter's accuracy cannot be reviewed from the product at all.

There is a second, smaller omission: a passing run had no name. `FilterOutcome`
has `PASS`, but the persisted vocabulary had no passing value, so "the filter
cleared this" was represented by the absence of data.

## Decision

**`flag` records the last verdict and survives publication. `FILTER_PASSED` is
that verdict for a passing run.**

```
published   + FILTER_PASSED       the filter cleared the post
published   + FILTER_FAILED       an admin approved a post the filter refused
published   + SIMILAR_CONTENT     an admin approved a post flagged as a duplicate
published   + FILTER_SKIPPED      an admin approved a post the filter never judged
unpublished + <any blocker>       an admin rejected a held post
```

1. **A filter `PASS` writes `FILTER_PASSED`.** It is the verdict, not a synonym
   for `published`: an admin approval keeps the blocker it found and does not
   relabel it. Relabelling would report that the filter cleared content it
   refused.
2. **Rejection keeps the blocker too.** `unpublished` + blocker +
   `rejection_reason` is the complete decision record: what the machine saw,
   next to what the human decided.
3. **Only a draft has no flag.** Withdrawing (`publish: false`) returns the post
   to `drafted` and clears `flag`/`flag_detail`: the verdict described content
   the author has taken back, and there is no queue it belongs to any more.
4. **The invariant follows from that:**
   `CHECK (flag IS NULL OR status <> 'drafted')`. The old
   `chk_post_flag_requires_processing` is dropped rather than kept under a name
   that would now be false.
5. **Re-filtering overwrites, it does not stack.** A new run moves the post to
   `processing` with no flag; the verdict that lands writes the new flag and
   snapshot over the old ones.
6. **`FILTER_PASSED` stores `{"kind":"FILTER_PASSED","score":…,"threshold":…}`.**
   No `outcome` field: the kind already states that the run passed, and repeating
   it would let the two disagree. Rounding, `kind`, and the fixed-by-constraint
   relationship to `flag` are unchanged from ADR-024.
7. **`flag`/`flagDetail` are admin-only on the API.** They are removed from the
   member-facing `PostListResponse` and remain on the admin list and detail
   responses. A published post that an admin approved over `FILTER_FAILED` must
   not announce the refusal — or, for `SIMILAR_CONTENT`, the id of the post it
   resembled — to every reader.
8. **No backfill.** Rows published before `V58` never stored the score or the
   threshold of the run that published them, so they keep `flag = NULL`,
   meaning "no verdict recorded". Fabricating `FILTER_PASSED` for them would
   invent a boundary they were never measured against.

## Considered options

- **Option A — keep clearing the flag (status quo).** Simplest, and keeps the
  axes disjoint. Rejected: it makes "filter pass vs human override"
  unrecoverable from the product, which is the question the admin review queue
  exists to support.
- **Option B — a separate `verdict` column beside `flag`.** Preserves the
  "flag = held" reading while recording history. Rejected: two columns that
  always change together, and the new one would duplicate `flag`'s vocabulary
  with a different name.
- **Option C — `FILTER_PASSED` on every published post, including admin
  approvals (chosen shape differs).** Simpler invariant (`published` always
  means `FILTER_PASSED`), but it destroys the override fact this change is meant
  to preserve.
- **Option D — widen `flag` and keep it (chosen).** One column, one vocabulary,
  one write per verdict; the status still answers visibility, and the pair
  `(status, flag)` answers "how did it get here".
- **Option E — expose the flag on the public post shape.** No mapper work, but
  it publishes moderation detail to every reader and leaks the duplicate
  target's id. Rejected.

## Consequences

**Positive**

- `published` + `FILTER_FAILED` is now a first-class, filterable state, so the
  admin queue can surface false positives and the filter can be tuned against
  real overrides.
- The final state of every submitted post is stored once, where a reviewer
  already looks, without a join to `moderation_log`.
- The passing verdict has a name, so absence of data stops ambiguously meaning
  both "never judged" and "passed".

**Negative / trade-offs**

- **The two axes are no longer disjoint.** `status` alone gates visibility;
  `flag` is now attached to three of the four statuses. Code that assumed
  `flag != null` implies "held" must read `status` too. The review queue keeps
  using `status = processing`, so it is unaffected.
- **Post rows published before `V58` have `flag = NULL`.** A `flag` filter will
  not match them and they read as "no verdict recorded", not as passes.
- **The public API loses two fields.** `flag`/`flagDetail` disappear from
  `PostListResponse`; any client that displayed "why is my post pending" must
  move to the admin endpoints (the frontend handoff note covers the migration).
- **`FILTER_PASSED` on `unpublished` is possible.** A post the filter cleared
  and an admin then rejected keeps `FILTER_PASSED`. That is accurate — the
  filter's verdict did not change — but it means `flag` alone never implies
  visibility in either direction.
- **One more flag value to keep in sync** in the admin `?flag=` filter and its
  validation message.

## Verification

- `V58` applies on a fresh database: every integration context runs Flyway from
  empty, so the widened vocabulary and the replaced constraint are proven.
- `PostEmbeddingSchemaIntegrationTest` proves the database accepts a flag on
  `published` and `unpublished` and still rejects one on `drafted`.
- `ContentFilterOutboundAdapterTest` and `PostContentFilterIntegrationTest`
  prove a pass stores `FILTER_PASSED` with the score and applied threshold, and
  that re-filtering overwrites the previous verdict.
- `AdminPostServiceTest` and `ModeratePostIntegrationTest` prove approval and
  rejection keep the blocker they found.
- `PostContentFilterIntegrationTest` proves the member-facing response no longer
  carries `flag`/`flagDetail`, while `AdminPostControllerIntegrationTest` proves
  the admin list still does, including `?flag=FILTER_PASSED`.
- The pre-`V58` published rows are deliberately not backfilled and are not
  covered by an automated test; they need a database that predates `V58`.
