# Feature Spec: Edit a User Post

## Status

In progress

## Author / owner

Vegalife backend team

## Summary

Allow the owner (or an Administrator) to partially edit a post: title, content, image, video, categories, and draft/published state, following BR-CONTENT-001 to 004 and BR-ADMIN-002.

## Problem / motivation

Users can create and list their posts, but currently cannot correct or update content after creation.

## Goals

- Let a signed-in user edit a non-deleted post owned by their account.
- Allow a request to change only the fields the user supplies.
- Return the updated post using the existing post response DTO.

## Non-goals

- Running semantic filtering inside this endpoint: an edit that changes content only re-queues the async filter job (BR-POST-007); see `docs/feats/post-content-filtering.md`.
- Editing posts owned by another user, except by an Administrator (BR-CONTENT-001, logged per BR-ADMIN-002).
- Deleting posts, restoring soft-deleted posts, or changing ownership.
- Clearing an image by sending `null`; an omitted or null field leaves the current value unchanged.

## Requirements

### Functional Requirements

- [ ] FR-001: `PATCH /api/posts/{postId}` requires a valid JWT and identifies the caller from the JWT principal.
- [ ] FR-002: The endpoint updates only the authenticated user's non-deleted post; missing, soft-deleted, and other users' posts return `404 Not Found`.
- [ ] FR-003: The request may include any non-empty subset of `title`, `content`, and `featuredImageUrl`; omitted or null fields remain unchanged.
- [ ] FR-004: A supplied title must not be blank and must be at most 255 characters; supplied content must not be blank.
- [ ] FR-005: A request with no updatable non-null fields returns `400 Bad Request`.
- [ ] FR-006: A successful edit returns `200 OK` with the updated post response; post ownership, view count, publication timestamp, and creation timestamp are not changed by the request.
- [ ] FR-007: A title or content change to a published or flagged post re-queues semantic filtering: `flag` returns to `PENDING` (BR-POST-007); a failing verdict moves the post to status `flagged` (BR-FILTER-008) and a passing one returns it to `published` (BR-FILTER-007). `publish: true` on a post without a `PASSED` verdict is publish intent only (BR-POST-004).

### Non-Functional Requirements

- [ ] NFR-SEC-001: The post ID and user ID in the request cannot override ownership; authorization is checked against the authenticated principal.
- [ ] NFR-MAINT-001: The change follows the existing controller-service-repository structure and exposes DTOs rather than entities.

## Design overview

`PostController` accepts the post UUID, authenticated UUID principal, and validated partial-update DTO. `PostService` loads the post by both post ID and owner ID while requiring `deletedAt` to be null, applies only supplied values, saves the entity, and maps it to `PostListResponse`. `publish: true` and content changes set publish intent / re-queue filtering through the ADR-005 outbox instead of publishing directly (BR-POST-004, BR-POST-007). No column is written by this endpoint that migration `V19__add_post_filtering.sql` does not already provide (`flag` is read into the response and written by the filter job).

## Success metrics

All automated acceptance scenarios for partial updates, validation, authentication, ownership, and soft deletion pass before merge.

## Acceptance criteria

**As an** authenticated user, **I want to** edit my own post, **so that** I can correct or update what I have shared.

- [ ] Given a valid JWT and an owned non-deleted post, when the caller supplies one or more valid editable fields, then the API returns `200 OK` with those fields updated.
- [ ] Given a partial edit, when an editable field is omitted or null, then the existing value for that field remains unchanged.
- [ ] Given a blank supplied title/content, an overlong title, or no non-null editable fields, when the caller submits the request, then the API returns `400 Bad Request` and does not update the post.
- [ ] Given a missing/invalid JWT, when the caller submits the request, then the API returns `401 Unauthorized`.
- [ ] Given a post that does not exist, is soft-deleted, or belongs to another user, when the caller submits the request, then the API returns `404 Not Found` and no post is changed.
- [ ] Given a valid edit, when the post is saved, then ownership, view count, publishedAt, and createdAt retain their existing values; status and flag change only through the publish and filtering rules (BR-POST-004, BR-POST-007).

## Risks / open questions

- This endpoint intentionally treats null the same as an omitted field. Removing a featured image is not supported by this task.

---

## Related

- API reference: `docs/apis/post/patch-posts-postid.md`
- Business rules: `docs/brs/posts.md`
