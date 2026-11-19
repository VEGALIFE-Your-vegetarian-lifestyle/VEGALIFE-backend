# ADR-029: Split `pending` Out of `processing`, and Hold Every Post That Carries Media

## Status

Accepted (supersedes [ADR-023](023-post-processing-status-and-flag-blocker.md) decision 4;
amends the state table in [ADR-025](025-post-flag-survives-publication.md))

## Date

2026-11-19

## Deciders

Project owner, backend implementation.

## Context

Two problems surfaced once the semantic filter was the only publication gate.

**1. The filter only reads text.** `ContentFilterService` embeds `title + content`.
`post.featured_image_url` and the `post_media` join table are never inspected, so an
on-topic caption with an off-topic image is published by a `PASS`. The author can also
attach media *after* publication — `PostService.attachMedia()` writes the join table and
changes nothing else — so the filter's verdict never covers the media at all.

**2. `processing` means two different things.** ADR-023 decision 4 made the admin review
queue `status = processing`, which also covers "a filter run is in flight". A reviewer
cannot tell, from `status` alone, whether a post is still being filtered or is ready for a
human, and the dashboard has to count `processing` *with* a flag to approximate "held".
`flag != null` is the only signal that a verdict exists, so the lifecycle column carries a
fact it cannot express on its own.

## Decision

**`pending` is a distinct lifecycle state for "a verdict exists, a human decision does
not", and a post that carries media is never published by the filter.**

### 1. Status vocabulary

`drafted`, `processing`, `pending`, `published`, `unpublished`.

- `drafted` — no publish intent, or withdrawn by the author. No flag.
- `processing` — submitted; a filter run is outstanding. **No flag.**
- `pending` — a verdict was recorded; an administrator has not decided yet. Carries the
  verdict in `flag`.
- `published` — the filter passed and no media needed review, or an admin approved it.
- `unpublished` — an admin rejected it.

`processing` and `pending` are separated by the verdict, which is now provable:

```sql
CHECK (status <> 'processing' OR flag IS NULL)
```

The ADR-025 constraint `CHECK (flag IS NULL OR status <> 'drafted')` stays. `pending` is
not required to carry a flag at the database level: a post published before `V58` has
`flag = NULL` ("no verdict recorded"), and moving such a post to `pending` must not invent
one.

### 2. Media is a review trigger, not a score input

A post "carries media" when `featured_image_url` is non-blank, or when it has at least one
non-deleted row in `post_media`. Media is never embedded and never scored — the filter
still embeds `title + content` only (ADR-026).

When a filter run ends in `PASS` and the post carries media, the run records the score and
`FILTER_PASSED` exactly as it would for a pass, but the post moves to `pending` instead of
`published`. The flag is the filter's verdict and is not rewritten or annotated: `pending +
FILTER_PASSED` already says "the text cleared, a human has not looked at the media". Every
other outcome (`FAIL`, `UNCERTAIN`, duplicate, unavailable) already needs a human and lands
at `pending` too.

### 3. Media added after publication re-enters review

Attaching media to a `published` post, or giving it a new non-blank `featuredImageUrl`,
moves it to `pending` and keeps the flag it had. Detaching media, re-sending the same set, or
clearing the featured image does not trigger review — none of those adds content no run has
seen. The filter is **not** re-run: the text did not change, and the existing score still
describes it.

A `drafted` post stays a draft (no review, no visibility). A `processing` post is left
alone — the pending run reads the media set at delivery time. An `unpublished` post stays
refused; the author appeals by editing and re-submitting.

### 4. Every writer and reader follows the split

- The review queue becomes `GET /api/admin/posts?status=pending`.
- The stale-message sweep settles a lost run to `pending` + `FILTER_SKIPPED`.
- Disabling the filter parks a submitted post at `pending` + `FILTER_SKIPPED`.
- The dashboard's "posts held for review" counts `pending`.
- A re-check (`publish: true`, or a title/content change) still moves the post to
  `processing` and clears the previous verdict; the new run writes the next one.
- Approve -> `published`, reject -> `unpublished`, both keeping the flag (ADR-025).

## Considered options

- **Option A — keep `processing` and infer "held" from `flag != null` (status quo).**
  Rejected: the reviewer's queue is a predicate over two columns, the dashboard already
  approximates it, and a status column that cannot state "waiting for a human" is the
  thing it exists for.
- **Option B — rename `processing` to `pending`.** Rejected: it would collapse "still
  filtering" and "ready for a human" again, which is the ambiguity being removed.
- **Option C — score media too (image embeddings / a vision model).** Rejected for now:
  a second provider surface, cost, and latency for a decision an admin already makes; the
  requirement is that media is *reviewed*, not that it is *scored*.
- **Option D — a new `MEDIA_UNREVIEWED` flag.** Rejected: it would overwrite the text
  verdict, and `pending + FILTER_PASSED` already distinguishes the case without widening
  the flag vocabulary or the admin `?flag=` filter.
- **Option E — block any post with media at creation.** Rejected: it would stop every
  image post regardless of content, instead of routing it to a human.

## Consequences

**Positive**

- The review queue is one status (`pending`) and means exactly one thing.
- Off-topic media can no longer ride an on-topic caption into the feed, at publication or
  afterwards.
- `flag` and `status` no longer overlap: a verdict exists iff the post is not in flight.

**Negative / trade-offs**

- **Every post with an image now waits for an admin.** The filter's `PASS` no longer
  publishes it, so publication latency for image posts is a human's turnaround.
- **A published post loses visibility when media is attached** until an admin approves it.
  Accepted: the alternative is unreviewed media in the feed.
- **One more status value** in the API, the admin filters, the dashboard and every
  documented transition table.
- **A one-time backfill** (`V61`) moves existing `processing` + flag rows to `pending`.

## Verification

- `V61` applies on a fresh database: every integration context runs Flyway from empty, so
  the widened `chk_post_status` and the new `chk_post_processing_has_no_flag` are proven.
- `PostEmbeddingSchemaIntegrationTest` proves the database rejects a flag on `processing`
  and accepts `pending` with and without one.
- `ContentFilterOutboundAdapterTest` proves a pass with a featured image or attached media
  settles at `pending` + `FILTER_PASSED`, and a pass without media still publishes.
- `PostServiceTest` proves `attachMedia`/`featuredImageUrl` move a published post to
  `pending`, and that a draft or an in-flight post is untouched.
- `PendingFilterSweepJobTest`, `AdminPostServiceTest`, `AdminDashboardServiceTest` prove
  the sweep, the admin guard and the dashboard follow `pending`.
