# Email Verification via Generalized OTP

- **Status**: In progress (direction confirmed with owner)
- **Issue**: [#59](https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/59)
- **Author**: Backend team
- **Last reviewed**: 2026-09-24
- **Depends on**: #22 (password-reset OTP, branch `feat/22-forgot-password-otp`, V13 migration)

## Problem

Email verification currently issues a signed JWT link
(`GET /api/auth/verify-email?token=...`). Password reset (issue #22) introduced a
6-digit OTP stored hashed in PostgreSQL. The codebase now has two unrelated
verification mechanisms: a link-based one for registration and a code-based one
for password reset. The link flow requires a frontend callback route, a separate
verification secret, and a separate `verification_tokens` table — and cannot be
reused for any future OTP purpose (e.g. change-email).

## Direction (confirmed 2026-09-24)

1. Replace the verification link with a 6-digit OTP, same shape as the password
   reset code (6 digits, 10-minute expiry, single-use, SHA-256 at rest).
2. Generalize storage into one `otp_code` table with a `purpose` column
   (`PASSWORD_RESET` | `EMAIL_VERIFICATION`). Supersede and query are scoped per
   user **and** purpose — an email-verification OTP never invalidates a pending
   password-reset OTP, and vice versa.
3. OTP issue/consume business logic stays inside `AuthService` (no new
   `OtpService`); shared mechanics are private helpers on that service.
4. Add a resend endpoint (overrides issue #59's "no resend endpoint" non-goal).
5. Expiry for email-verification OTP is **10 minutes** (not 24h) — BR-AUTH-004
   is reconciled from the stale 24h/30min link wording to 10-minute OTP.
6. Delete `VerificationTokenService`, the link email, and
   `GET /api/auth/verify-email?token=`.

## Goals

- One OTP storage model reusable by any future auth OTP purpose.
- Registration verification matches the password-reset UX (user types 6 digits
  instead of clicking a link).
- Users can request a fresh code without re-registering (anti-enumeration).

## Non-goals

- Rate limiting on send/verify endpoints (tracked as follow-up risk).
- Login gate changes (`POST /login` still requires an active account; not
  re-verified accounts still fail there).
- Expired-row cleanup job for `otp_code` (pre-existing gap for
  `password_reset_otp`; `deleteExpired` exists but is never scheduled — follow-up).
- Frontend implementation (separate repo).

## Functional requirements

### FR-001 — Generalized OTP storage

A single `otp_code` table (V14 renames `password_reset_otp` and adds `purpose`)
with columns `id`, `user_id`, `otp_hash` (SHA-256 hex, 64 chars), `purpose`
(`PASSWORD_RESET` | `EMAIL_VERIFICATION`, not null, default `PASSWORD_RESET` for
existing rows), `expires_at`, `used_at`, `created_at`. Entity `OtpCode`,
repository `OtpCodeRepository`, enum `OtpPurpose`. All repository lookups and
supersede updates are filtered by `(user_id, purpose)`.

### FR-002 — Register issues a verification OTP

`POST /api/auth/register` creates the account (status `PENDING` unchanged),
issues one `EMAIL_VERIFICATION` OTP (10-minute expiry), supersedes any prior
unused `EMAIL_VERIFICATION` OTPs for that user only, and sends the OTP email.
No verification link is created or sent.

### FR-003 — Verify email with OTP

`POST /api/auth/verify-email` with body `{ "email": string, "otp": string }`
activates the account. Unknown email → 400 `Invalid or already used verification
code` (mirrors reset-password anti-enumeration). Already-verified account → 200
idempotent success without consuming an OTP. Success response shape is the same
`RegisterResponse` as today (user id, email, username, status, timestamps).

### FR-004 — Invalid / used OTP

OTP not found for (user, `EMAIL_VERIFICATION`), hash mismatch, or already used →
400 `Invalid or already used verification code`.

### FR-005 — Expired OTP

Active OTP past `expires_at` → 400
`Verification code has expired. Please request a new one.`

### FR-006 — Purpose-scoped supersede

Issuing any OTP supersedes (marks `used_at`) only unused OTPs of the **same
purpose for the same user**. Registering twice supersedes the old verification
code; requesting a password reset never touches pending verification codes.

### FR-007 — Resend verification OTP

`POST /api/auth/resend-email` with `{ "email": string }` always
returns 200 with a generic message
(`If an account with that email exists, a verification code has been sent`) —
regardless of whether the email is unknown or already verified (no code is sent
in those cases). For an unverified, existing user: supersede prior
`EMAIL_VERIFICATION` OTPs, issue a fresh 10-minute OTP, and send the email.

### FR-008 — OTP secrecy at rest

Only SHA-256 hex digests are persisted. Raw OTPs are never logged.

## Non-functional requirements

- **SEC**: no raw OTP in DB or logs; generic messages on resend and unknown
  email; constant response shape across unknown/known email on resend.
- **MAINT**: one expiry config `app.email-verification.otp-expiry-minutes`
  (default 10, mirrors `app.password-reset.otp-expiry-minutes`); shared
  `ApiResponse` envelope; exceptions from `shared/exception`.
- **COMPAT**: `app.verification.*` config (`token-expiry-minutes`, `issuer`,
  `secret` / `VERIFICATION_SECRET`) removed once the link flow is deleted.

## API surface (after change)

| Method | Path | Request | Success | Notes |
|--------|------|---------|---------|-------|
| POST | `/api/auth/register` | `RegisterRequest` | 201 `RegisterResponse` | now sends OTP email, no link |
| POST | `/api/auth/verify-email` | `{email, otp}` | 200 `RegisterResponse` | replaces `GET ...?token=` |
| POST | `/api/auth/resend-email` | `{email}` | 200 generic | new; anti-enumeration |
| POST | `/api/auth/forgot-password` | `ForgotPasswordRequest` | 200 generic | unchanged (purpose `PASSWORD_RESET`) |
| POST | `/api/auth/reset-password` | `ResetPasswordRequest` | 200 generic | unchanged |

## Business rules touched

- BR-AUTH-003 — verification now via `POST /verify-email` + OTP (200).
- BR-AUTH-004 — rewritten: 10-minute email-verification OTP (replaces stale
  24h token / 30-minute wording).
- BR-AUTH-017 — at most one unused OTP **per user per purpose** (was per user).
- BR-AUTH-019 — storage now `otp_code.otp_hash`; drop phantom
  `PasswordResetOtpService` reference (logic lives in `AuthService`).
- BR-AUTH-021 (new) — email-verification OTP lifecycle + resend anti-enumeration.

## Errors

| Condition | HTTP | Code / message |
|-----------|------|----------------|
| Missing/invalid `email` or `otp` on verify | 400 | validation message from DTO |
| Unknown email on verify | 400 | `Invalid or already used verification code` |
| Wrong / used verification OTP | 400 | `Invalid or already used verification code` |
| Expired verification OTP | 400 | `Verification code has expired. Please request a new one.` |
| Resend (any email state) | 200 | `If an account with that email exists, a verification code has been sent` |

## Test coverage bar (same as #22)

- Unit (`AuthServiceTest`): register issues OTP and supersedes only
  `EMAIL_VERIFICATION`; verify success / unknown email / wrong OTP / used OTP /
  expired OTP / already-verified idempotent; resend happy path + unknown email +
  already-verified no-op; forgot-password still works after purpose-scoping
  (regression).
- Integration (`EmailVerificationIntegrationTest`, renamed/updated):
  register → email contains 6 digits → verify via POST 200 → login succeeds;
  resend → second code valid, first superseded; password-reset OTP unaffected by
  verification issuance (cross-purpose regression).
- Build gates: `./mvnw clean verify`, `checkstyle:check`, `spotless:apply`.

## Risks / open issues

- **No rate limiting** on register/verify/resend → OTP spam / email flooding
  possible; mitigated only by 10-minute single-active-code supersede. Follow-up.
- **Branch stacking**: this branch sits on unmerged `feat/22-forgot-password-otp`
  (not yet on `main`); must merge after #22.
- **Expired-row cleanup** still unscheduled for `otp_code`; follow-up with #22's
  pre-existing gap.
- PR will exceed the ~400 LOC review guideline once tests are counted; phases
  below are kept commit-sized for incremental review.
