# Feature Spec: Create a User Post

## Status

In progress

## Author / owner

Vegalife backend team

## Summary

Allow an authenticated user to create a post with a title, content, and optional featured image.

## Problem / motivation

The existing post table and post-list endpoint let users store and retrieve posts, but the backend did not provide an API to create a post through the application.

## Goals

- Let a signed-in user create a post owned by their account.
- Initialize new posts consistently so the content filtering pipeline (`docs/feats/post-content-filtering.md`) can process them.

## Non-goals

- Blocking this endpoint on semantic filtering: filtering runs asynchronously after creation (BR-FILTER-006).
- Publishing a post that has not received a `PASSED` filter verdict (BR-POST-004). Immediate publication is replaced by an asynchronous filter queue.
- Editing or deleting posts.
- Adding media attachments, recipes, or locations to the request.

## Requirements

### Functional Requirements

- [x] FR-001: `POST /api/posts` requires a valid JWT and assigns the post to the user identified by that JWT.
- [x] FR-002: The request requires a non-blank title of at most 255 characters and non-blank content; a featured image URL is optional.
- [x] FR-003: The client cannot choose the post owner, status, view count, or timestamps.
- [x] FR-004: A new post is saved with status `created`, view count `0`, and no publication timestamp. With `publish: true` it is saved with `flag: PENDING` and a `CONTENT_FILTER` message enqueued; it stays `created` until filtering returns `PASSED` (BR-POST-004).
- [x] FR-005: The endpoint returns `201 Created` with an `ApiResponse` containing a post DTO, including the `flag` field (`null` when the post was not queued).

### Non-Functional Requirements

- [x] NFR-SEC-001: Ownership is derived from authentication rather than trusted request data.
- [x] NFR-SEC-002: Required text fields are validated on the server.
- [x] NFR-MAINT-001: The implementation follows the project's controller-service-repository layering and exposes DTOs, not entities.

## Design overview

`PostController` validates the request and extracts the UUID principal. `PostService` loads that user, initializes the new `Post`, and saves it through `PostRepository`. `PostMapper` maps the request to the entity and the saved entity to the response DTO. With `publish: true`, the service sets `flag: PENDING` and enqueues a `CONTENT_FILTER` message on the ADR-005 outbox instead of publishing (BR-POST-004). The `flag` column is added by migration `V19__add_post_filtering.sql`.

## Success metrics

Before merge, 100% of the automated acceptance scenarios for creation, owner assignment, initial state, validation, and authentication must pass. Production usage metrics are not available yet.

## Acceptance criteria

**As an** authenticated user, **I want to** create a post, **so that** my content is saved under my account.

- [x] Given a valid JWT and valid body, when the user creates a post, then the API returns `201 Created` and persists the post for that user.
- [x] Given a valid request, when the post is saved, then its status is `created`, its view count is `0`, and `publishedAt` is null.
- [ ] Given `publish: true` on an otherwise valid request, when the post is saved, then its status stays `created` with `flag: PENDING`, and it becomes `published` only after the async filter job returns `PASSED` (BR-POST-004).
- [ ] Given `publish: true` without any category, when the user submits the request, then the API returns `400 Bad Request` and no post is created.
- [x] Given a blank title or content, when the user submits the request, then the API returns `400 Bad Request`.
- [x] Given no valid JWT, when the user submits the request, then the API returns `401 Unauthorized`.

## Risks / open questions

- Publication of a `publish: true` post now depends on the asynchronous filter job (BR-FILTER-006): clients must treat `created` + `flag: PENDING` as "queued", not "failed". The filter behavior itself is specified in `docs/feats/post-content-filtering.md`.

## Related

- API reference: `docs/apis/post/post-posts.md`
- Content filtering feature: `docs/feats/post-content-filtering.md`
- Business rules: `docs/brs/posts.md`
