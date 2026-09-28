# Business Rules: Posts

## Rule Index

| Rule ID | Title | Status | Last Reviewed |
|---------|-------|--------|---------------|
| BR-POST-001 | Post Ownership Comes from Authentication | Active | 2026-09-26 |
| BR-POST-002 | Users List Only Their Non-Deleted Posts | Active | 2026-09-26 |
| BR-POST-003 | User Post Lists Use Bounded Newest-First Pagination | Active | 2026-09-26 |
| BR-POST-004 | New Posts Start in the Created State | Active | 2026-09-26 |
| BR-POST-005 | Post Title and Content Are Required | Active | 2026-09-26 |
| BR-POST-006 | Users Edit Only Their Own Non-Deleted Posts | Active | 2026-09-27 |
| BR-POST-007 | Post Edits Change Only Supplied Fields | Active | 2026-09-27 |
| BR-POST-008 | Posts Are Soft-Deleted by Owner or Administrator | Active | 2026-09-29 |
| BR-POST-009 | Only Administrators Hide Posts, and It Is Logged | Active | 2026-09-29 |
| BR-POST-010 | Only Published Posts Are Public | Active | 2026-09-29 |

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

When a user creates a post, the system sets its view count to `0`. The post starts as a private draft (`created`) unless the client asks to publish (`publish: true`), which is accepted only when the post has all information required for its type and at least one active category (BR-CONTENT-002/003/004); it is then `published` with `publishedAt` set. Status and view count cannot otherwise be set by the client.

## Rationale

System-controlled initial values keep post lifecycle state consistent and leave publication decisions to later processing.

## Scope & Exceptions

Applies to `POST /api/posts`. Later semantic filtering or review workflows may transition the post to another valid status.

## Enforcement

- Service: `PostService.createPost()` sets status and view count and does not assign a publication timestamp.
- Request DTO: `PostCreateRequest` does not expose lifecycle fields.
- API reference: `docs/apis/post/post-posts.md`.

## Last Reviewed

2026-09-26, by Vegalife backend team

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

Post edits accept a non-empty subset of `title`, `content`, `featuredImageUrl`, `videoUrl`, `mediaId`, `categoryIds`, and `publish`. Only supplied, non-null values are applied; omitted and null values leave existing data unchanged. A supplied title must be non-blank and no longer than 255 characters, and supplied content must not be blank. The post `type` is fixed at creation and any different `type` is rejected (BR-CONTENT-002); video fields are rejected for blog posts. `categoryIds` replaces the category set and may only reference active categories (BR-CONTENT-004). `publish: true` publishes and `publish: false` returns the post to a private draft; a post that is or becomes published must keep at least one category and, for video, a video file or link (BR-CONTENT-003). Only an Administrator may change the state of a `hidden` post. Ownership and view count are not editable.

## Rationale

Partial updates let clients change one field without resending the full post and prevent omitted fields from being overwritten with empty values.

## Scope & Exceptions

Applies to `PATCH /api/posts/{postId}`. Removing a featured image by sending null is not supported by this endpoint.

## Enforcement

- Request DTO: `PostUpdateRequest` validates supplied text (null allowed, blank rejected) and rejects an empty update.
- Service: `PostService.updatePost()` applies only non-null request values and enforces the type, category and publish rules.
- API reference: `docs/apis/post/patch-posts-postid.md`.

## Last Reviewed

2026-09-27, by Vegalife backend team

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

When a member's posts are listed, guests and other members see only `published`, non-deleted posts. Drafts and other non-public states (`created`, `processed`, `unpublished`, `hidden`) are visible only to the post's creator and Administrators (BR-CONTENT-003).

## Rationale

Published content is open to everyone (BR-PUBLIC-001, BR-SEARCH-002), while unpublished or moderated content must stay private to its owner and administrators.

## Scope & Exceptions

Applies to `GET /api/users/{userId}/posts`. `GET /api/posts` is the caller's own list and already includes all non-deleted statuses.

## Enforcement

- Security: `SecurityConfig` permits unauthenticated `GET /api/users/*/posts`.
- Service: `PostService.listPostsOfUser()` selects the published-only query unless the viewer is the owner or an Administrator.
- API reference: `docs/apis/post/get-users-userid-posts.md`.

## Last Reviewed

2026-09-29, by Vegalife backend team

---
