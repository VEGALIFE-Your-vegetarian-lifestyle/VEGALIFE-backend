# ADR-004: Generalize OTP Storage with a Purpose Discriminator

## Status

Accepted

## Date

2026-09-24

## Deciders

Backend team, per confirmed direction on issue #59.

## Context

Issue #59 replaces the stateless JWT email-verification link with a 6-digit
OTP. ADR-003 already established hashed OTP rows for password reset in a
dedicated `password_reset_otp` table (V13). Adding email verification as a
second, separately named table would duplicate lifecycle logic (issue, hash,
expire, single-use, supersede) and leave two near-identical schemas to
maintain. Every future OTP purpose (change-email, 2FA enrollment, …) would
repeat the pattern again.

Constraints from the confirmed direction:

- OTP business logic stays inside `AuthService` (no new `OtpService`).
- An email-verification OTP must never invalidate a pending password-reset
  OTP, and vice versa — supersede is per user **and** purpose.
- Verification OTP expiry is 10 minutes (BR-AUTH-004 reconciled), same as
  password reset.

## Decision

1. **One table, purpose-scoped lifecycle.** V14 renames `password_reset_otp`
   → `otp_code` and adds `purpose VARCHAR(32) NOT NULL DEFAULT
   'PASSWORD_RESET'` (existing rows backfill as password-reset codes).
   Purposes are an enum `OtpPurpose { PASSWORD_RESET, EMAIL_VERIFICATION }`
   stored as a string.
2. **Entity/repository rename.** `PasswordResetOtp` → `OtpCode`,
   `PasswordResetOtpRepository` → `OtpCodeRepository`. Every lookup and
   supersede query takes `(userId, purpose)`:
   `findLatestUnusedByUserIdAndPurpose`, `markAllUnusedByUserIdAndPurpose`.
3. **Storage posture unchanged from ADR-003**: SHA-256 hex at rest,
   `expires_at` + `used_at` lifecycle columns, at most one active row per
   (user, purpose).
4. **Consumers**: `AuthService` issues/consumes `EMAIL_VERIFICATION` codes for
   register/verify/resend and `PASSWORD_RESET` codes for forgot/reset, via
   private helpers on that service. `VerificationTokenService` and the
   verification link email are deleted.

## Considered options

- **Option A — one `otp_code` table + `purpose` (chosen)** — one schema, one
  repository, supersede naturally scopes with a second WHERE clause; adding a
  purpose later is an enum constant, not a migration.
- **Option B — separate `email_verification_otp` table** — clearer names per
  flow, but duplicates columns, indexes, repository methods, and cleanup;
  every future purpose multiplies tables.
- **Option C — single active OTP per user, no purpose column** — simplest
  queries, but requesting a password reset would silently kill a pending
  verification code (and the reverse), breaking independent flows.

## Consequences

**Positive:**

- Register/verify and forgot/reset share one audited storage path; ADR-003's
  hashing/expiry/single-use conclusions apply to both purposes verbatim.
- Cross-purpose interference is impossible by construction (purpose in every
  query).
- Frontend contract unifies on "6-digit code + email" for both auth flows.

**Negative / trade-offs:**

- V14 is a rename + ALTER on a table introduced one migration earlier;
  environments that already applied V13 need the rename to run in order
  (Flyway handles it; never edit V13 after it has been applied anywhere).
- The `purpose` column is an open enum in the DB (checked in Java only) —
  acceptable at current scale; a CHECK constraint can be added later.
- Expired-row cleanup gap noted in ADR-003 now covers both purposes;
  follow-up to schedule `OtpCodeRepository.deleteExpired` remains open.

## Verification

Unit tests cover both purposes (issue, consume, expired, supersede,
cross-purpose non-interference); integration tests cover register → OTP →
verify and forgot → OTP → reset end to end against Testcontainers PostgreSQL.
