# Feature Spec: List the Authenticated User's Posts

## Status

Implemented

## Author / owner

Vegalife backend team

## Summary

Allow an authenticated user to retrieve a paginated list of their own posts, including posts in any current status.

## Problem / motivation

The backend had no endpoint for a signed-in user to retrieve their posts for review or management. Returning a global post feed would expose content outside the scope of this user task.

## Goals

- Let a signed-in user retrieve only posts they own.
- Keep the result bounded and ordered consistently for clients.

## Non-goals

- Listing all users' posts or providing an admin moderation feed.
- Creating, editing, or deleting posts.
- Filtering posts by a requested status.

## Requirements

### Functional Requirements

- [x] FR-001: `GET /api/posts` requires a valid JWT and derives the owner ID from that token.
- [x] FR-002: The response includes only non-soft-deleted posts owned by the authenticated user, regardless of post status.
- [x] FR-003: Results use zero-based pagination, default to page `0` and size `20`, and accept a size from `1` to `100`.
- [x] FR-004: Results are ordered by creation time, newest first.
- [x] FR-005: The response uses the shared `ApiResponse` and `PageResponse` DTOs.

### Non-Functional Requirements

- [x] NFR-SEC-001: The client cannot select another user's posts by supplying a user ID.
- [x] NFR-MAINT-001: Controller, service, repository, and DTO responsibilities follow the existing layered structure.
- [x] NFR-SCALE-001: The endpoint returns a bounded page instead of an unbounded result set.

## Design overview

`PostController` reads the UUID principal from the JWT and passes pagination to `PostService`. `PostService` uses `PostRepository` to query only that user's non-deleted posts and maps them to a response DTO inside the shared page envelope.

## Success metrics

Before merge, 100% of the automated acceptance scenarios for ownership, soft deletion, status inclusion, and pagination must pass. Production usage metrics are not available yet.

## Acceptance criteria

**As an** authenticated user, **I want to** list my own posts, **so that** I can review my content without seeing another user's posts.

- [x] Given a valid JWT, when the user lists posts, then only their non-deleted posts are returned.
- [x] Given posts with different statuses, when the user lists posts, then all non-deleted statuses are included.
- [x] Given no query parameters, when the user lists posts, then page `0` with size `20` is returned, newest first.
- [x] Given no valid JWT, when the user calls the endpoint, then the API returns `401 Unauthorized`.

## Risks / open questions

- Posts in hidden or unpublished states are included for the owner by design; a public feed will require a separate API and visibility rules.

## Related

- API reference: `docs/apis/post/get-posts.md`
- Business rules: `docs/brs/posts.md`
