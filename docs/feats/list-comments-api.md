# Feature Spec: List Comments API

## Status

In progress

## Author / owner

Backend team; driving issue: [#44](https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/44)

## Summary

Return a post's comment thread — top-level comments paginated, each with its full nested reply tree — so a frontend can render the discussion under a post. This is the public-read side of `BR-COMMENT-001` that issues #41-#43 deliberately left unimplemented.

## Problem / motivation

Comments can be created, edited, and deleted, but there is still no endpoint to read them back as a thread. `docs/feats/create-comments-api.md` and `docs/feats/delete-comments-api.md` both flagged this as an open dependency: readers need a way to see nested replies and to see soft-deleted comments rendered as placeholders rather than exposed or simply vanishing (which would orphan live replies visually).

## Goals

- Let anyone (no authentication required, per `BR-COMMENT-001`) fetch a published post's comments as a nested thread.
- Paginate top-level comments so a post with many root-level comments doesn't return unbounded data.
- Render a soft-deleted comment as a `"[deleted]"` placeholder, with no author identity, while keeping its replies attached at the correct place in the tree.
- Include basic author info (`userId`, `username`, `avatarUrl`) on each live comment.

## Non-goals

- Real-time updates (WebSocket) — issue #44 excludes this explicitly.
- Comment voting — handled by a separate, already-tracked feature (`vote` table/API).
- Paginating replies within a thread. A root comment's full reply subtree is returned in one response; only the set of root comments is paginated. Expected thread depth/width for this platform does not warrant per-level pagination, and issue #44 only asks for pagination of "deep threads" at the root level.
- Edit history or any other field beyond what `CommentResponse`-family endpoints already expose (`edited`, `content`, timestamps).

## Requirements

### Functional Requirements

- [ ] FR-001: `GET /api/posts/{postId}/comments` requires no authentication and returns the comment thread for the post identified by `postId`.
- [ ] FR-002: The target post must exist, be non-deleted, and have status `published`; otherwise the API returns `404` (same post-visibility rule `createComment` already uses).
- [ ] FR-003: Top-level comments (`parentId IS NULL`) are returned paginated, oldest first, using the same `page`/`size` query parameters and defaults (`page=0`, `size=20`, max `size=100`) as other list endpoints (`PostListRequest`).
- [ ] FR-004: Each returned comment includes its full reply subtree, recursively, each reply carrying the same shape as its parent.
- [ ] FR-005: A soft-deleted comment (`deletedAt != null`) is included in the tree (so its replies keep their place) but is rendered with `content: "[deleted]"`, `deleted: true`, and `userId`/`username`/`avatarUrl` all `null`.
- [ ] FR-006: A live (non-deleted) comment includes `userId`, `username`, and `avatarUrl` (`null` if the author has no profile row yet, per `BR-PROFILE-003`).
- [ ] FR-007: `edited` reflects the comment's own edited flag (`BR-COMMENT-002`) regardless of deletion.

### Non-Functional Requirements

- [ ] NFR-PERF-001: Fetches the post's full comment set in one query (`CommentRepository.findByPostIdOrderByCreatedAtAsc`) and builds the tree and author lookups in memory, rather than one query per node. Acceptable for this platform's expected comment volumes (issue marked Difficulty: Simple); revisit if thread sizes grow large enough to matter.
- [ ] NFR-SEC-001: The endpoint is explicitly allowlisted as `permitAll` in `SecurityConfig` (`GET /api/posts/*/comments`) per ADR-009's model — it is not protected by `@PreAuthorize`, and `EndpointAuthorizationGuardTest` enforces that every handler is one or the other.

## Design overview

Add `GET /api/posts/{postId}/comments` to the existing `CommentController` (no `@PreAuthorize`, not covered by the controller's bearer `@SecurityRequirement` since that moved to the individual authenticated handlers). `CommentService.getComments` loads the published post (404 otherwise), fetches every comment for the post in one query, partitions it into roots and a `parentId -> children` map, paginates the roots in memory, bulk-loads usernames (`UserRepository.findAllById`) and avatar URLs (`UserProfileRepository.findByUserIdIn`, new method) for every live author appearing in the page's subtrees, and recursively maps each root (and its descendants) into a new `CommentThreadResponse` DTO.

## Success metrics

All acceptance scenarios below pass in automated tests before merge.

## Acceptance criteria

**As** any visitor, **I want to** read a post's comments as a nested thread, **so that** I can follow the discussion including replies, without needing an account.

- [ ] Given a published post with top-level comments and replies, when the thread is requested, then the response returns root comments with their replies nested underneath, each carrying `userId`, `username`, `avatarUrl`, `content`, `edited`, `deleted`, and timestamps.
- [ ] Given more root comments than fit on one page, when a page is requested, then only that page's root comments are returned, paginated the same way other list endpoints are (`page`, `size`, `totalElements`, `totalPages`, `first`, `last`).
- [ ] Given a soft-deleted comment with live replies, when the thread is requested, then the deleted comment appears with `content: "[deleted]"`, `deleted: true`, no author fields, and its replies still nested underneath it.
- [ ] Given no `Authorization` header at all, when the thread is requested, then it still succeeds (`200 OK`) — this endpoint requires no authentication.
- [ ] Given a post that does not exist, is soft-deleted, or is not published, when the thread is requested, then `404 Not Found` is returned.

## Risks / open questions

- None outstanding; this closes the open dependency noted in `docs/feats/create-comments-api.md` and `docs/feats/delete-comments-api.md`.

---

## Related

- API reference: `docs/apis/post/get-posts-postid-comments.md`
- Business rule: `docs/brs/comments.md` (`BR-COMMENT-001`, updated to mark public reading as implemented)
- Prior features: `docs/feats/create-comments-api.md` (#41), `docs/feats/edit-comments-api.md` (#42), `docs/feats/delete-comments-api.md` (#43)
