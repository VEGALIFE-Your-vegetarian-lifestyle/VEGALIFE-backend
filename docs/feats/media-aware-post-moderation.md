# Feature Spec: Media-Aware Post Moderation and a `pending` Status

## Status

In progress

## Author / owner

Backend team, with the design decision recorded in
[ADR-029](../adrs/029-post-pending-status-and-media-review.md).

## Summary

The semantic filter only reads `title + content`, so a post can carry an off-topic
featured image or attached media and still be published by a text `PASS`. This change makes
media a review trigger: a post that carries media is never published by the filter, and
media added after publication sends the post back for a human decision. To make "waiting for
a human" expressible, `pending` becomes a distinct post status; `processing` keeps only its
"a filter run is outstanding" meaning.

## Problem / motivation

- `ContentFilterService.filter()` embeds `title + content`; `featuredImageUrl` and
  `post_media` are never read. An on-topic caption with an off-topic image passes.
- `PostService.attachMedia()` replaces the media set and saves, with no status change and
  no re-check. A post that passed with no media can gain off-topic media while staying
  `published`.
- `PATCH /api/posts/{postId}` treats only `title`/`content` as a content change, so a new
  `featuredImageUrl` on a published post is not reviewed either.
- `processing` currently means both "filter run in flight" and "held for an admin"
  (ADR-023 decision 4). The admin queue and the dashboard "held posts" metric both work
  around this with `flag != null`.

## Goals

- A post that carries media (non-blank `featuredImageUrl`, or at least one non-deleted
  attached media row) is never published by a filter `PASS`; it waits for an admin.
- The filter still runs and the score/verdict are still recorded for such a post.
- Attaching media, or giving a `published` post a new non-blank `featuredImageUrl`, moves it
  to `pending` without re-running the filter. Detaching media, re-sending the same set, or
  clearing the image leaves the status unchanged.
- `pending` is a first-class status: the admin review queue is `status=pending`, and every
  writer/reader that used `processing` for "held" uses `pending`.
- A post without media behaves exactly as before: a `PASS` publishes it.

## Non-goals

- Scoring or classifying images. Media is reviewed by a human; it is never embedded.
- Removing media from an existing post. A `null` `featuredImageUrl` still means "unchanged"
  and `POST /api/posts/{postId}/media` replaces the set rather than extending it.
- Auditing admin decisions (ADR-027 keeps the platform without an audit trail).
- Changing thresholds, bands, centroids, or the embedding provider.

## Acceptance criteria

1. `POST /api/posts` with `publish: true` and a `featuredImageUrl`, whose filter run passes,
   ends at `status = pending`, `flag = FILTER_PASSED`, with `contentScore` and
   `flagDetail` populated, and is absent from the public feed.
2. The same request without a featured image ends at `status = published`,
   `flag = FILTER_PASSED`.
3. `POST /api/posts/{postId}/media` on a `published` post with a non-empty set moves it to
   `pending` and keeps its flag; an empty set, or an identical re-send, leaves it
   `published`.
4. `PATCH /api/posts/{postId}` that changes `featuredImageUrl` to a new non-blank value on a
   `published` post moves it to `pending` and keeps its flag; clearing it leaves the post
   alone. A `drafted` post stays `drafted`.
5. `GET /api/admin/posts?status=pending` lists every post awaiting a decision; an unknown
   status value is still a `400`.
6. `POST /api/admin/posts/{postId}/approve` publishes a `pending` post; `/reject` sets
   `unpublished`. Both keep the flag.
7. The stale-message sweep and the disabled-filter bypass settle a post at
   `pending` + `FILTER_SKIPPED`, never at `processing` with a flag.
8. The database rejects `status = 'processing'` with a non-null `flag`, and accepts
   `status = 'pending'` with or without one.
9. `GET /api/posts/me?status=pending` is accepted and returns the caller's pending posts.

## Design overview

- `PostStatus` gains `pending`; `processing` is "verdict outstanding", `pending` is
  "verdict recorded, human decision outstanding".
- `ContentFilterOutboundAdapter.apply()` branches on a media check for the `PASS` case;
  every non-pass outcome blocks to `pending`.
- `PostService` moves media changes on a published post to `pending` (flag untouched) and
  changes the "filter disabled" parking state to `pending`.
- `PendingFilterSweepJob`, `AdminDashboardService`, `AdminPostService`,
  `MyPostListRequest`, `PostListRequest` and the API docs follow `pending`.
- Migration `V61` widens `chk_post_status`, moves existing `processing` + flag rows to
  `pending`, and adds `chk_post_processing_has_no_flag`.

## Success metrics

- Zero published posts with media that have no admin decision after this change.
- The admin queue returns only posts with a recorded verdict (`flag` non-null for all rows
  created after the migration).

## Risks

- Image posts take longer to appear publicly (human turnaround). Mitigated by the admin
  queue now being a single, unambiguous predicate.
- Clients that hard-coded four status values need the fifth; the status filter DTOs and
  API docs are updated in the same change.

## Related documents

- [ADR-029](../adrs/029-post-pending-status-and-media-review.md)
- [BR-POST-004, BR-POST-007, BR-POST-010, BR-POST-012, BR-POST-015](../brs/posts.md)
- [BR-FILTER-005, BR-FILTER-007, BR-FILTER-008, BR-FILTER-009](../brs/posts.md)
