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

When a user creates a post, the system sets its status to `created`, its view count to `0`, and its publication timestamp to null. The client cannot set these fields.

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

An authenticated user may edit a post only when the post is owned by that user and `deleted_at` is null. A missing post, soft-deleted post, or post owned by another user is reported as not found.

## Rationale

Post ownership is private user content. Applying the same owner boundary to edits prevents cross-account changes and avoids disclosing whether another user's post exists.

## Scope & Exceptions

Applies to `PATCH /api/posts/{postId}`. Administrative moderation edits are outside this rule.

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

Post edits accept a non-empty subset of `title`, `content`, and `featuredImageUrl`. Only supplied, non-null values are applied; omitted and null values leave existing data unchanged. A supplied title must be non-blank and no longer than 255 characters, and supplied content must not be blank. Post lifecycle and ownership fields are system-controlled and are not editable through this endpoint.

## Rationale

Partial updates let clients change one field without resending the full post and prevent omitted fields from being overwritten with empty values.

## Scope & Exceptions

Applies to `PATCH /api/posts/{postId}`. Removing a featured image by sending null is not supported by this endpoint.

## Enforcement

- Request DTO: `PostUpdateRequest` validates supplied text and rejects an empty update.
- Service: `PostService.updatePost()` applies only non-null request values.
- API reference: `docs/apis/post/patch-posts-postid.md`.

## Last Reviewed

2026-09-27, by Vegalife backend team

---
