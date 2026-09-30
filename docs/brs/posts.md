# Business Rules: Posts

## Rule Index

| Rule ID | Title | Status | Last Reviewed |
|---------|-------|--------|---------------|
| BR-POST-001 | Post Ownership Comes from Authentication | Active | 2026-09-26 |
| BR-POST-002 | Users List Only Their Non-Deleted Posts | Active | 2026-09-26 |
| BR-POST-003 | User Post Lists Use Bounded Newest-First Pagination | Active | 2026-09-26 |
| BR-POST-004 | New Posts Start in the Created State | Active | 2026-09-30 |
| BR-POST-005 | Post Title and Content Are Required | Active | 2026-09-26 |
| BR-POST-006 | Users Edit Only Their Own Non-Deleted Posts | Active | 2026-09-27 |
| BR-POST-007 | Post Edits Change Only Supplied Fields | Active | 2026-09-30 |
| BR-POST-008 | Posts Are Soft-Deleted by Owner or Administrator | Active | 2026-09-29 |
| BR-POST-009 | Only Administrators Hide Posts, and It Is Logged | Active | 2026-09-29 |
| BR-POST-010 | Only Published Posts Are Public | Active | 2026-09-30 |
| BR-FILTER-004 | Semantic Relevance Uses Three Bands and Configured Thresholds | Active | 2026-09-30 |
| BR-FILTER-005 | Only Publish Intent Triggers Filtering | Active | 2026-09-30 |
| BR-FILTER-006 | Filtering Is Asynchronous Through the Outbound Queue | Active | 2026-09-30 |
| BR-FILTER-007 | A Passed Filter Publishes the Post | Active | 2026-09-30 |
| BR-FILTER-008 | A Rejected or Uncertain Filter Flags the Post | Active | 2026-09-30 |
| BR-FILTER-009 | Posts Stuck Pending Are Flagged After 24 Hours | Active | 2026-09-30 |

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

# Business Rule: New Posts Start in the Created State

## Rule ID

`BR-POST-004`

## Status

Active

## Statement

When a user creates a post, the system sets its view count to `0`. The post starts as a private draft (`created`) unless the client asks to publish (`publish: true`), which is accepted only when the post has all information required for its type and at least one active category (BR-CONTENT-002/003/004). Publishing is then gated by content filtering: the post is saved as `created` with `flag = PENDING`, `publish_intent = true`, and a `CONTENT_FILTER` message enqueued (BR-FILTER-005, BR-FILTER-006), and it becomes `published` with `publishedAt` set only once filtering returns `PASSED` (BR-FILTER-007). Status and view count cannot otherwise be set by the client.

## Rationale

System-controlled initial values keep post lifecycle state consistent and leave publication decisions to later processing.

## Scope & Exceptions

Applies to `POST /api/posts`. The `flag` value is the filter state and is distinct from `status` (BR-FILTER-004); a draft (`publish: false`) keeps `flag` NULL and is never filtered (BR-FILTER-005).

## Enforcement

- Service: `PostService.createPost()` sets status and view count, records publish intent, and enqueues the filter message in the caller's transaction; `publishedAt` is assigned only when the filter verdict `PASSED` is applied.
- Request DTO: `PostCreateRequest` does not expose lifecycle fields.
- API reference: `docs/apis/post/post-posts.md`.

## Last Reviewed

2026-09-30, by Vegalife backend team

---

---

# Business Rule: Post Title and Content Are Required

## Rule ID

`BR-POST-005`

## Status

Active

## Statement

Post creation requires a non-blank title no longer than 255 characters and non-blank content. A featured image URL is optional.

## Rationale

Each post needs a title and body to be useful to readers; an image is supplementary content.

## Scope & Exceptions

Applies to requests to create a post through `POST /api/posts`. The existing database also requires title and content columns to be non-null.

## Enforcement

- DTO: `PostCreateRequest` uses `@NotBlank` for title and content and `@Size(max = 255)` for title.
- Database: the existing `post` table defines title and content as `NOT NULL`.
- API reference: `docs/apis/post/post-posts.md`.

## Last Reviewed

2026-09-26, by Vegalife backend team

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

Post edits accept a non-empty subset of `title`, `content`, `featuredImageUrl`, `videoUrl`, `mediaId`, `categoryIds`, and `publish`. Only supplied, non-null values are applied; omitted and null values leave existing data unchanged. A supplied title must be non-blank and no longer than 255 characters, and supplied content must not be blank. The post `type` is fixed at creation and any different `type` is rejected (BR-CONTENT-002); video fields are rejected for blog posts. `categoryIds` replaces the category set and may only reference active categories (BR-CONTENT-004). `publish: true` requests publication and re-queues the post for content filtering (BR-FILTER-005): the post only becomes `published` once the filter returns `PASSED` (BR-FILTER-007). `publish: false` withdraws the post to a private draft immediately, clears `publish_intent`, and never triggers filtering. A content change (title, content, or media) to a post whose status is `published` or `flagged` re-queues it for filtering (BR-FILTER-005). A post that is or becomes published must keep at least one category and, for video, a video file or link (BR-CONTENT-003). Only an Administrator may change the state of a `hidden` post. Ownership and view count are not editable.

## Rationale

Partial updates let clients change one field without resending the full post and prevent omitted fields from being overwritten with empty values.

## Scope & Exceptions

Applies to `PATCH /api/posts/{postId}`. Removing a featured image by sending null is not supported by this endpoint.

## Enforcement

- Request DTO: `PostUpdateRequest` validates supplied text (null allowed, blank rejected) and rejects an empty update.
- Service: `PostService.updatePost()` applies only non-null request values and enforces the type, category and publish rules.
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

# Business Rule: Only Administrators Hide Posts, and It Is Logged

## Rule ID

`BR-POST-009`

## Status

Active

## Statement

Only an Administrator may hide a post or lift a hide (BR-ADMIN-002, BR-ADMIN-004). Hiding sets status `hidden` and clears the publication timestamp; lifting returns the post to a private draft. While a post is hidden, only an Administrator may change its publish state. Every hide and unhide is recorded in `moderation_log`.

## Rationale

Moderation is an administrator power; recording each action keeps decisions reviewable, and returning to draft forces the normal publish checks before content becomes public again.

## Scope & Exceptions

Applies to `PATCH /api/posts/{postId}/visibility`. Owners withdraw their own posts with `publish: false`.

## Enforcement

- Security: `SecurityConfig` requires `ROLE_ADMIN` for the route.
- Service: `PostService.updateVisibility()` changes status and writes the log; `applyPublishState()` blocks non-admins on hidden posts.
- API reference: `docs/apis/post/patch-posts-postid-visibility.md`.

## Last Reviewed

2026-09-29, by Vegalife backend team

---

# Business Rule: Only Published Posts Are Public

## Rule ID

`BR-POST-010`

## Status

Active

## Statement

When a member's posts are listed, guests and other members see only `published`, non-deleted posts. Drafts and other non-public states (`created`, `processed`, `unpublished`, `hidden`, `flagged`) are visible only to the post's creator and Administrators (BR-CONTENT-003).

## Rationale

Published content is open to everyone (BR-PUBLIC-001, BR-SEARCH-002), while unpublished or moderated content must stay private to its owner and administrators.

## Scope & Exceptions

Applies to `GET /api/users/{userId}/posts`. `GET /api/posts` is the caller's own list and already includes all non-deleted statuses.

## Enforcement

- Security: `SecurityConfig` permits unauthenticated `GET /api/users/*/posts`.
- Service: `PostService.listPostsOfUser()` selects the published-only query unless the viewer is the owner or an Administrator.
- API reference: `docs/apis/post/get-users-userid-posts.md`.

## Last Reviewed

2026-09-30, by Vegalife backend team

---

# Business Rule: Semantic Relevance Uses Three Bands and Configured Thresholds

## Rule ID

`BR-FILTER-004`

## Status

Active

## Statement

The content is embedded and compared by cosine similarity with two precomputed centroids — one built from on-topic seed texts and one from off-topic seed texts, both Vietnamese and English. The relevance score is the normalized margin between the two similarities, `score = (cos(text, onTopic) - cos(text, offTopic) + 1) / 2`, clamped to `[0, 1]`. The score maps to exactly one verdict: `score >= app.filter.accept-threshold` is `PASSED`; `score < app.filter.reject-threshold` is `REJECTED`; anything between the two is `NEEDS_REVIEW`. Defaults are `0.75` and `0.45`. The filter state is stored in `post.flag`, which is separate from `post.status`; `flag` is NULL only when the post has never been filtered.

## Rationale

A three-band outcome separates confident failures from content that is merely ambiguous, so uncertain posts are held for a human instead of being published or silently rejected. Thresholds are configuration so tuning does not require a redeploy of logic.

## Scope & Exceptions

Applies to every filter run. There is no `NOT_FILTERED` value: NULL means never filtered (drafts and pre-existing rows), and every run ends in one of the three verdicts. The centroid model and regeneration procedure are recorded in `docs/adrs/007-post-content-filtering.md`.

## Enforcement

- `EmbeddingRelevanceScorer` reads both thresholds from `app.filter.*` and returns the band; `ContentFilterService` turns it into the verdict and reasons.
- `post.flag VARCHAR(16)` is constrained to `PENDING`/`PASSED`/`REJECTED`/`NEEDS_REVIEW` or NULL (migration V19).
- Unit tests cover both bands and the review band at the exact threshold boundaries.

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

A post is filtered only while it has `publish_intent = true`. That flag is set by creating with `publish: true` or editing with `publish: true`, and cleared by withdrawing with `publish: false`. While `publish_intent` is true, a content change (title, content, or media) on a post whose status is `published` or `flagged` re-queues it for filtering. A draft — created or edited without publish intent, `flag` NULL — is never filtered, and unpublishing never triggers a filter run.

## Rationale

Only content that is (or is about to be) public needs screening; screening drafts would burn embedding calls on work in progress and expose unfinished content to automated judgement.

## Scope & Exceptions

Applies to `POST /api/posts` and `PATCH /api/posts/{postId}`. Ownership, category, and type validation (BR-POST-006, BR-POST-007, BR-CONTENT-002/003/004) are unchanged and happen before any queueing decision. Withdrawing stays immediate (BR-CONTENT-003).

## Enforcement

- Service: `PostService.createPost()` / `updatePost()` decide whether to enqueue based on `publish_intent` and the content dirty check, and enqueue in the caller's transaction.
- API references: `docs/apis/post/post-posts.md`, `docs/apis/post/patch-posts-postid.md`.

## Last Reviewed

2026-09-30, by Vegalife backend team

---

# Business Rule: Filtering Is Asynchronous Through the Outbound Queue

## Rule ID

`BR-FILTER-006`

## Status

Active

## Statement

Filtering never runs inside the HTTP request. Enqueueing writes an `outbound_message` row with channel `CONTENT_FILTER` in the same transaction that saves the post, sets `post.flag = PENDING` and `post.filter_queued_at = now`, and returns; a scheduled drainer later claims the message and runs the filter. There is no synchronous or real-time filtering path, and a failed filter run is retried by the queue's existing retry/deferred handling rather than failing the user's request.

## Rationale

The embedding call is an external network dependency with unbounded latency; keeping it off the request thread keeps create/edit fast and makes the post durable even if the model or provider is down.

## Scope & Exceptions

Applies to every filter trigger (BR-FILTER-005). The queue is the ADR-005 PostgreSQL outbox with a new `CONTENT_FILTER` channel — no new broker. A message that exhausts retries or is lost is covered by the 24-hour sweep (BR-FILTER-009), so `PENDING` is never permanent.

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

When a filter run ends with `PASSED`, the post's `flag` becomes `PASSED` and its status becomes (or stays) `published` whenever publish intent was requested or the post is already published; `publishedAt` is set if it was null. A draft that was never submitted for publication keeps its existing status — a `PASSED` verdict can only arrive for a post with publish intent (BR-FILTER-005).

## Rationale

Passing the filter is the gate that makes publication legitimate; the transition is driven by the verdict, not by the client's request, so the API response and the public visibility can never disagree about a filtered post.

## Scope & Exceptions

Applies to every `PASSED` verdict, whether it comes from a create, an edit, or a re-run of a flagged post (a passing edit returns a flagged post to `published`). Administrative hide/unhide (BR-POST-009) is unaffected.

## Enforcement

- `ContentFilterOutboundAdapter` applies the verdict: sets `flag`, moves `status` to `published`, and stamps `publishedAt` when it is null.
- API references: `docs/apis/post/post-posts.md`, `docs/apis/post/patch-posts-postid.md`.

## Last Reviewed

2026-09-30, by Vegalife backend team

---

# Business Rule: A Rejected or Uncertain Filter Flags the Post

## Rule ID

`BR-FILTER-008`

## Status

Active

## Statement

When a filter run ends with `REJECTED` or `NEEDS_REVIEW`, the post's `flag` is set to that value and its status becomes `flagged`, which is not publicly visible (BR-POST-010). A `REJECTED` run logs a WARN carrying the out-of-band score; a `NEEDS_REVIEW` run logs a WARN recording that the score fell between the two thresholds. `NEEDS_REVIEW` is a data state only this sprint — no admin endpoint changes it. There is no `prev_status` column: the previous status is not tracked.

## Rationale

Failing content must not stay public, but it must not be destroyed either — `flagged` keeps the post intact for its owner and for later review, and distinguishing rejection from uncertainty tells a future reviewer how much scrutiny to apply.

## Scope & Exceptions

Applies to every `REJECTED` / `NEEDS_REVIEW` verdict and to the stale-pending sweep (BR-FILTER-009), which produces `NEEDS_REVIEW`. The owner can edit the post again; a passing edit returns it to `published` (BR-FILTER-007).

## Enforcement

- `ContentFilterOutboundAdapter` sets flag and status and writes the WARN log.
- `post.status` accepts `flagged` from migration V19 on.

## Last Reviewed

2026-09-30, by Vegalife backend team

---

---

# Business Rule: Posts Stuck Pending Are Flagged After 24 Hours

## Rule ID

`BR-FILTER-009`

## Status

Active

## Statement

A scheduled job finds every post whose `flag = PENDING` and whose `filter_queued_at` is older than `app.filter.sweep-max-age` (default 24 hours) and moves it to `flag = NEEDS_REVIEW`, `status = flagged`, logging at ERROR. Posts with a fresh `PENDING` or a NULL `flag` are never touched.

## Rationale

The outbox retries and then defers, but a message can still be lost (deleted row, exhausted retries, prolonged provider outage). Without a bound, such a post would stay `PENDING` forever — invisible to the owner's publish intent and to any reviewer.

## Scope & Exceptions

Applies only to `PENDING` posts; it never re-runs a completed verdict and never touches drafts (NULL flag). 24 hours is the default and is configuration, not a constant.

## Enforcement

- `PendingFilterSweepJob` (scheduled, `OutboundRetentionJob` pattern) performs the transition.
- Unit tests cover: stale PENDING swept, fresh PENDING untouched, NULL flag untouched.

## Last Reviewed

2026-09-30, by Vegalife backend team

---
