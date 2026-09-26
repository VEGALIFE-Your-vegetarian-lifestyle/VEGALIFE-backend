# Feature Spec: Forgot Password with Email OTP

## Status

In progress

## Author / owner

Backend team, driven by issue #22 (Epic: Account & Access Management, Sprint 1).

## Summary

Let a registered user recover a forgotten password by proving control of their email: `POST /api/auth/forgot-password` emails a 6-digit OTP valid for 10 minutes, and `POST /api/auth/reset-password` consumes that OTP to set a new password.

## Problem / motivation

There is currently no way to regain access to an account whose password is forgotten. The only auth flows are register, email-verify, login, refresh, and logout (`AuthController`). Without a reset flow, users must rely on manual admin intervention, and issue #22 (Sprint 1) requires a secure, time-limited reset via email OTP.

## Goals

- A registered user can set a new password without knowing the old one, by receiving a code at their registered email.
- The reset credential is time-limited (10 minutes) and single-use.
- Account existence is not revealed by the forgot-password endpoint.

## Non-goals

- Reset via SMS or other channels (per issue #22).
- Password history / reuse prevention (per issue #22).
- Rate limiting / resend cooldown on forgot-password (noted as a risk below).
- A dedicated resend endpoint — calling forgot-password again issues a fresh code and invalidates the previous one.
- Admin-triggered password resets.

## Requirements

### Functional Requirements

- [x] FR-001: `POST /api/auth/forgot-password` accepts `{ email }`; when the email belongs to a registered user, a 6-digit numeric OTP is generated, persisted with `expires_at = now + 10 minutes`, and emailed to that address.
- [x] FR-002: `POST /api/auth/forgot-password` always returns the same 200 response regardless of whether the email exists (anti-enumeration).
- [x] FR-003: Requesting a new OTP invalidates any previous unused OTP for that user **of the same purpose** (`PASSWORD_RESET`); at most one active reset OTP exists per user. Email-verification codes are unaffected (ADR-004).
- [x] FR-004: `POST /api/auth/reset-password` accepts `{ email, otp, newPassword }`; on a valid, unexpired, unused OTP matching that user's active code, the password is BCrypt-encoded into `user.password_hash`, the OTP is marked used, and all of the user's refresh tokens are revoked.
- [x] FR-005: Expired OTP → 400 with an explicit expiry message; wrong or already-used OTP → 400 with an invalid-code message.
- [x] FR-006: Resending (calling forgot-password again) issues a new code that supersedes the old one.

### Non-Functional Requirements

- [x] NFR-SEC-001: OTP is stored only as a SHA-256 hash; the raw 6-digit code is never persisted or logged.
- [x] NFR-SEC-002: Both endpoints are public under `/api/auth/**` (already `permitAll`); no auth token required, no security-config change.
- [x] NFR-SEC-003: A successful reset forces re-login on other sessions (refresh tokens revoked, cf. BR-AUTH-013 mechanism).
- [x] NFR-MAINT-001: Expiry is configurable via `app.password-reset.otp-expiry-minutes` (default 10); OTP persistence follows existing Flyway + JPA conventions.
- [x] NFR-MAINT-002: Responses use the standard `ApiResponse` envelope; errors reuse existing `InvalidTokenException` / `ExpiredTokenException` → 400 mappings.

## Design overview

Two new public endpoints on `AuthController`, logic in `AuthService`, OTP storage initially as `password_reset_otp` (Flyway `V13`) with entity `PasswordResetOtp` + repository — renamed to `otp_code` + `OtpCode` with a `purpose` discriminator in V14 (issue #59 / ADR-004; reset codes use purpose `PASSWORD_RESET`) — a new `EmailService.sendPasswordResetOtp(...)` method with a Thymeleaf `email/password-reset-otp.html` template, and config `app.password-reset.otp-expiry-minutes`. OTP codes are SHA-256-hashed at rest, mirroring the refresh-token pattern (BR-AUTH-011). The storage decision is recorded in `docs/adrs/003-password-reset-otp-storage.md` (superseded in shape by `docs/adrs/004-generalized-otp-storage.md`; decision itself unchanged).

## Success metrics

- All 4 acceptance criteria from issue #22 pass in integration tests.
- Reset → login with the new password succeeds; old password fails afterward.
- No raw OTP appears in API responses, logs, or the database.

## Acceptance criteria

**As a** registered user who forgot their password, **I want to** reset it via a code emailed to me, **so that** I can regain access without admin help.

- [x] Given a registered email, when the user requests a reset, then a 6-digit OTP is sent via email.
- [x] Given a valid OTP within 10 minutes, when the user submits OTP and new password, then the password is updated (old password no longer works, new one does).
- [x] Given an expired OTP, when the user submits it, then a 400 error with an expiry message is returned.
- [x] Given an invalid (wrong or already-used) OTP, when the user submits it, then a 400 error with an invalid-code message is returned.
- [x] Given an unregistered email, when the user requests a reset, then the same generic 200 response is returned and no email is sent.
- [x] Given a previous OTP was issued, when the user requests another, then the previous OTP no longer works.

## Risks / open questions

- **No rate limiting** on forgot-password or reset-password: a 6-digit OTP has 10^6 combinations and could be guessed online within its 10-minute window by a determined attacker. Accepted for Sprint 1 scope; rate limiting should be a follow-up issue.
- Email deliverability depends on SMTP config (`spring.mail.*`); the request only enqueues the email (ADR-005), so an SMTP outage no longer surfaces as 500 or rolls back OTP creation — the background drainer retries delivery.
- Suspended/deactivated users can still receive a reset code; login remains blocked by status checks (BR-AUTH-008/016), so this is deliberate.

## Related

- Issue: https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/22
- ADR: `docs/adrs/003-password-reset-otp-storage.md`
- API refs: `docs/apis/auth/post-forgot-password.md`, `docs/apis/auth/post-reset-password.md`
- Business rules: `docs/brs/auth.md` (BR-AUTH-017 … BR-AUTH-020)
