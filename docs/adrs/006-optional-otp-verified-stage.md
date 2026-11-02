# ADR-006: Optional OTP Verified Stage on `otp_code`

## Status
Accepted

## Date
2026-09-27

## Deciders
Backend team, with architectural review during issue #62 implementation

## Context
Issue #62 splits `POST /api/auth/reset-password` into two steps: `POST /api/auth/verify-password-reset` (prove the OTP) and `POST /api/auth/reset-password` (set the new password). The verify step must prove possession of a valid code **without** consuming it — consumption stays single-use and happens only when the password actually changes (BR-AUTH-017 reading: verification is repeatable/idempotent until expiry, consumption exactly once).

The existing `otp_code` table (ADR-004) has only `issued → consumed` semantics via `used_at`. We need a durable, DB-enforced marker that a code has passed verification but is not yet consumed, and `resetPassword` must atomically reject rows that are missing it (including the legacy 3-field body, where the unknown `otp` field is silently ignored). The verification stage must not alter `verifyEmail` (email-verification purpose) behavior.

Constraints:
- Flyway migration must be additive-only (no rewrite of existing rows; V16).
- Email-verification OTPs share the same table and must be unaffected.
- Consumption must be race-safe: two concurrent resets with the same code must let exactly one win.

## Decision
Add a nullable `verified_at TIMESTAMP` column to `otp_code`, acting as an **optional, purpose-opt-in verification stage** in the lifecycle:

- `issued` → (`verified_at` set, purpose `PASSWORD_RESET` only) → `consumed` (`used_at`).
- `AuthService.verifyPasswordReset` sets `verified_at` (idempotent: already-verified rows return 200 without rewriting the timestamp).
- `AuthService.resetPassword` requires `verified_at IS NOT NULL` and consumes via a compare-and-set query: `UPDATE otp_code SET used_at = :now WHERE id = :id AND used_at IS NULL AND verified_at IS NOT NULL`, treating affected-rows != 1 as the existing invalid-code error.
- Email verification (`verifyEmail`) never reads or writes `verified_at`; its rows stay `issued → consumed` exactly as today.

## Considered options
- **Option A — nullable `verified_at` on `otp_code` (chosen)** — additive one-column migration; consumption stays a single atomic CAS; purpose-scoped by caller logic like every other `otp_code` concern.
- **Option B — side table (e.g. `otp_verification`)** — normalized, but a join per reset, a second FK lifecycle to garbage-collect, and no stronger invariant than the CAS query already gives.
- **Option C — column on `User` (e.g. `password_reset_verified_at`)** — conflates per-code state with account state: a superseded code could be "covered" by verification of a dead code, and it says nothing about which row is verified.
- **Option D — carry `verified` in the access/step token instead of the DB** — reintroduces the stateless-token complexity ADR-004 removed and cannot be CAS-guarded against concurrent resets.

## Consequences

**Positive:**
- Verification and consumption are cleanly separated in the data model; the reset step's precondition is enforced by SQL, not by service-layer ordering.
- CAS on `used_at IS NULL AND verified_at IS NOT NULL` makes concurrent double-resets impossible to win twice.
- Additive migration; existing rows simply have `verified_at = NULL` and behave exactly as before (an unverified row is indistinguishable from today's unused row).
- No new tables, no `User` schema change, `verifyEmail` untouched.

**Negative / trade-offs:**
- `otp_code` gains a nullable column that is meaningful only for one purpose — callers must know the lifecycle (documented in BR-AUTH-017 and the feature spec).
- The reset endpoint alone can no longer be exercised end to end; every test and client flow needs the verify step first (intentional hard break for legacy 3-field clients).
- Anyone who knows the email can finish a reset inside the ≤10-minute window after the legitimate user verified but before the user reset (accepted residual risk, recorded in the plan).

## Verification
Integration tests in `AuthControllerIntegrationTest` cover the stage boundaries: verify does not consume (old password still works), reset-before-verification → 400, reused code → 400, expired code at either step → 400, legacy 3-field body → 400. Revisit if a third purpose ever needs a pre-consumption stage, or if the verify-then-reset window becomes an exploited issue (then consider binding the verify step to a single-use step token).
