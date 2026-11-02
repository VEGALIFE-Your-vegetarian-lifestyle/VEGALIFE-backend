# Feature Spec: Create Comments API

## Status

In progress

## Author / owner

Backend team; driving issue: [#41](https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/41)

## Summary

Allow an authenticated user to add a plain-text comment to a published post, either as a top-level comment or as a nested reply.

## Problem / motivation

Users can discuss posts and recipes to build community engagement, but the backend has no API for creating a comment. The `comment` table and entity already support post ownership and parent-child threading; the missing piece is the authenticated create flow.

## Goals

- Let an authenticated user create a top-level comment or reply on a published post.
- Derive the comment author from the authenticated principal and link the comment to the post in the path.
- Return the new comment's ID and `parentId` so the frontend can place it in the correct thread.

## Non-goals

- Public comment listing. Issue #41 covers creation only; BR-COMMENT-001's public-read side needs a separate endpoint/feature.
- Comments on recipes. The current comment table links comments to posts, and issue #41's acceptance criteria specify posts.
- Editing, deleting, or rich-text formatting comments.

## Requirements

### Functional Requirements

- [ ] FR-001: `POST /api/posts/{postId}/comments` requires an authenticated, active user and creates a comment for the post identified by `postId`.
- [ ] FR-002: The request requires non-blank plain-text `content`; an optional `parentId` creates a reply. Missing or null `parentId` creates a top-level comment.
- [ ] FR-003: A reply's parent must be an active comment on the same post. A parent may itself be a reply, allowing nested threads of any depth.
- [ ] FR-004: The target post must exist, be non-deleted, and have status `published`; otherwise the API returns `404`.
- [ ] FR-005: The server derives `userId` from the JWT and `postId` from the path; callers cannot choose another author or post in the body.
- [ ] FR-006: Success returns `201 Created` with the new comment's `id`, `postId`, `parentId`, `userId`, `content`, `createdAt`, and `updatedAt`. A top-level comment has `parentId: null`; for a reply, `parentId` identifies the parent that determines its thread position.

### Non-Functional Requirements

- [ ] NFR-SEC-001: Requests without a valid JWT are rejected with `401 Unauthorized`.
- [ ] NFR-SEC-002: The endpoint does not accept a client-supplied `userId`.
- [ ] NFR-MAINT-001: The implementation reuses the existing `comment` table and layered controller/service/repository structure; no migration or dependency is required.

## Design overview

Add a `CommentController` at `/api/posts/{postId}/comments`, a validated request DTO, and a create response DTO. A comment service loads the target published post, validates an optional active parent comment belongs to that post, and persists the authenticated user's comment through the existing `CommentRepository`. The existing `comment` table already has `post_id`, `user_id`, `parent_id`, and content/timestamp columns. The default security rule already requires authentication for POST routes outside explicitly public matchers.

## Success metrics

All acceptance scenarios for authenticated creation, top-level and nested placement, post/user linkage, validation, invalid parents, and unauthorized access pass in automated tests before merge.

## Acceptance criteria

**As an** authenticated user, **I want to** comment on a post or reply in an existing thread, **so that** I can discuss content with the community.

- [ ] Given an authenticated user and a published post, when they submit non-blank content without a `parentId`, then a top-level comment is saved and `201 Created` returns its ID with `parentId: null`.
- [ ] Given an authenticated user and an active comment on the target post, when they submit non-blank content with that comment's ID as `parentId`, then a reply is saved and returned with its own ID and that `parentId`.
- [ ] Given a valid reply parent that is itself a reply, when another reply is created under it, then the nested thread is preserved.
- [ ] Given a missing or invalid JWT, when the endpoint is called, then `401 Unauthorized` is returned and no comment is created.
- [ ] Given blank content, a missing/non-published/deleted post, or a missing/deleted parent on another post, when the endpoint is called, then it returns the documented client error and creates no comment.

## Risks / open questions

- BR-COMMENT-001 also says anyone, including guests, may read existing comments. Issue #41 does not define a public read endpoint, and the current `GET /api/admin/comments` is Admin-only. Public retrieval remains unimplemented and must be addressed separately before the frontend can load comment threads.

---

## Related

- API reference: `docs/apis/post/post-posts-postid-comments.md`
- Business rule: `docs/brs/comments.md`
- Driving issue: [#41](https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/41)
