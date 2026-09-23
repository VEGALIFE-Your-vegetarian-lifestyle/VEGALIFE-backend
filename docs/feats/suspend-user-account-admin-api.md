# Feature Spec: Suspend User Account API (Admin)

## Status
In progress

## Author / owner
Backend team; driving issue: https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/25

## Summary
Allow admins to suspend a user account (mark `status=suspended`, revoke refresh tokens) and reject suspended accounts on login, token refresh, and every authenticated request until restored (#26).

## Problem / motivation
Admins currently cannot disable abusive or spam accounts without deleting data. Existing JWT authentication only validates signature, expiry, and blacklist — a suspended user with a still-valid access token continues to pass the filter and act until natural token expiry. Issue #25 tracks this under Sprint 1.

## Goals
- Admins can suspend a non-deleted user by id; the account is marked `status=suspended`.
- Suspended users cannot log in or refresh tokens.
- Any still-valid access token is rejected on the next authenticated request after suspension.
- Non-admin callers get 403; missing/invalid JWT gets 401.

## Non-goals
- Suspension reason field or audit log (out of scope per product decision; no schema change).
- Restore/unsuspend API (issue #26).
- Permanent deletion or soft-delete (separate concern).
- Bulk suspend.
- Automated suspension from reports.
- Caching account state in Redis or in-memory (performance is not critical for this phase).
- Bulk-blacklisting every outstanding access-token jti (not tracked per user; account-state check covers enforcement).

## Requirements

### Functional Requirements
- [ ] FR-001: `POST /api/admin/users/{userId}/suspend` requires role ADMIN.
- [ ] FR-002: On success, the user's `status` is set to `suspended` and all outstanding refresh tokens for that user are revoked.
- [ ] FR-003: Response is 200 with the same user summary shape as list (`id`, `email`, `username`, `role`, `status`, `createdAt`).
- [ ] FR-004: Missing or soft-deleted user → 404.
- [ ] FR-005: User already `suspended` → 409.
- [ ] FR-006: Login for a suspended account fails with a clear inactive-account message (distinct from invalid credentials / unverified email).
- [ ] FR-007: Refresh with a still-valid refresh token for a non-activated account fails (400).
- [ ] FR-008: Every authenticated request loads current account state; missing, soft-deleted, or `status != activated` → 401 (plain-text body consistent with existing filter).
- [ ] FR-009: Authorities for authorization come from the DB role at authentication time (not solely the JWT `role` claim).
- [ ] FR-010: Non-admin authenticated → 403; no/invalid JWT → 401 (existing SecurityConfig `/api/admin/**`).

### Non-Functional Requirements
- [ ] NFR-SEC-001: Suspension takes effect for subsequent API calls without waiting for access-token natural expiry (≤ one request after status change).
- [ ] NFR-SEC-002: No secrets or password hashes appear in suspend responses.
- [ ] NFR-MAINT-001: JWT filter stays a thin shell; account validation lives in a Spring `AuthenticationProvider` wired through `AuthenticationManager`.
- [ ] NFR-MAINT-002: Response and error shapes use existing `ApiResponse<T>` and exception handlers; no new wrapper styles.

## Design overview
Refactor auth so `JwtAuthenticationFilter` extracts the Bearer token and delegates to `AuthenticationManager`. A new `JwtAuthenticationProvider` parses/validates the token (reuse `JwtTokenService.parseAccessToken`), rejects blacklisted tokens, loads a lightweight account-state projection by id (indexed PK lookup; no cache), and builds an authenticated principal only when the account exists, is not soft-deleted, and has `status=activated`. Role authority is taken from the DB `role` column.

Add `AdminService.suspendUser` + controller mapping: find active user or 404; if already suspended → 409; set `status=suspended`; `jwtTokenService.revokeAllUserRefreshTokens(userId)`; return mapped `UserListResponse`. `AuthService.login` and `AuthService.refreshToken` reject non-activated accounts with `"Account is not active"` (unverified email keeps the existing verification message).

No Flyway migration: `User.Status.suspended` already exists (V1 check constraint).

## Success metrics
- Issue #25 acceptance criteria met in integration tests (suspension recorded; suspended user cannot login or act; non-admin 403) except reason recording, which is explicitly out of scope.
- Existing auth/admin test suite green after filter refactor.

## Acceptance criteria
**As an** admin, **I want to** suspend an abusive account, **so that** they cannot log in or use the API while their data remains intact for later restore.

- [ ] Given an admin JWT, when suspending an active user, then 200 with `status=suspended` and refresh tokens revoked.
- [ ] Given an admin JWT, when suspending an already-suspended user, then 409.
- [ ] Given an admin JWT, when suspending a missing or soft-deleted user, then 404.
- [ ] Given a non-admin JWT, when calling the endpoint, then 403.
- [ ] Given no JWT, when calling the endpoint, then 401.
- [ ] Given a suspended account, when logging in with correct password, then login fails with inactive-account message (no tokens issued).
- [ ] Given a suspended account with a still-valid access token, when calling any authenticated endpoint, then 401 is returned.
- [ ] Given a suspended account with an unrevoked refresh token from before suspension, when calling `/refresh`, then refresh fails.

## Risks / open questions
- Suspension reason (issue AC) is intentionally not implemented — no schema/field; treat AC as partially deferred; note on PR when closing.
- Login currently maps `InvalidTokenException` to HTTP 400 via `GlobalExceptionHandler` while `post-login.md` documents 401/403 — pre-existing gap; this feature does not change status codes unless tests already assert them.
- One extra indexed `user` row read per authenticated request — accepted (user said performance not critical; no cache).

## Related
- Driving issue: #25
- Companion: restore API #26, list API #24
- API: `docs/apis/admin/post-suspend-user.md`
- Business rules: `docs/brs/auth.md` (BR-AUTH-008, BR-AUTH-016)
