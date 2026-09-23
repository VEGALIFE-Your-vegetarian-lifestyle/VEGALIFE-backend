# Feature Spec: Restore User Account API (Admin)

## Status
Implemented

## Author / owner
Backend team; driving issue: https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/26

## Summary
Allow admins to reverse a suspension by restoring the account (`status=suspended` → `status=activated`), so the user can log in and use the API again, with the restoration recorded for audit.

## Problem / motivation
Issue #25 added admin suspend, but there is no way to reverse it after review/appeal. A suspended account stays blocked forever — login, refresh, and every authenticated request reject it (`status != activated`). Issue #26 tracks this under Sprint 1.

## Goals
- Admins can restore a suspended, non-deleted user by id; the account is marked `status=activated`.
- Restored users can log in and perform actions normally (same enforcement path as any activated account).
- Restoration is logged for audit trail.
- Non-admin callers get 403; missing/invalid JWT gets 401.

## Non-goals
- Automatic restoration after a time period.
- Notification to the user (separate concern).
- Restoring `deactivated` or `created` accounts — only `suspended` accounts are in scope.
- Persistent audit table / audit entity (per product decision: SLF4J log line only, consistent with suspend #25; no schema change).
- Bulk restore.

## Requirements

### Functional Requirements
- [x] FR-001: `POST /api/admin/users/{userId}/restore` requires role ADMIN.
- [x] FR-002: On success, the user's `status` is set to `activated`.
- [x] FR-003: Response is 200 with the same user summary shape as list/suspend (`id`, `email`, `username`, `role`, `status`, `createdAt`).
- [x] FR-004: Missing or soft-deleted user → 404.
- [x] FR-005: User whose status is not `suspended` → 409 ("User is not suspended").
- [x] FR-006: A successful restoration writes an audit log line including the target user id.
- [x] FR-007: Restored users can log in with correct credentials and call authenticated endpoints normally.

### Non-Functional Requirements
- [x] NFR-SEC-001: Only `ROLE_ADMIN` may call the endpoint (existing `SecurityConfig` `/api/admin/**`); non-admin → 403, no JWT → 401.
- [x] NFR-SEC-002: No secrets or password hashes appear in restore responses.
- [x] NFR-MAINT-001: Response and error shapes use existing `ApiResponse<T>`, `UserListResponse`, and exception handlers; no new wrapper styles.
- [x] NFR-MAINT-002: No schema change — `activated` is already allowed by the V1 status check constraint.

## Design overview
Mirror `AdminService.suspendUser`: find active user or 404; if `status != suspended` → 409 (`DuplicateResourceException`); set `status=activated`; save; `log.info` audit line with target user id; return mapped `UserListResponse`. Endpoint lives in `AdminController` under `/api/admin/users/{userId}/restore`, admin-gated for free by `SecurityConfig`. No Flyway migration and no refresh-token action — an already-revoked refresh token is not reissued; the user simply logs in again.

## Success metrics
- Issue #26 acceptance criteria met in integration tests (restore succeeds; restored user can log in; non-admin 403; audit log written).
- Existing auth/admin test suite stays green.

## Acceptance criteria
**As an** admin, **I want to** restore a suspended account after review/appeal, **so that** the user can use the platform again without data loss.

- [x] Given an admin JWT, when restoring a suspended user, then 200 with `status=activated` and an audit log line is written.
- [x] Given a restored account, when logging in with correct password, then login succeeds and the user can perform actions normally.
- [x] Given an admin JWT, when restoring a user who is not suspended, then 409.
- [x] Given an admin JWT, when restoring a missing or soft-deleted user, then 404.
- [x] Given a non-admin JWT, when calling the endpoint, then 403.
- [x] Given no JWT, when calling the endpoint, then 401.

## Risks / open questions
- Audit trail is a log line only (confirmed with user) — if compliance later requires queryable audit, a separate schema change will be needed.
- Restoring does not re-issue tokens; the user must log in again (acceptable — refresh tokens were revoked at suspend).

## Related
- Driving issue: #26
- Companion: suspend API #25, list API #24
- API: `docs/apis/admin/post-restore-user.md`
- Business rules: `docs/brs/auth.md` (BR-AUTH-008, BR-AUTH-016)
