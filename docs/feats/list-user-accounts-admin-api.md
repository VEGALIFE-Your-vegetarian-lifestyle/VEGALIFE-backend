# Feature Spec: List User Accounts API (Admin)

## Status
In progress

## Author / owner
Backend team; driving issue: https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/24

## Summary
Allow admin users to retrieve a paginated list of all non-deleted user accounts, filterable by status, role, and creation date range, for moderation, support, and analytics.

## Problem / motivation
Admins currently have no way to view registered users through the API. Moderation and support workflows (suspend/restore is planned separately) need a baseline list of accounts with key fields. Issue #24 tracks this under Sprint 1 (Priority: High).

## Goals
- Admins can page through all non-deleted users with key account fields.
- Admins can narrow the list by status, role, and createdAt range.
- Non-admin authenticated users are denied access (403).

## Non-goals
- Bulk actions (suspend/restore are separate endpoints, #25/#26).
- Export functionality.
- Soft-deleted account visibility (`deletedAt IS NULL` always).
- Full-text search by username/email.

## Requirements

### Functional Requirements
- [ ] FR-001: `GET /api/admin/users` returns a paginated list of users to callers with role ADMIN.
- [ ] FR-002: Each item includes `id`, `email`, `username`, `role`, `status`, `createdAt` — never `passwordHash` or other secrets.
- [ ] FR-003: Optional query filters: `status` (User.Status), `role` (User.Role), `createdFrom`, `createdTo` (ISO-8601 date or datetime).
- [ ] FR-004: Pagination via `page` (0-based, default 0), `size` (default 20, max 100), optional `sort` (default `createdAt,desc`).
- [ ] FR-005: Soft-deleted users (`deletedAt != null`) are never returned.
- [ ] FR-006: Non-admin authenticated requests receive 403; missing/invalid JWT receives 401.

### Non-Functional Requirements
- [ ] NFR-SEC-001: Endpoint path `/api/admin/**` is restricted to `ROLE_ADMIN` in the security filter chain.
- [ ] NFR-SEC-002: Response DTO excludes `passwordHash`, `emailVerified` internals are optional; only fields listed in FR-002.
- [ ] NFR-MAINT-001: Response wrapped in `ApiResponse<T>`; pagination shape reusable via `shared/dto/PageResponse`.
- [ ] NFR-MAINT-002: Controller remains thin; filtering/pagination logic lives in `AdminService`.

## Design overview
New `AdminController` + `AdminService` under `controller/admin` and `service/admin` (names intentionally generic for future admin operations). `UserRepository` gains a `Pageable` query excluding soft-deleted rows with optional filters. `SecurityConfig` adds `.requestMatchers("/api/admin/**").hasRole("ADMIN")`. First pagination pattern in the codebase: `PageResponse<T>` in `shared/dto`. JWT filter already supplies `ROLE_ADMIN` authority; no method-security enablement required.

## Success metrics
- Issue #24 acceptance criteria all checked and covered by integration tests before PR merge.
- Endpoint p95 < 300ms on dev with < 10k users (manual check optional).

## Acceptance criteria
**As an** admin, **I want to** list and filter user accounts, **so that** I can support moderation and user support without database access.

- [ ] Given an admin JWT, when requesting `GET /api/admin/users`, then 200 with a paginated list of users is returned.
- [ ] Given an admin JWT, when filtering by `status=suspended`, then only suspended non-deleted users are returned.
- [ ] Given an admin JWT, when filtering by `role` and `createdFrom`/`createdTo`, then only matching users are returned.
- [ ] Given a non-admin JWT, when requesting the endpoint, then 403 is returned.
- [ ] Given no JWT, when requesting the endpoint, then 401 is returned.
- [ ] Given any list response, when inspecting items, then only id/email/username/role/status/createdAt fields are present (no passwordHash).

## Risks / open questions
- None blocking. Sort allowlist is not enforced beyond Spring Data's default behavior — invalid sort properties surface as a 500 unless handled; acceptable for admin-only v1, revisit if exposed more broadly.
