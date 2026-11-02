# Feature Spec: View Post Detail API (Get Post by ID)

## Status

Implemented

## Author / owner

Vegalife backend team

## Summary

Add `GET /api/posts/{postId}` so any caller can read a single non-deleted post by id, regardless of who wrote it or its status — closing the gap where a post's detail/share view could only be reached by listing and filtering, and another user's post was unreachable at all.

## Problem / motivation

The frontend post detail page (and shared links) cannot fetch a single post today: the API only exposes `GET /api/posts` (the caller's own posts) and `GET /api/users/{userId}/posts` (a profile list). Reading one post by id means fetching lists and filtering client-side, and someone else's post is unreachable entirely.

## Goals

- Let any caller — authenticated user or guest — fetch any non-deleted post by id, in any status, owned by anyone.
- Return the same `PostListResponse` shape the list endpoints already use.
- Exclude only soft-deleted posts; the endpoint is a direct-by-id read, so it does not gate on the `published` status that controls the feed.

## Non-goals

- Gating the response on the post's status or on ownership — only soft-deletion excludes a post.
- View-count increment, caching, or ETags.
- Comments, reactions, or "related posts" in the payload.
- A global feed or listing other users' posts (that's `GET /api/posts/feed`, a separate feature).

> Note: the original scope (issue #100) required a valid JWT and listed public access as a non-goal. That decision was reversed so a shared/linked post can be opened by a signed-out visitor; this endpoint now follows the same public-read pattern as `GET /api/posts/feed` and `GET /api/users/{userId}/posts`.

## Requirements

### Functional Requirements

- [x] FR-001: `GET /api/posts/{postId}` is public (`permitAll`); no JWT is required. A supplied-but-invalid or expired token is still rejected with `401`.
- [x] FR-002: Given an existing, non-deleted post, the endpoint returns `200 OK` with `ApiResponse` wrapping the same `PostListResponse` the list endpoints use, regardless of who the caller is or the post's status.
- [x] FR-003: Given a soft-deleted post or an unknown/malformed id, the endpoint returns `404 Post not found`.

### Non-Functional Requirements

- [x] NFR-SEC-001: Visibility is derived from the post's own `deletedAt` only, never from the caller's identity, ownership, or the post's status; a soft-deleted or unknown post is reported as the same `404` so no post existence leaks.
- [x] NFR-MAINT-001: Follows the existing controller-service-repository structure and reuses `PostListResponse`/`PostMapper` rather than introducing a parallel DTO.

## Design overview

`PostController.getPost()` accepts the path `postId` and delegates to `PostService.getPost(postId)`, which calls `PostRepository.findDetailById(id)` (a `where p.id = :id and p.deletedAt is null` query — no status predicate) and maps a missing result to the existing `ResourceNotFoundException("Post not found")` — the same not-found-over-leak pattern `PostService` already uses elsewhere (e.g. `findManageablePost`). The status-scoped `findByIdAndStatusAndDeletedAtIsNull` is retained for callers that gate on publication (comment creation). The route is opened to guests by a `permitAll` rule in `SecurityConfig` for `GET /api/posts/*` (a single path segment, so it matches `feed` and `{postId}` but never the auth-required `GET /api/posts` list); the controller no longer carries a class-level bearer requirement — each protected operation declares `@SecurityRequirement` itself, so `getPost` (and `listFeed`) are documented as public.

## Success metrics

All automated acceptance scenarios (any-status 200, soft-deleted/unknown as 404, invalid-token 401) pass before merge.

## Acceptance criteria

**As a** visitor (guest or member), **I want to** open a single post by its id, **so that** I can view a post's detail page or follow a shared link regardless of who wrote it.

- [x] Given any caller, when they request an existing, non-deleted post by id, then `200 OK` is returned with the post's `PostListResponse`, whatever its status.
- [x] Given any caller, when the post belongs to someone else or is not published, then it is returned — visibility depends only on soft-deletion, never on ownership or status.
- [x] Given any caller, when the target post is soft-deleted or the id is unknown, then `404 Post not found` is returned.
- [x] Given no authentication, when the endpoint is called, then the request is served like any other caller (no `401`); only an invalid/expired token is rejected.

## Risks / open questions

- None — the shape, visibility rule, and non-goals were fixed by the driving issue; no open design decision remained.

---

## Related

- API reference: `docs/apis/post/get-posts-postid.md`
- Business rules: `docs/brs/posts.md` (BR-POST-011)
- List responses use the same shape: `docs/apis/post/get-posts.md`
- Driving issue: https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/100
