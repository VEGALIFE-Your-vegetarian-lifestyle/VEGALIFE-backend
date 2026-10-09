# Feature Spec: View Post Detail API (Get Post by ID)

## Status

Implemented

## Author / owner

Vegalife backend team

## Summary

Add `GET /api/posts/{postId}` so any authenticated user can read a single published post by id, regardless of who wrote it — closing the gap where a post's detail/share view could only be reached by listing and filtering, and another user's post was unreachable at all.

## Problem / motivation

The frontend post detail page (and shared links) cannot fetch a single post today: the API only exposes `GET /api/posts` (the caller's own posts) and `GET /api/users/{userId}/posts` (a profile list). Reading one post by id means fetching lists and filtering client-side, and someone else's post is unreachable entirely.

## Goals

- Let any caller — authenticated user or guest — fetch any `published`, non-deleted post by id, owned by anyone.
- Return the same `PostListResponse` shape the list endpoints already use.
- Keep visibility rules consistent with the rest of the post API: non-public states are never exposed through this endpoint, not even to their owner.

## Non-goals

- Owner/admin draft preview (only `published` posts are revealed by this endpoint).
- View-count increment, caching, or ETags.
- Comments, reactions, or "related posts" in the payload.
- A global feed or listing other users' posts (that's `GET /api/posts/feed`, a separate feature).

> Note: the original scope (issue #100) required a valid JWT and listed public access as a non-goal. That decision was reversed so a shared/linked post can be opened by a signed-out visitor; this endpoint now follows the same public-read pattern as `GET /api/posts/feed` and `GET /api/users/{userId}/posts`.

## Requirements

### Functional Requirements

- [x] FR-001: `GET /api/posts/{postId}` is public (`permitAll`); no JWT is required. A supplied-but-invalid or expired token is still rejected with `401`.
- [x] FR-002: Given an existing, non-deleted, `published` post, the endpoint returns `200 OK` with `ApiResponse` wrapping the same `PostListResponse` the list endpoints use, regardless of who the authenticated caller is.
- [x] FR-003: Given a soft-deleted post, a post whose status is not `published` (`created`/`processed`/`unpublished`/`hidden`/`flagged`), or an unknown/malformed id, the endpoint returns `404 Post not found` — including when the caller is the post's own owner and the post is not yet published.

### Non-Functional Requirements

- [x] NFR-SEC-001: Visibility is derived from the post's own `status`/`deletedAt`, never from the caller's identity or ownership; no post data leaks through a different status code or response shape for a non-visible post.
- [x] NFR-MAINT-001: Follows the existing controller-service-repository structure and reuses `PostListResponse`/`PostMapper` rather than introducing a parallel DTO.

## Design overview

`PostController.getPost()` accepts the path `postId` and delegates to `PostService.getPost(postId)`, which calls a new repository method `PostRepository.findByIdAndStatusAndDeletedAtIsNull(id, Post.Status.published)` and maps a missing result to the existing `ResourceNotFoundException("Post not found")` — the same not-found-over-leak pattern `PostService` already uses elsewhere (e.g. `findManageablePost`). The route is opened to guests by a `permitAll` rule in `SecurityConfig` for `GET /api/posts/*` (a single path segment, so it matches `feed` and `{postId}` but never the auth-required `GET /api/posts` list); the controller no longer carries a class-level bearer requirement — each protected operation declares `@SecurityRequirement` itself, so `getPost` (and `listFeed`) are documented as public.

## Success metrics

All automated acceptance scenarios (ownership-independent 200, every non-visible case as 404, unauthenticated 401) pass before merge.

## Acceptance criteria

**As a** visitor (guest or member), **I want to** open a single post by its id, **so that** I can view a post's detail page or follow a shared link regardless of who wrote it.

- [x] Given any caller, when they request an existing, non-deleted, published post by id, then `200 OK` is returned with the post's `PostListResponse`.
- [x] Given any caller, when the post belongs to someone else but is published, then it is returned — visibility depends only on published status, never on ownership.
- [x] Given any caller, when the target post is not published (or soft-deleted, or the id is unknown), then `404 Post not found` is returned.
- [x] Given no authentication, when the endpoint is called, then the request is served like any other caller (no `401`); only an invalid/expired token is rejected.

## Risks / open questions

- None — the shape, visibility rule, and non-goals were fixed by the driving issue; no open design decision remained.

---

## Related

- API reference: `docs/apis/post/get-posts-postid.md`
- Business rules: `docs/brs/posts.md` (BR-POST-011)
- List responses use the same shape: `docs/apis/post/get-posts.md`
- Driving issue: https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/100
