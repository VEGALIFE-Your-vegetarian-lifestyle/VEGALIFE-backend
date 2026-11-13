# Business Rules: Posts

## Rule Index

| Rule ID | Title | Status | Last Reviewed |
|---------|-------|--------|---------------|
| BR-POST-001 | Post Ownership Comes from Authentication | Active | 2026-09-26 |
| BR-POST-002 | Users List Only Their Non-Deleted Posts | Active | 2026-09-26 |
| BR-POST-003 | User Post Lists Use Bounded Newest-First Pagination | Active | 2026-09-26 |
| BR-POST-004 | New Posts Start as Drafts, and Submitting Parks Them in Processing | Active | 2026-11-13 |
| BR-POST-005 | Post Title and Content Are Required | Active | 2026-09-26 |
| BR-POST-006 | Users Edit Only Their Own Non-Deleted Posts | Active | 2026-09-27 |
| BR-POST-007 | Post Edits Change Only Supplied Fields | Active | 2026-11-13 |
| BR-POST-008 | Posts Are Soft-Deleted by Owner or Administrator | Active | 2026-09-29 |
| BR-POST-009 | Only Administrators Hide Posts, and It Is Logged | Retired (ADR-023) | 2026-11-13 |
| BR-POST-010 | Only Published Posts Are Public | Active | 2026-11-13 |
| BR-POST-011 | Single Post Detail Excludes Only Deleted Posts, Not Ownership | Active | 2026-10-10 |
| BR-FILTER-004 | Semantic Relevance Uses Three Bands and Configured Thresholds | Active | 2026-11-13 |
| BR-FILTER-005 | Only Publish Intent Triggers Filtering | Active | 2026-11-13 |
| BR-FILTER-006 | Filtering Is Asynchronous Through the Outbound Queue | Active | 2026-11-13 |
| BR-FILTER-007 | A Passed Filter Publishes the Post | Active | 2026-11-13 |
| BR-FILTER-008 | A Failed or Uncertain Filter Holds the Post for an Admin | Active | 2026-11-13 |
| BR-FILTER-009 | Posts Stuck in Flight Are Marked Filter-Skipped After 24 Hours | Active | 2026-11-13 |

---

---

# Business Rule: Post Ownership Comes from Authentication

## Rule ID

`BR-POST-001`

## Status

Active

## Statement

When a user creates or lists posts, the user's identity is taken from the authenticated JWT. A client-supplied user ID must not determine post ownership or which user's posts are listed.

## Rationale

Deriving ownership from the authenticated identity prevents users from creating posts under another account or retrieving another user's private post-management list.

## Scope & Exceptions

Applies to `POST /api/posts`, `GET /api/posts`, and `PATCH /api/posts/{postId}`. Administrative moderation APIs are outside this rule and require their own authorization contract.

## Enforcement

- Controller: `PostController` receives `@AuthenticationPrincipal UUID userId` for these endpoints.
- Service: `PostService.createPost()` assigns the loaded user; list/edit operations use that user ID when querying posts.
- API references: `docs/apis/post/post-posts.md`, `docs/apis/post/get-posts.md`, and `docs/apis/post/patch-posts-postid.md`.

## Last Reviewed

2026-09-26, by Vegalife backend team

---

# Business Rule: Users List Only Their Non-Deleted Posts

## Rule ID

`BR-POST-002`

## Status

Active

## Statement

The user post-list endpoint returns only posts owned by the authenticated user whose `deleted_at` is null. It includes all post statuses so the owner can see posts that are not publicly published.

## Rationale

Owners need to manage their own content, while soft-deleted records and other users' content must remain outside their list.

## Scope & Exceptions

Applies to `GET /api/posts`. It does not define visibility for a public feed or admin moderation view.

## Enforcement

- Repository: `PostRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc()`.
- API reference: `docs/apis/post/get-posts.md`.

## Last Reviewed

2026-09-26, by Vegalife backend team

---

---

# Business Rule: User Post Lists Use Bounded Newest-First Pagination

## Rule ID

`BR-POST-003`

## Status

Active

## Statement

`GET /api/posts` uses zero-based pagination. If omitted, `page` is `0` and `size` is `20`; `size` must be between `1` and `100`. Results are sorted by creation time descending.

## Rationale

Bounded pages control response size, and newest-first ordering lets users see their recent posts first.

## Scope & Exceptions

Applies to the authenticated user's post-list endpoint. Clients cannot request a different sort order.

## Enforcement

- Request DTO: `PostListRequest` validates page and size and supplies defaults.
- Repository: `PostRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc()` applies newest-first ordering.
- API reference: `docs/apis/post/get-posts.md`.

## Last Reviewed

2026-09-26, by Vegalife backend team

---

---

# Business Rule: New Posts Start as Drafts, and Submitting Parks Them in Processing

## Rule ID

`BR-POST-004`

## Status

Active

## Statement

When a user creates a post, the system sets its view count to `0`. The post starts as a private
draft (`drafted`, no `flag`) unless the client asks to submit it (`publish: true`), which is accepted
only when the post has at least one active category (BR-CONTENT-004). Submitting does not publish:
the post is saved as `processing` with no `flag` and a `CONTENT_FILTER` message is enqueued
(BR-FILTER-005, BR-FILTER-006). It becomes `published` only when the filter passes (BR-FILTER-007) or
an Administrator approves it (BR-POST-012). `status`, `flag`, and view count cannot be set by the
client.

## Rationale

System-controlled initial values keep the post lifecycle consistent. Separating "submitted" from
"published" makes it possible to say, for every post, whether a decision is still outstanding —
which a single draft/published pair cannot express.

## Scope & Exceptions

Applies to `POST /api/posts`. The status vocabulary is `drafted`, `processing`, `published`,
`unpublished`. A `flag` records the last verdict and is absent only on a draft (ADR-025); the
constraint is `flag IS NULL OR status <> 'drafted'`. A draft (`publish: false`) keeps `flag` NULL
and is never filtered (BR-FILTER-005).

## Enforcement

- Service: `PostService.createPost()` sets status, view count, and blocker; it enqueues the filter
  message in the caller's transaction, and `publishedAt` is assigned only when the filter or an
  admin decision publishes the post.
- Database: `V56__post_processing_status_and_flag_blocker.sql` rewrites the old status/flag values
  and adds `flag_detail`; `V58__keep_post_flag_after_publication.sql` widens the vocabulary with
  `FILTER_PASSED` and replaces the constraint with
  `CHECK (flag IS NULL OR status <> 'drafted')` (ADR-025).
- Request DTO: `PostCreateRequest` does not expose lifecycle fields.
- API reference: `docs/apis/post/post-posts.md`.

## Last Reviewed

2026-11-13, by Vegalife backend team

---

---

# Business Rule: Post Title and Content Are Required

## Rule ID

`BR-POST-005`

## Status

Active

## Statement

Post creation requires a non-blank title no longer than 255 characters, non-blank plain `content` (used only as the semantic-filtering input, BR-FILTER-004), and a `rawContent` JSON object (the frontend editor's rich-text document). A featured image URL is optional.

## Rationale

Each post needs a title and body to be useful to readers; an image is supplementary content. Plain content and the rich-text document are both required because the frontend editor only produces rich text, while filtering only understands plain text.

## Scope & Exceptions

Applies to requests to create a post through `POST /api/posts`. The existing database also requires `title`, `content`, and `raw_content` columns to be non-null.

## Enforcement

- DTO: `PostCreateRequest` uses `@NotBlank` for title and content, `@NotNull` plus a custom `@AssertTrue` check for `rawContent` (must be a JSON object), and `@Size(max = 255)` for title.
- Database: the `post` table defines `title`, `content`, and `raw_content` as `NOT NULL` (migration `V27__add_post_raw_content_drop_type_video_url.sql`).
- API reference: `docs/apis/post/post-posts.md`.

## Last Reviewed

2026-10-05, by Vegalife backend team

---

# Business Rule: Users Edit Only Their Own Non-Deleted Posts

## Rule ID

`BR-POST-006`

## Status

Active

## Statement

An authenticated user may edit a post only when the post is owned by that user and `deleted_at` is null; an Administrator may edit any non-deleted post (BR-CONTENT-001). For a non-admin, a missing post, soft-deleted post, or post owned by another user is reported as not found. Every edit an Administrator makes to another user's post is recorded in `moderation_log` (actor, action, target, time) per BR-ADMIN-002.

## Rationale

Post ownership is private user content. Applying the same owner boundary to edits prevents cross-account changes and avoids disclosing whether another user's post exists.

## Scope & Exceptions

Applies to `PATCH /api/posts/{postId}`.

## Enforcement

- Controller: `PostController` supplies the authenticated user ID from the JWT principal.
- Repository/service: the post is looked up by post ID, owner ID, and non-deleted state.
- API reference: `docs/apis/post/patch-posts-postid.md`.

## Last Reviewed

2026-09-27, by Vegalife backend team

---

---

# Business Rule: Post Edits Change Only Supplied Fields

## Rule ID

`BR-POST-007`

## Status

Active

## Statement

Post edits require `content` and `rawContent` on every request, and accept a non-empty subset of `title`, `featuredImageUrl`, `categoryIds`, and `publish` on top of them. Only supplied, non-null values among the optional fields are applied; omitted and null values leave existing data unchanged. A supplied title must be non-blank and no longer than 255 characters; `content` must not be blank and `rawContent` must be a JSON object. `categoryIds` replaces the category set and may only reference active categories (BR-CONTENT-004). `publish: true` submits the post for a filter re-check (BR-FILTER-005): it moves to `processing` with no blocker and only becomes `published` once the filter passes (BR-FILTER-007) or an admin approves it (BR-POST-012). `publish: false` withdraws the post immediately from any state except `drafted` (where it is a no-op): `status = drafted`, `publishedAt`, `flag`, and `flagDetail` are cleared, and no filter is queued. A title or plain-content change to a `published`, `unpublished`, or held (`processing` + blocker) post re-queues it for filtering; a post merely in flight is not queued again; a `rawContent`-only change never re-queues, since filtering only reads plain `content`. A post that is or becomes published must keep at least one category (BR-CONTENT-003). Ownership and view count are not editable.

## Rationale

Partial updates let clients change title, image, categories, or publish state without resending the full post; `content`/`rawContent` are required on every edit because the frontend editor always resubmits both together.

## Scope & Exceptions

Applies to `PATCH /api/posts/{postId}`. Removing a featured image by sending null is not supported by this endpoint.

## Enforcement

- Request DTO: `PostUpdateRequest` requires `content` (`@NotBlank`) and `rawContent` (`@NotNull` plus a JSON-object `@AssertTrue` check) on every request; other fields stay optional (null allowed, blank/invalid rejected when supplied).
- Service: `PostService.updatePost()` applies only non-null optional request values and enforces the category and publish rules.
- API reference: `docs/apis/post/patch-posts-postid.md`.

## Last Reviewed

2026-09-30, by Vegalife backend team

---

# Business Rule: Posts Are Soft-Deleted by Owner or Administrator

## Rule ID

`BR-POST-008`

## Status

Active

## Statement

A post may be deleted only by its owner or an Administrator (BR-CONTENT-001). Deletion is soft: `deleted_at` is set and the row is kept, so the post disappears from user lists and can no longer be edited or deleted. For a non-admin, a missing, already-deleted, or other user's post is reported as not found. Every deletion an Administrator makes to another user's post is recorded in `moderation_log` (BR-ADMIN-002).

## Rationale

Ownership boundaries protect user content, soft deletion preserves data for moderation and recovery, and the moderation log keeps administrator actions reviewable.

## Scope & Exceptions

Applies to `DELETE /api/posts/{postId}`. Restoring deleted posts is not supported.

## Enforcement

- Controller: `PostController.deletePost()` supplies the JWT principal and admin flag.
- Service: `PostService.deletePost()` uses `findManageablePost()` and records the moderation entry.
- API reference: `docs/apis/post/delete-posts-postid.md`.

## Last Reviewed

2026-09-29, by Vegalife backend team

---

# Business Rule: Only Administrators Hide Posts, and It Is Logged — RETIRED

## Rule ID

`BR-POST-009`

## Status

Retired by [ADR-023](../adrs/023-post-processing-status-and-flag-blocker.md) on 2026-11-13.

## Statement

*(Historical.)* Only an Administrator could hide a post or lift a hide. Hiding set
`status = hidden` and cleared the publication timestamp; lifting returned the post to a private
draft. While a post was hidden, only an Administrator could change its publish state. Every hide and
unhide was recorded in `moderation_log`.

## Why it was retired

`hidden` was a second, parallel "not visible" state alongside `unpublished`, and it could only be
entered and left by an admin. That made the owner unable to withdraw or resubmit their own post, and
forced every reader to know two overlapping rules. The model now has one negative terminal state —
`unpublished`, reached only by an admin rejection (BR-POST-012) — and one unlist action owned by the
author: `PATCH /api/posts/{postId}` with `"publish": false`, returning the post to `drafted`.

The `PATCH /api/posts/{postId}/visibility` route was removed outright rather than deprecated; a
request to it answers `404 Not Found`. A request to change an `unpublished` post's state by its owner
is refused with `404` (it is reported as not manageable), and only an admin decision or a passing
filter changes it.

## Enforcement

- Migration `V56__post_processing_status_and_flag_blocker.sql` rewrites every `hidden` row: one that
  had passed the filter (`flag = PASSED`) is restored to `published` (its `published_at` recovered
  from `updated_at`); one with no `flag` (a draft an admin hid) returns to `drafted`; every other
  hidden post was held by the filter and becomes `processing` so a human decides it.
- `PostController` no longer declares the visibility operation and `PostVisibilityRequest` was
  deleted.
- Regression test: `PostControllerIntegrationTest.updateVisibilityEndpoint_noLongerExists`.

## Last Reviewed

2026-11-13, by Vegalife backend team

---

# Business Rule: Only Published Posts Are Public

## Rule ID

`BR-POST-010`

## Status

Active

## Statement

When a member's posts are listed, guests and other members see only `published`, non-deleted posts. Drafts and other non-public states (`drafted`, `processing`, `unpublished`) are visible only to the post's creator and Administrators (BR-CONTENT-003). A post is not public while a decision is outstanding (`processing`, with or without a `flag`) or after an admin rejection (`unpublished`).

## Rationale

Published content is open to everyone (BR-PUBLIC-001, BR-SEARCH-002), while unpublished or moderated content must stay private to its owner and administrators.

## Scope & Exceptions

Applies to `GET /api/users/{userId}/posts`. `GET /api/posts` is the caller's own list and already includes all non-deleted statuses.

## Enforcement

- Security: `SecurityConfig` permits unauthenticated `GET /api/users/*/posts`.
- Service: `PostService.listPostsOfUser()` selects the published-only query unless the viewer is the owner or an Administrator.
- API reference: `docs/apis/post/get-users-userid-posts.md`.

## Last Reviewed

2026-10-10, by Vegalife backend team

---

# Business Rule: Single Post Detail Excludes Only Deleted Posts, Not Ownership

## Rule ID

`BR-POST-011`

## Status

Active

## Statement

When a caller — guest or authenticated — requests a single post by id, the post is returned whenever `deleted_at` is null, regardless of its status. Neither status nor ownership is checked: another user's post is returned, and the caller's own draft, `processing`, or `unpublished` post is returned too. Only a missing or soft-deleted post returns `404 Post not found`. The endpoint is public (no authentication required).

## Rationale

A shared or linked post must be readable by anyone who opens the link, signed in or not, at whatever stage it is in — the detail endpoint is a direct-by-id read, not a public-feed browse, so it does not gate on the `published` status that controls the feed. Only removal is hidden, and a soft-deleted or unknown id is reported as the same generic 404 so no post existence is leaked.

## Scope & Exceptions

Applies to `GET /api/posts/{postId}`. It does not define owner/admin draft preview, caching, or view-count behavior — none of these exist for this endpoint. `GET /api/posts` (the caller's own list, all statuses, authenticated) and `GET /api/users/{userId}/posts` (public, owner/admin see all statuses) have their own rules (BR-POST-002, BR-POST-010).

## Enforcement

- Repository: `PostRepository.findDetailById(id)` (`where p.id = :id and p.deletedAt is null` — no status predicate).
- Service: `PostService.getPost()` maps a missing result to `ResourceNotFoundException("Post not found")`.
- Security: `SecurityConfig` permits unauthenticated `GET /api/posts/*` (single path segment — matches `feed` and `{postId}`, never the auth-required list route); `PostController` declares `@SecurityRequirement` per protected operation instead of at class level, so `getPost` is documented as public in OpenAPI.
- API reference: `docs/apis/post/get-posts-postid.md`.

## Last Reviewed

2026-10-10, by Vegalife backend team

---

---

# Business Rule: Semantic Relevance Uses Three Bands and Configured Thresholds

## Rule ID

`BR-FILTER-004`

## Status

Active

## Statement

The content is embedded and mean-centered — the text embedding and both centroids have the seed-corpus mean subtracted first (raw e5 embeddings are anisotropic, so an uncentered margin score collapses into a ~0.46–0.54 band no threshold can reach) — and compared by cosine similarity with two precomputed centroids, one built from on-topic seed texts and one from off-topic seed texts, both Vietnamese and English. The relevance score is the normalized margin between the two centered similarities, `score = (cos(text - mean, onTopic - mean) - cos(text - mean, offTopic - mean) + 1) / 2`, clamped to `[0, 1]`. The score maps to exactly one band: `score >= accept-threshold` is a pass; `score < reject-threshold` is a refusal; anything between the two is uncertain. The two thresholds are read live, per scoring call, from the `content_filter_accept_threshold` / `content_filter_reject_threshold` platform settings (issue #13 follow-up), falling back to the compiled `app.filter.accept-threshold` / `app.filter.reject-threshold` defaults on a missing, invalid, or contradictory (`reject >= accept`) stored pair — never a startup or request failure. The compiled defaults are recalibrated whenever the embedding model changes; the current values (`0.70` / `0.45`) are calibrated for `gemini-embedding-001` at 768 dimensions from the measured centered seed distribution (highest off-topic `0.3491`, lowest on-topic `0.7553`). The derivation and the selected values are recorded in [Filter Calibration: gemini-embedding-001](../feats/filter-calibration-gemini.md); thresholds derived for a previous model (e5: `0.65` / `0.43`) must not be carried across. The band is an internal observation (`FilterOutcome`: `PASS` / `FAIL` / `UNCERTAIN`), deliberately not a persisted value; what a post stores in `post.flag` is the verdict — `FILTER_PASSED`, `FILTER_FAILED`, `SIMILAR_CONTENT`, or `FILTER_SKIPPED` — and it is kept after publication rather than cleared (ADR-023, ADR-025).

## Rationale

A three-band outcome separates confident failures from content that is merely ambiguous, so uncertain posts are held for a human instead of being published or silently rejected. Thresholds are configuration so tuning does not require a redeploy of logic.

## Scope & Exceptions

Applies to every filter run. There is no `NOT_FILTERED` value: a draft carries no flag, and every run ends in exactly one band, persisted as `FILTER_PASSED` or `FILTER_FAILED`. The centroid model and regeneration procedure are recorded in `docs/adrs/007-post-content-filtering.md`.

## Enforcement

- `EmbeddingRelevanceScorer` reads both thresholds from `PlatformSettingsProvider` (falling back to `app.filter.*`) on every `score()` call and returns the band; `ContentFilterService` turns it into a `FilterVerdict` carrying a `FilterOutcome`.
- `post.flag VARCHAR(32)` is constrained to `FILTER_PASSED` / `FILTER_FAILED` / `SIMILAR_CONTENT` /
  `FILTER_SKIPPED` or NULL, and `CHECK (flag IS NULL OR status <> 'drafted')` keeps a verdict off a
  draft (migration V58, ADR-025).
- Unit tests cover all three bands at the exact threshold boundaries; a fixture-based regression test replays captured embeddings through the configured thresholds, and `CentroidRegenerationTest` refreshes those fixtures whenever the centroids are regenerated.

## Last Reviewed

2026-09-30, by Vegalife backend team

---

---

# Business Rule: Only Publish Intent Triggers Filtering

## Rule ID

`BR-FILTER-005`

## Status

Active

## Statement

A post is filtered only when it is submitted for publication or when already-visible content changes. An explicit `publish: true` on create or edit always queues a filter run and moves the post to `processing` with no blocker. Without an explicit `publish` field, a title or plain-`content` change on a post that is `published`, `unpublished` (an admin refusal the author is appealing), or `processing` **with** a blocker re-queues it for filtering; a `rawContent`-only change does not, since filtering never reads `rawContent`. A post merely in flight (`processing`, no blocker) is not queued again — the pending run reads the body at delivery time, so it already sees the edit. A draft — `drafted`, no blocker — is never filtered, and withdrawing with `publish: false` never triggers a filter run.

When the `content_filter_enabled` platform setting is false (a deliberate outage/bypass mode), BR-FILTER-004/006/007 are bypassed for new filter triggers: a post that would have been queued is instead held at `processing` with `flag = FILTER_SKIPPED` and `flagDetail = {"kind":"FILTER_SKIPPED","cause":"DISABLED"}` — no outbound row, no embedding call — so an admin can decide it. `FILTER_SKIPPED` rather than `FILTER_FAILED` because the filter never formed an opinion. This only stops *new* filtering; a post already in flight from before the setting was disabled is still settled by the stale-message sweep (BR-FILTER-009), so disabling the filter never leaves a post in flight forever.

## Rationale

Only content that is (or is about to be) public needs screening; screening drafts would burn embedding calls on work in progress and expose unfinished content to automated judgement.

## Scope & Exceptions

Applies to `POST /api/posts` and `PATCH /api/posts/{postId}`. Ownership and category validation (BR-POST-006, BR-POST-007, BR-CONTENT-003/004) are unchanged and happen before any queueing decision. Withdrawing stays immediate (BR-CONTENT-003).

## Enforcement

- Service: `PostService.createPost()` / `updatePost()` decide whether to enqueue from an explicit `publish: true` or from the content dirty check on a `published`/`unpublished`/held post, and enqueue in the caller's transaction. The enqueue-vs-bypass choice reads `PlatformSettingsProvider.isContentFilterEnabled()` (issue #13 follow-up), falling back to `app.filter.enabled` on a missing/invalid stored value.
- `PendingFilterSweepJob` sweeps any `processing` post with no blocker whose queue row is stale, regardless of the current `content_filter_enabled` value — it does not check the setting, so it covers both "message lost" and "filter was disabled mid-flight".
- API references: `docs/apis/post/post-posts.md`, `docs/apis/post/patch-posts-postid.md`.

## Last Reviewed

2026-10-10, by Vegalife backend team

---

# Business Rule: Filtering Is Asynchronous Through the Outbound Queue

## Rule ID

`BR-FILTER-006`

## Status

Active

## Statement

Filtering never runs inside the HTTP request. Enqueueing writes an `outbound_message` row with channel `CONTENT_FILTER` in the same transaction that saves the post, moves the post to `processing` with no blocker, and returns; a scheduled drainer later claims the message and runs the filter. The message row's `created_at` is the enqueue clock the sweep reasons about (BR-FILTER-009). There is no synchronous or real-time filtering path, and a failed filter run is retried by the queue's existing retry/deferred handling rather than failing the user's request.

## Rationale

The embedding call is an external network dependency with unbounded latency; keeping it off the request thread keeps create/edit fast and makes the post durable even if the model or provider is down.

## Scope & Exceptions

Applies to every filter trigger (BR-FILTER-005). The queue is the ADR-005 PostgreSQL outbox with a new `CONTENT_FILTER` channel — no new broker. A message that exhausts retries or is lost is covered by the 24-hour sweep (BR-FILTER-009), so a post is never left in flight forever.

## Enforcement

- `ContentFilterOutboundAdapter` implements the existing `OutboundChannelAdapter` for `CONTENT_FILTER`; `OutboundMessageDrainer` dispatches to it.
- Service: `PostService` uses the outbox enqueue-in-caller-tx pattern so the message and the post commit together.
- Design: `docs/adrs/007-post-content-filtering.md`.

## Last Reviewed

2026-09-30, by Vegalife backend team

---

---

# Business Rule: A Passed Filter Publishes the Post

## Rule ID

`BR-FILTER-007`

## Status

Active

## Statement

When a filter run ends in a pass, the post's status becomes `published`, `flag` becomes `FILTER_PASSED`, `flagDetail` becomes `{"kind":"FILTER_PASSED","score":…,"threshold":…}` (the score and the accept boundary the run cleared), and `publishedAt` is set if it was null (a re-check keeps the original publication time). Every queued run was enqueued by an explicit submit request or by a content change on a post that was meant to be visible (BR-FILTER-005), so a pass publishes the post — the verdict, not the client's request, drives publication. The verdict is applied only while the post is still `processing` and not soft-deleted: a pass arriving after the author withdrew the post is discarded rather than resurrecting it.

## Rationale

Passing the filter is the gate that makes publication legitimate; the transition is driven by the verdict, not by the client's request, so the API response and the public visibility can never disagree about a filtered post. Storing `FILTER_PASSED` instead of clearing the columns is what lets a reviewer later tell a genuine pass from a post an admin approved over a blocker (ADR-025). Discarding a stale pass keeps the author's withdrawal stronger than a late machine decision.

## Scope & Exceptions

Applies to every pass, whether it comes from a create, an edit, or a re-check of a held post. A pass does not rewrite `rejectionReason` (only an admin approve clears it). Compare BR-POST-012 for the admin decision.

## Enforcement

- `ContentFilterOutboundAdapter` guards on `status = processing` and `deletedAt` before scoring, then sets `status = published`, `flag = FILTER_PASSED`, and the `flagDetail` snapshot, stamping `publishedAt` only when it is null.
- Unit tests: `ContentFilterOutboundAdapterTest.passPublishesThePostAndRecordsTheVerdict`, `passIsDiscardedWhenTheAuthorWithdrewWhileTheMessageWasInFlight`.
- API references: `docs/apis/post/post-posts.md`, `docs/apis/post/patch-posts-postid.md`.

## Last Reviewed

2026-11-13, by Vegalife backend team

---

# Business Rule: A Failed or Uncertain Filter Holds the Post for an Admin

## Rule ID

`BR-FILTER-008`

## Status

Active

## Statement

When a filter run refuses or is uncertain, or when it finds the content too similar to a published
post, the post stays `processing` and receives a blocker in `flag`: `FILTER_FAILED` for a refusal or
an uncertain score, or `SIMILAR_CONTENT` for a near-duplicate. `flagDetail` carries the evidence as
one JSON object whose `kind` always equals `flag` (ADR-024): `{kind,outcome,score,threshold}` for
`FILTER_FAILED`, `{kind,postId,similarity,threshold}` for `SIMILAR_CONTENT`, and `{kind,cause}` for
`FILTER_SKIPPED`. The `threshold` is the accept/reject boundary in force when the run was judged,
snapshotted because those thresholds are live platform settings. The filter never unpublishes: only
an Administrator may set `unpublished`, through `POST /api/admin/posts/{postId}/reject`
(BR-POST-012). A refusal or uncertainty logs a WARN carrying the out-of-band score.

## Rationale

A machine refusing content is evidence for a human, not a verdict that destroys the author's work:
holding the post at `processing` keeps it intact for review, keeps it off the feed, and lets an admin
either publish it or reject it with a stated reason. Splitting "the filter said no" from "an admin
said no" means the two can never be confused later, and the author is never told an admin rejected
something no human ever saw.

## Scope & Exceptions

Applies to every non-pass verdict and to the stale-message sweep (BR-FILTER-009), which writes
`FILTER_SKIPPED` with `cause = SWEPT`. The owner can edit the post again; an edit re-queues it
(BR-FILTER-005) and clears the previous blocker until the new run records its own (ADR-025), or the
owner can withdraw it to `drafted` (BR-POST-007). A duplicate's matched post id and its similarity
cannot be recovered later, which is why they are persisted in `flagDetail`.

## Enforcement

- `ContentFilterOutboundAdapter` applies a non-pass verdict as `status = processing` plus
  `flag`/`flagDetail`; it never writes `unpublished`.
- Database: `CHECK (flag IS NULL OR status <> 'drafted')` (V58, ADR-025) and
  `flag IN ('FILTER_PASSED', 'FILTER_FAILED', 'SIMILAR_CONTENT', 'FILTER_SKIPPED')` (V58), plus
  `flag_detail->>'kind' = flag` (migration V57, ADR-024).
- API: `flag`/`flagDetail` are exposed only on the admin post list and detail — never on the
  member-facing post shape — and the review queue is
  `GET /api/admin/posts?status=processing`.

## Last Reviewed

2026-11-13, by Vegalife backend team

---

---

# Business Rule: Posts Stuck in Flight Are Marked Filter-Skipped After 24 Hours

## Rule ID

`BR-FILTER-009`

## Status

Active

## Statement

A scheduled job finds every `CONTENT_FILTER` recipient whose newest outbound message is older than
`app.filter.sweep-max-age` (default 24 hours) and, for each such post still at `processing` with no
blocker, sets `flag = FILTER_SKIPPED` and
`flagDetail = {"kind":"FILTER_SKIPPED","cause":"SWEPT"}`, logging at ERROR. The post stays
`processing`, so it is now visible in the admin review queue instead of waiting invisibly. A post
that is not `processing`, already carries a blocker, or has a fresh outbound message is never
touched.

## Rationale

The outbox retries and then defers, but a message can still be lost (deleted row, exhausted retries,
prolonged provider outage). Without a bound, such a post would stay in flight forever — invisible to
its owner and to any reviewer. `FILTER_SKIPPED` says exactly what happened: no verdict was reached.

## Scope & Exceptions

Applies only to posts `processing` with no blocker; it never re-runs a completed verdict and never
touches drafts. 24 hours is the default and is configuration, not a constant. The outbox row is the
enqueue clock: it is written in the same transaction as the move to `processing`, and terminal rows
are deleted only by the 7-day retention job, so every lost message is always sweepable.

## Enforcement

- `PendingFilterSweepJob` (scheduled, `OutboundRetentionJob` pattern) queries recipients of
  `CONTENT_FILTER` whose latest message is older than the max age, then performs the transition.
- Repository: `PostRepository.countByStatusAndFlagIsNotNullAndDeletedAtIsNull(processing)` backs the
  dashboard's "held posts" metric, which counts blockers rather than a single flag value.
- Unit tests cover: stale recipient settled as `FILTER_SKIPPED`, fresh recipient untouched, a post
  with an existing blocker untouched, a non-`processing` post untouched.

## Last Reviewed

2026-11-13, by Vegalife backend team

---

# Business Rule: Only Administrators Approve or Reject a Submitted Post

## Rule ID

`BR-POST-012`

## Status

Active

## Statement

Only an Administrator may decide a post that is waiting for review, through
`POST /api/admin/posts/{postId}/approve` or `POST /api/admin/posts/{postId}/reject`. A post is
decidable in `processing` and `unpublished`; a `drafted` post has not been submitted and is refused
with `400`.

`approve` sets `status = published`, stamps `publishedAt` when it is null, clears
`rejectionReason`, and requires the post to have at least one category (BR-CONTENT-003). It keeps
`flag`/`flagDetail` exactly as the filter left them, so the row records whether the filter cleared
the post (`FILTER_PASSED`) or a human overrode it (`FILTER_FAILED`, `SIMILAR_CONTENT`,
`FILTER_SKIPPED`) — ADR-025. It also approves every `PENDING` component (recipe, dish, ingredient) of
the post and never resurrects a `REJECTED` component (BR-DISH-001).

`reject` sets `status = unpublished`, clears `publishedAt`, keeps `flag`/`flagDetail` so the decision
record pairs what the machine saw with what the human decided (ADR-025), stores the reason in
`rejectionReason` for the author, and
rejects every still-`PENDING` component.

Every real decision is recorded in `moderation_log` as `APPROVE_POST` / `REJECT_POST` with the
optional reason (BR-ADMIN-002).

## Rationale

Approving and rejecting are the two things a human reviewer actually decides; a generic
"moderate with PUBLISH/UNPUBLISH" verb cannot express "the filter was wrong, publish it". Keeping the
blocker through both decisions is what preserves that distinction: `published` + `FILTER_FAILED`
says the filter was overridden, which clearing the columns used to hide (ADR-025). Refusing `drafted`
prevents an admin from publishing a post its own author never submitted.

## Scope & Exceptions

Applies to `POST /api/admin/posts/{postId}/approve` and `/reject`. It does not cover editing content
(BR-POST-006) or deletion (BR-POST-008). The author's own unlist action is `publish: false`
(BR-POST-007), not rejection. The admin post list's default `flag = all` filter is a query default
chosen so no blocker is silently hidden, not a rule about which posts exist.

## Enforcement

- Security: `AdminPostController` carries `@PreAuthorize("hasRole('ADMIN')")` (ADR-009); no
  `SecurityConfig` path rule grants `/api/admin/**`.
- Service: `AdminPostService.approvePost()` / `rejectPost()` load a non-deleted post, refuse
  `drafted`, validate categories on approve, keep `flag`/`flagDetail` untouched, cascade to
  components, and write the log row.
- Database: `CHECK (flag IS NULL OR status <> 'drafted')` (V58) allows a blocker on `published` and
  `unpublished` by design and still forbids one on a draft.
- API reference: `docs/apis/admin/post-posts-postid-approve.md`.

## Last Reviewed

2026-11-13, by Vegalife backend team

---

# Business Rule: Every Administrator Content-Moderation Action Is Logged

## Rule ID

`BR-ADMIN-002`

## Status

Active

## Statement

Every administrator action that changes another user's content — editing (`EDIT_POST`), deleting
(`DELETE_POST`), approving (`APPROVE_POST`), and rejecting (`REJECT_POST`) — is written to
`moderation_log` with the acting administrator (`actor_id`), the action, the target (`target_type`,
`target_id`), the time (`created_at`), and — for approve/reject — an optional reason. A no-op that
changes nothing is never logged. The log is append-only; entries are never edited or deleted by
application code.

## Rationale

Moderation is an administrator power over content they do not own; a durable, attributable record is
what makes those actions auditable and reviewable after the fact, including the stated justification.

## Scope & Exceptions

Applies to post and comment moderation. Category creation, edit, and retirement are deliberately
**not** logged — BR-ADMIN-002 scopes the log to content/comment moderation, not category management
(the decision recorded in `create-category-api.md`, `edit-category-api.md`, `delete-category-api.md`).
`reason` is optional today; a required-reason contract would be a separate validation change.

The log is the audit trail, not the current state. A rejection reason is also stored on the
rejected row itself (`post.rejection_reason`, `recipe.rejection_reason`, migration `V51`) so the
author can be told why, and that column is cleared when the row is approved again. The two are
complementary: the column answers "why is this rejected right now", the log preserves every
decision in order, including reasons that were later superseded.

## Enforcement

- Table: `moderation_log` (migration `V18`); `V32` adds the nullable `reason` column.
- Service: every writer builds a `ModerationLog` through `ModerationLogRepository` in the same
  transaction as the change (`PostService`, `AdminPostService`).
- API references: `docs/apis/post/patch-posts-postid.md`, `docs/apis/post/delete-posts-postid.md`,
  `docs/apis/admin/post-posts-postid-approve.md`.

## Last Reviewed

2026-10-10, by Vegalife backend team

---

# Business Rule: Only Approved Recipes May Be Attached to a Post

## Rule ID

`BR-POST-013`

## Status

Active

## Statement

A post may reference a recipe only when that recipe's `moderationStatus` is `APPROVED` and the recipe
is not soft-deleted. A `PENDING` or `REJECTED` recipe id in `POST /api/posts/{postId}/recipes` is
refused with `404` and the message `One or more recipes are not found or are not approved`; nothing is
written, so the request is all-or-nothing.

The rule covers every id in the request, including ids already attached, and applies to
Administrators exactly as it does to the post's owner. Approving a recipe is an act of the moderation
queue (`POST /api/admin/recipes/{recipeId}/approve` or the post-approval cascade), never a side effect
of attaching it.

## Rationale

A recipe body is moderated as a unit, and `PENDING` recipes are invisible in every public listing. If
a post could carry one, publishing the post would be a way to show an unreviewed dish, ingredients,
and instructions to everyone — bypassing the queue the recipe was placed in (ADR-016) and giving the
content filter a component it cannot vouch for. Rejecting with `404` rather than `400` also keeps the
endpoint from acting as an oracle for "this recipe id exists but is unreviewed", matching how a
missing or another user's post is reported.

Validating already-attached ids, not just newly added ones, closes the re-send loophole: a post that
somehow holds a now-unapproved recipe cannot keep it by echoing the same set back, and a recipe that
loses approval is detached on the next edit rather than staying live.

## Scope & Exceptions

Applies to `POST /api/posts/{postId}/recipes` and is not re-checked at publish time — the attach call
is the only write, so refusing there is the enforcement. Not covered: reading a post whose recipe was
approved at attach time and later demoted (the association is untouched by moderation), and the
approval cascade in `AdminPostService`, which approves components of a post already attached under
this rule.

## Enforcement

- Repository: `RecipeRepository.findByIdInAndDeletedAtIsNullAndModerationStatus` is the single
  approved-only lookup used by the attach path.
- Service: `PostService.resolveRecipes()` compares the returned count against the requested count and
  throws `ResourceNotFoundException`, which the global handler maps to `404`.
- Tests: `PostServiceTest.attachRecipes_rejectsRecipeThatIsNotApproved`,
  `attachRecipes_rejectsUnknownOrDeletedRecipe`.
- API reference: `docs/apis/post/post-posts-postid-recipes.md`.

## Last Reviewed

2026-11-12, by Vegalife backend team

