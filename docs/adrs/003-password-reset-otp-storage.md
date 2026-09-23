# ADR-003: Store Password-Reset OTPs as Hashed Rows in PostgreSQL

## Status

Accepted

## Date

2026-09-24

## Deciders

Backend team, per confirmed direction on issue #22.

## Context

The forgot-password flow needs a 6-digit numeric code that is valid for exactly 10 minutes, single-use, and invalidatable when a newer code is issued. The code must be verified server-side during `POST /api/auth/reset-password`.

Existing precedents in this codebase:

- Email verification uses a **stateless JWT** (`VerificationTokenService`) with no server-side row — but that design cannot revoke a token before it expires, cannot enforce single-use, and carries no "only the latest code is valid" semantics.
- Refresh tokens are **opaque random values stored as SHA-256 hashes** in a `refresh_token` table with `expires_at` / `revoked_at` (BR-AUTH-011, V11 migration) — exactly the lifecycle fields this flow needs.
- No Redis or other shared cache is in the stack (see `docs/arch/dependencies.md`); introducing one for a single feature would be new infrastructure.

The OTP is low-entropy (10^6 values), so at-rest storage choice matters: a plaintext column would expose usable reset codes to anyone with a database dump or a mis-logged query.

## Decision

Persist password-reset OTPs in a new PostgreSQL table `password_reset_otp` (Flyway `V13`), each row holding the SHA-256 hex hash of the 6-digit code plus `expires_at` and `used_at` lifecycle columns, with at most one active (unused, unexpired) row per user.

## Considered options

- **Option A — DB table with SHA-256-hashed code (chosen)** — single source of truth, supports expiry/single-use/invalidation with plain SQL, matches the existing refresh-token pattern, no new infrastructure.
- **Option B — Stateless JWT (like email verification)** — no table, but cannot revoke old codes, cannot enforce single-use server-side, and "expires in 10 minutes" lives only in the token — fails the resend-invalidates-previous requirement.
- **Option C — Plaintext OTP column** — simpler debugging, but a DB read or log line yields a live reset code; rejected on security grounds.
- **Option D — Redis with TTL** — natural 10-minute expiry, but adds a datastore the project does not run, and OTP state would vanish on cache restart mid-flow.

## Consequences

**Positive:**

- Expiry, single-use, and invalidation are enforced in one place with indexed queries; behavior survives restarts and is visible to operators.
- Hashed at rest: a leaked row does not yield a usable code (SHA-256 of a 6-digit value cannot be reversed, only brute-forced offline — same posture as refresh tokens).
- Reuses established Flyway/JPA/repository conventions; no new dependency.

**Negative / trade-offs:**

- A small new table that must be cleaned up: expired rows are deleted by extending the existing daily `TokenCleanupJob` (BR-AUTH-015 pattern) or lazily on write.
- SHA-256 is fast, so an offline attacker with a DB dump could brute-force a 6-digit code in the 10-minute validity window — mitigated by the short window and by the code being useless once expired; bcrypt would slow this but breaks the constant-time-style hash-compare symmetry used for refresh tokens and adds cost for a 6-value-of-entropy secret.
- Online guessing of the 6-digit code within 10 minutes is feasible without rate limiting — accepted as Sprint 1 scope, flagged as a follow-up in the feature spec's risks.

## Verification

Integration tests cover all issue #22 acceptance criteria (valid, expired, invalid, resent OTP). Revisit if a security review flags OTP online guessing: add rate limiting / attempt counters rather than changing storage.
