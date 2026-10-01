# Feature Spec: Global Post Feed

## Status

In progress

## Author / owner

Vegalife backend team (issue #8)

## Summary

Public, paginated feed of every published post on the platform, newest first: `GET /api/posts/feed`. Reachable without a login, so guests and members browse community content the same way.

## Problem / motivation

There is no way to browse the community's content as a whole. `GET /api/posts` only returns the caller's own posts, and `GET /api/users/{userId}/posts` only returns one member's posts — discovering other people's content means walking user IDs by hand.

Issue #8 was originally a personalized recommendation feed, but the schema cannot record the per-user signals a recommendation needs: no view-history table exists, the `vote` table has no write path, and there is no member-facing comment endpoint. A global feed delivers the browsing capability now and gives a later recommendation engine a baseline ranking to improve on (see the issue's deferral comment).

## Goals

- Anyone — guest or authenticated — can browse published posts across the whole platform.
- Results are stable, newest-first, and paginated with the project's standard bounds.

## Non-goals

- Personalization, semantic filtering, or any per-user ranking (deferred with issue #8; needs interaction-history data first).
- Recording view, vote, or comment interactions.
- Category filtering, keyword search, or client-selectable sort order.
- Popularity ranking (`post.view_count` is never incremented by any code path today, so it cannot rank anything).

## Requirements

### Functional Requirements

- [ ] FR-001: `GET /api/posts/feed` is reachable without a JWT (public `permitAll` route).
- [ ] FR-002: The feed returns only posts with status `published` and `deleted_at IS NULL`.
- [ ] FR-003: Results are ordered by `published_at DESC, created_at DESC` (ties fall back to creation time).
- [ ] FR-004: Pagination uses the existing `PostListRequest` bounds: `page` defaults to `0`, `size` defaults to `20`, `size` must be between `1` and `100`; out-of-range values return `400`.
- [ ] FR-005: If a token is supplied it must still be valid — an invalid or expired token returns `401` even on a public route.
- [ ] FR-006: Draft, processed, unpublished, hidden, and flagged posts are never returned (BR-POST-010).
- [ ] FR-007: Items use the existing `PostListResponse` shape in the standard `PageResponse` envelope.

### Non-Functional Requirements

- [ ] NFR-SEC-001: No private or moderated post content is exposed through the feed under any caller identity.
- [ ] NFR-MAINT-001: The endpoint reuses `PostListRequest`, `PostListResponse`, `PageResponse`, and `PostMapper` — no new DTOs or mappers.
- [ ] NFR-SCALE-001: Query is a single indexed table scan over `post` with no joins; no schema change required.

## Design overview

Four touch points, no migration:

1. `PostRepository` — new derived query `findByStatusAndDeletedAtIsNullOrderByPublishedAtDescCreatedAtDesc(Pageable)`, mirroring the existing user-feed method.
2. `PostService.listFeed(PageRequest)` — maps the page through the existing `PostMapper` into `PostListResponse`.
3. `PostController` — `@GetMapping("/feed")` returning `ApiResponse.ok(...)` with the `PageResponse` envelope; route does not collide with `GET /api/posts` because `PostController` defines no `GET /{postId}`.
4. `SecurityConfig` — add `GET /api/posts/feed` to the `permitAll` chain alongside `GET /api/users/*/posts`.

## Success metrics

- Guest `GET /api/posts/feed` returns `200` with only published posts in the integration suite.
- Full verification (`clean compile`, `test`, `checkstyle:check`, `spotless:apply`) green before review.

## Acceptance criteria

**As a** guest or member, **I want to** scroll a platform-wide feed of published posts, **so that** I can discover community content without knowing whose profile to open.

- [ ] Given a guest, when `GET /api/posts/feed` is requested, then a paginated list of published, non-deleted posts is returned.
- [ ] Given mixed post statuses in the database, when the feed is read, then drafts, flagged, hidden, and soft-deleted posts are absent.
- [ ] Given several published posts, when the feed is read, then they appear newest-first by publish time, with creation time as the tie-breaker.
- [ ] Given no pagination parameters, when the feed is read, then page `0` of size `20` is returned; given `size=101`, then `400`.
- [ ] Given an invalid or expired token, when the feed is requested, then `401 Unauthorized` is returned.

## Risks / open questions

- Ordering by `published_at` leaves rows with a null `published_at` at one end of the sort. In practice only `published` rows are selected and publication sets `publishedAt`, but if any legacy row predates that column, its feed position is undefined — worth a one-off check during testing, not a code change.
- Feed content is unbounded across the whole platform; without a category or quality filter it will include every published post. Accepted for this scope — filtering is a follow-up once the feed exists.
