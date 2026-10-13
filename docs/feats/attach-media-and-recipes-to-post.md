# Feature Spec: Attach Media and Recipes to a Post

## Status

In progress

## Author / owner

Backend team; driving issue: [#137](https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/137)

## Summary

Let an authenticated post owner replace the media and recipe attachments on a post using IDs of resources that already exist.

## Problem / motivation

The `post_media` and `post_recipe` join tables and entity mappings exist, but post create/update currently do not write those associations. Search issue #40 aggregates posts, videos, and recipes and needs posts to carry those relationships.

## Goals

- Add an API to replace a post's complete media attachment set.
- Add an API to replace a post's complete recipe attachment set.
- Return attachment IDs on updated post responses and when reading the post back.

## Non-goals

- Uploading media or creating recipes; those resources must already exist.
- Changing post content, status, publication state, or content-filter jobs.
- Adding media or recipe attachment data to semantic filtering.

## Requirements

### Functional Requirements

- [ ] FR-001: `POST /api/posts/{postId}/media` requires an authenticated user and replaces the media set with exactly the supplied `mediaIds`.
- [ ] FR-002: Every requested media row must exist, be non-deleted, and have status `succeed`; otherwise the request returns `400` without changing attachments.
- [ ] FR-003: `POST /api/posts/{postId}/recipes` requires an authenticated user and replaces the recipe set with exactly the supplied `recipeIds`.
- [ ] FR-004: Every requested recipe row must exist and be non-deleted; otherwise the request returns `400` without changing attachments. Recipe ownership is not restricted.
- [ ] FR-005: A non-admin caller can change attachments only on their own non-deleted post; an unavailable or another user's post returns `404`.
- [ ] FR-006: An empty ID list clears only that attachment type. Other post fields and the other attachment type remain unchanged.
- [ ] FR-007: Both endpoints return the updated post, including `mediaIds` and `recipeIds`; `GET /api/posts/{postId}` also returns the persisted attachment IDs.
- [ ] FR-008: Attachment changes do not alter status, publication state, or enqueue/re-trigger content filtering.

### Non-Functional Requirements

- [ ] NFR-SEC-001: Both write endpoints require a valid JWT; unauthenticated requests return `401`.
- [ ] NFR-MAINT-001: Reuse the existing join tables and layered architecture; no schema migration or dependency is required.

## Design overview

Add two authenticated operations to `PostController` and transactional replacement methods to `PostService`. Validate all requested resources before mutating either association. `Post.media` owns the `post_media` join table; `Recipe.posts` owns `post_recipe`. Expose recipe IDs alongside media IDs in `PostListResponse` so attach responses and post reads show the saved relationship. No content-filter service or outbound queue is invoked.

## Success metrics

All acceptance scenarios pass in unit and API integration tests, including replacement, empty-list clearing, invalid resource rejection, ownership, and read-back.

## Acceptance criteria

**As a** post owner, **I want to** attach existing media or recipes to my post, **so that** the frontend and Search can use those relationships.

- [ ] Given an authenticated owner and valid resource IDs, when either endpoint is called, then the exact supplied set is saved and the updated post is returned.
- [ ] Given an empty list, when either endpoint is called, then only that attachment set is cleared.
- [ ] Given a missing/deleted/unsuccessful media or missing/deleted recipe ID, when the request is called, then it returns `400` and makes no partial update.
- [ ] Given another user's post and a non-admin caller, when attachments are changed, then it returns `404`.
- [ ] Given a successful attachment, when the post is read back, then both attachment ID sets reflect the stored associations and post status/filter state is unchanged.

## Risks / open questions

- Media uploader ownership is deliberately not enforced when attaching media, per BR-MEDIA-002 in `docs/brs/media.md`. Per the issue's acceptance criteria, recipe ownership is also not restricted; any existing non-deleted recipe may be attached.

---

## Related

- API references: [`POST /api/posts/{postId}/media`](../apis/post/post-posts-postid-media.md), [`POST /api/posts/{postId}/recipes`](../apis/post/post-posts-postid-recipes.md)
- Driving issue: [#137](https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/137)
