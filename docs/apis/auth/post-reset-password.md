# API Reference: POST /api/auth/reset-password

## Overview
Second step of the two-step password reset: sets a new password once the OTP has been verified via `POST /api/auth/verify-password-reset`. Consumes the OTP (single-use), updates the password, and revokes all of the user's refresh tokens.

## Endpoint
```
POST /api/auth/reset-password
```

## Authentication
None (public endpoint) — a previously verified, unexpired OTP for that account is the authorization.

## Request

### Path Parameters
None

### Query Parameters
None

### Request Body
```json
{
  "email": "string — the account's registered email",
  "newPassword": "string — replacement password"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| email | string | Yes | Not blank, valid email format, max 100 chars |
| newPassword | string | Yes | Not blank, 8–100 characters |

> Since issue #62 the body no longer contains `otp` — the code was proven at the verify step. A legacy 3-field body `{email, otp, newPassword}` passes Jackson validation (unknown `otp` is ignored) but fails with 400 "Invalid or already used password reset code", because the row is not verified yet: old one-shot clients are hard-broken by design.

## Responses

### Success Response (200 OK)
```json
{
  "success": true,
  "message": "Password has been reset successfully",
  "data": null
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | Confirmation message |
| data | object | Always null |

After success the old password no longer works; all existing refresh tokens for the account are revoked, so other sessions must log in again.

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Validation failed | "Validation failed" |
| 400 | OTP expired (past `expires_at`) | "Password reset code has expired. Please request a new one." |
| 400 | Unknown email, no active OTP, OTP not verified, or already used | "Invalid or already used password reset code" |
| 500 | Server error | "Internal server error" |

> Business errors are raised as `ExpiredTokenException` / `InvalidTokenException` and mapped to HTTP 400 by `GlobalExceptionHandler` (same convention as login). There is no OTP-format row in this table anymore — the OTP is not part of this request.

## Business Rules
- BR-AUTH-005: Password Minimum Length (8 chars)
- BR-AUTH-007: Email Format and Length (valid, max 100 chars)
- BR-AUTH-017: Password Reset OTP Lifecycle (6-digit, 10 minutes, single-use, latest supersedes; verified at step 1, consumed here)
- BR-AUTH-019: Password Reset OTP Stored as SHA-256 Hash
- BR-AUTH-020: Password Reset Revokes Refresh Tokens (happens here, on success)

## Flow
1. Validate request body (email format, password length)
2. Find user by email; if not found → 400 "Invalid or already used password reset code" (same message as a bad OTP — no enumeration)
3. Load the user's latest unused OTP row; if none → same 400 invalid message
4. If `expires_at <= now` → 400 "Password reset code has expired. Please request a new one."
5. If `verified_at` is null (verify step never done) → 400 invalid message
6. Compare-and-set `used_at = now` where the row is still unused; if 0 rows affected (concurrent reset won) → 400 invalid message
7. Set `user.password_hash = BCrypt(newPassword)`
8. Revoke all active refresh tokens for the user (`JwtTokenService.revokeAllUserRefreshTokens`)
9. Return 200

## Example

### Request
```bash
curl -X POST http://localhost:8080/api/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","newPassword":"newSecurePass123"}'
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Password has been reset successfully",
  "data": null
}
```

### Error Response (400 — Expired)
```json
{
  "success": false,
  "message": "Password reset code has expired. Please request a new one.",
  "data": null
}
```

### Error Response (400 — Invalid / not verified)
```json
{
  "success": false,
  "message": "Invalid or already used password reset code",
  "data": null
}
```

## Related
- Feature Spec: `docs/feats/forgot-password-reset.md`
- ADR: `docs/adrs/006-optional-otp-verified-stage.md`
- Counterpart endpoints: `docs/apis/auth/post-forgot-password.md`, `docs/apis/auth/post-verify-password-reset.md`
- Business Rules: `docs/brs/auth.md`
