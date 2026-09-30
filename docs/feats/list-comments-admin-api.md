# Feature Spec: List All Comments API (Admin)

## Status
In progress

## Author / owner
Backend team; driving issue: https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/2

## Summary
Allow admin users to retrieve a paginated list of all comments across the platform — every user, both active and removed — filterable by status, author, post, and creation date range, for discussion moderation.

## Problem / motivation
Admins moderate discussions at scale but have no API to see comments: no `Comment` Java code exists at all (only the `comment` table from `V8__create_comment_vote_tables.sql`). Moderation of user discussions today requires direct database access. Issue #2 tracks this under Sprint 2 (Priority: Medium).

## Goals
- Admins can page through all comments (all users, all statuses) with the fields needed to moderate.
- Admins can narrow the list by status, author, post, and createdAt range.
- Non-admin users are denied access (403); unauthenticated requests get 401.

## Non-goals
- Bulk actions (hide/delete many comments at once).
- Thread context in the list view — rows are flat; `parentId` is returned as a plain field, parent/child trees are not resolved.
- Creating, editing, or deleting comments (no comment mutation endpoints in this issue).
- A persisted `status` column or new migration — status is derived from `deleted_at` (see Requirements).

## Requirements

### Functional Requirements
- [ ] FR-001: `GET /api/admin/comments` returns a paginated list of comments to callers with role ADMIN.
- [ ] FR-002: The unfiltered list includes comments from all users and both statuses (active and removed).
- [ ] FR-003: Each item includes `id`, `postId`, `parentId`, `userId`, `username`, `content`, `status`, `createdAt`, `updatedAt`.
- [ ] FR-004: Optional query filters: `status` (`active` | `removed`), `userId` (UUID), `postId` (UUID), `createdFrom`, `createdTo` (ISO-8601 date or datetime).
- [ ] FR-005: `status` is derived from `deleted_at`: `active` = `deleted_at IS NULL`, `removed` = `deleted_at IS NOT NULL`. Omitting `status` returns both. There is no `all` value and no new column.
- [ ] FR-006: Pagination via `page` (0-based, default 0), `size` (default 20, max 100), optional `sort` (default `createdAt,desc`, allowlisted to `createdAt` and `updatedAt`).
- [ ] FR-007: Invalid `status` value, non-allowlisted `sort` property, malformed UUID, or `createdFrom` after `createdTo` returns 400 with a validation message.
- [ ] FR-008: Non-admin authenticated requests receive 403; missing/invalid JWT receives 401.

### Non-Functional Requirements
- [ ] NFR-SEC-001: Endpoint path `/api/admin/**` is restricted to `ROLE_ADMIN` by the existing security filter chain — no `SecurityConfig` change needed.
- [ ] NFR-SEC-002: Response DTO exposes only the fields listed in FR-003; no internal columns beyond them.
- [ ] NFR-MAINT-001: Response wrapped in `ApiResponse<T>`; pagination shape reused via `shared/dto/PageResponse`.
- [ ] NFR-MAINT-002: Controller stays thin; filtering, validation, and pagination logic live in the service layer, mirroring `AdminService.listUsers`.
- [ ] NFR-MAINT-003: Filtering is built with a JPA `Specification` (like `UserSpecifications`), not a fixed JPQL query, so nullable filter parameters don't hit untyped PostgreSQL parameters.

## Design overview
New `Comment` entity mapped to the existing `comment` table (`model/post/Comment.java`, plain UUID FK columns `userId`/`postId`/`parentId`, no associations), `CommentRepository extends JpaRepository<Comment, UUID>, JpaSpecificationExecutor<Comment>`, and `CommentSpecifications.withFilters(...)` for the optional predicates. A new `GET /comments` handler on the existing `AdminController` delegates to a `listComments` method on `AdminService`, which validates inputs, builds the `Pageable`, runs the specification, batch-resolves author usernames in one extra `findAllById` call (avoids N+1), and maps to `CommentListResponse`. Request DTO `CommentListRequest` (`@ModelAttribute @Valid`) mirrors `UserListRequest`. No migration: status derives from `deleted_at`. `SecurityConfig` already maps `/api/admin/**` to `hasRole("ADMIN")`.

## Success metrics
- Issue #2 acceptance criteria all checked and covered by unit + integration tests before PR merge.
- Endpoint p95 < 300ms on dev-sized data (manual check optional).

## Acceptance criteria
**As an** admin, **I want to** list and filter all comments across the platform, **so that** I can moderate discussions without database access.

- [ ] Given an admin JWT, when requesting `GET /api/admin/comments`, then 200 with a paginated list of comments is returned.
- [ ] Given comments from multiple users and posts, when requesting the list without filters, then comments from all users and both statuses are returned.
- [ ] Given an admin JWT, when filtering by `status=active` (or `status=removed`), then only active (or only removed) comments are returned.
- [ ] Given an admin JWT, when filtering by `userId` and/or `postId`, then only that author's / that post's comments are returned.
- [ ] Given an admin JWT, when filtering by `createdFrom`/`createdTo`, then only comments created in that range are returned.
- [ ] Given a non-admin JWT, when requesting the endpoint, then 403 is returned.
- [ ] Given no JWT, when requesting the endpoint, then 401 is returned.

## Risks / open questions
- `username` resolution costs one extra `findAllById` query per page — acceptable at page sizes ≤ 100; revisit only if this endpoint ever becomes high-traffic.
- Sort is allowlisted to `createdAt`/`updatedAt` (unlike the free-form sort in `AdminService.listUsers`) so invalid properties surface as 400 instead of 500; the two endpoints intentionally diverge here.
- Moderation states beyond removed (e.g. `hidden`) would require a real status column and a migration — deliberately deferred until a product decision exists.
