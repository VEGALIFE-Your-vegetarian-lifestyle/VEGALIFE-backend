# Feature Spec: List All Posts API (Admin)

## Status
Implemented (pending PR review)

## Author / owner
Backend team; driving issue: https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/1

## Summary
Allow admin users to retrieve a paginated list of every non-deleted post across the platform — all authors, all statuses — filterable by status, author, category, and creation date range, with each item carrying its semantic content-filter verdict.

## Problem / motivation
Admins review reported and flagged content, but the post list endpoints that exist are author-scoped: `GET /api/posts` returns only the caller's own posts and `GET /api/users/{userId}/posts` returns one member's posts. Neither can answer "show me everything, including posts by other users in non-public states", so moderation of flagged content today requires database access. Issue #1 tracks this under the Content Moderation epic (Sprint 2, Priority: Medium).

## Goals
- Admins can page through every non-deleted post regardless of author or status, with author identity and the content-filter verdict on each row.
- Admins can narrow the list by post status, author, category, and createdAt range.
- Non-admin authenticated users are denied access (403); anonymous callers get 401.

## Non-goals
- Bulk moderation actions (hide/unhide in bulk, approve/reject — issue #5).
- Filtering or sorting by the content-filter verdict itself (`flag` is returned, not queryable here).
- Export functionality.
- Soft-deleted post visibility (`deletedAt IS NULL` always).
- Full-text search over title/content.
- Comments, videos, and recipes lists (issues #2, #3, #4).

## Requirements

### Functional Requirements
- [x] FR-001: `GET /api/admin/posts` returns a paginated list of posts to callers with role ADMIN.
- [x] FR-002: The result spans all authors and all `Post.Status` values (`created`, `processed`, `published`, `unpublished`, `hidden`, `flagged`).
- [x] FR-003: Each item includes `id`, `title`, `type`, `content`, `featuredImageUrl`, `videoUrl`, `categoryIds`, `mediaIds`, `status`, `flag`, `viewCount`, `publishedAt`, `createdAt`, plus author `userId`, `username`, `email`.
- [x] FR-004: Optional query filters: `status` (Post.Status), `userId` (UUID), `categoryId` (UUID), `createdFrom`, `createdTo` (ISO-8601 datetime on `createdAt`).
- [x] FR-005: Pagination via `page` (0-based, default 0), `size` (default 20, max 100), optional `sort` (default `createdAt,desc`); sort property must be one of `createdAt`, `publishedAt`, `updatedAt`, `viewCount`, `title`, otherwise 400.
- [x] FR-006: Soft-deleted posts (`deletedAt != null`) are never returned.
- [x] FR-007: Non-admin authenticated requests receive 403; missing/invalid JWT receives 401.
- [x] FR-008: `createdFrom` after `createdTo` is rejected with 400.

### Non-Functional Requirements
- [x] NFR-SEC-001: Endpoint path `/api/admin/**` is restricted to `ROLE_ADMIN` by the existing security filter chain rule.
- [x] NFR-SEC-002: The response DTO exposes no `passwordHash` or other account secrets; author fields are limited to `userId`, `username`, `email`.
- [x] NFR-MAINT-001: Response wrapped in `ApiResponse<T>` with pagination shape from `shared/dto/PageResponse`.
- [x] NFR-MAINT-002: Controller stays thin; filter parsing and page assembly live in a service; predicates live in a `PostSpecifications` class.
- [x] NFR-SCALE-001: The `categoryId` filter returns each matching post exactly once (join is de-duplicated).

## Design overview
New `AdminPostController` (`controller/admin`) and `AdminPostService` (`service/admin`) alongside the existing user-admin pair, reached through the `GET /api/admin/posts` path already covered by `SecurityConfig`'s `/api/admin/**` → `hasRole("ADMIN")` matcher, so no security configuration changes. `PostRepository` gains `JpaSpecificationExecutor<Post>` and a new `PostSpecifications.allWithFilters` mirrors `UserSpecifications.activeWithFilters`: soft-delete exclusion plus optional status/author/date predicates, with `categories` joined and de-duplicated for `categoryId`. Filtering and mapping reuse the `AdminService` precedent, with one deliberate deviation — the `sort` property is validated against an allowlist so a bad value yields 400 rather than Spring Data's `PropertyReferenceException` (500). Author identity and the semantic `flag` verdict come from the post's lazy `user` association and the `flag` column added by the post content-filtering feature (issue #33/34, `docs/feats/post-content-filtering.md`).

## Success metrics
- Issue #1's five acceptance criteria are covered by integration tests before PR merge.
- Endpoint p95 < 300ms on dev with < 10k posts (manual check optional).

## Acceptance criteria
**As an** admin, **I want to** list and filter every post on the platform, **so that** I can review reported and flagged content without database access.

- [x] Given an admin JWT, when requesting `GET /api/admin/posts`, then 200 with a paginated list of posts is returned.
- [x] Given posts authored by several users in mixed statuses, when requesting the endpoint, then posts from all users and all statuses are returned.
- [x] Given an admin JWT, when filtering by `status`, `userId`, `createdFrom`/`createdTo`, or `categoryId`, then only matching posts are returned.
- [x] Given any list response, when inspecting items, then each item shows the semantic filtering result (`flag`: `PENDING`, `PASSED`, `REJECTED`, `NEEDS_REVIEW`, or null when never filtered).
- [x] Given a non-admin JWT, when requesting the endpoint, then 403 is returned.
- [x] Given no JWT, when requesting the endpoint, then 401 is returned.

## Risks / open questions
- Author mapping reads a lazy `user` per row, bounded by `size` (max 100) per page — at most 101 statements per request. Acceptable at current scale; an `@EntityGraph` is the fix if it shows up in profiling.
- Soft-deleted posts stay invisible to admins, consistent with `GET /api/admin/users`. If moderation ever needs to audit deleted content, that is a separate change.
- The 403 body is Spring Boot's default error shape (no custom `AccessDeniedHandler` exists), same as every other admin endpoint today.
