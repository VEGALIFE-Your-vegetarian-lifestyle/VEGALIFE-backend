# API Reference: POST /api/auth/reset-password

## Overview
Set a new password using the 6-digit OTP previously issued by `POST /api/auth/forgot-password`. Consumes the OTP (single-use), updates the password, and revokes all of the user's refresh tokens.

## Endpoint
```
POST /api/auth/reset-password
```

## Authentication
None (public endpoint) — possession of a valid, unexpired OTP is the authorization.

## Request

### Path Parameters
None

### Query Parameters
None

### Request Body
```json
{
  "email": "string — the account's registered email",
  "otp": "string — 6-digit numeric code from the email",
  "newPassword": "string — replacement password"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| email | string | Yes | Not blank, valid email format, max 100 chars |
| otp | string | Yes | Exactly 6 digits (`^\d{6}$`) |
| newPassword | string | Yes | Not blank, 8–100 characters |

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
| 400 | OTP unknown, wrong, or already used | "Invalid or already used password reset code" |
| 500 | Server error | "Internal server error" |

> Business errors are raised as `ExpiredTokenException` / `InvalidTokenException` and mapped to HTTP 400 by `GlobalExceptionHandler` (same convention as login).

## Business Rules
- BR-AUTH-005: Password Minimum Length (8 chars)
- BR-AUTH-007: Email Format and Length (valid, max 100 chars)
- BR-AUTH-017: Password Reset OTP Lifecycle (6-digit, 10 minutes, single-use, latest supersedes)
- BR-AUTH-019: Password Reset OTP Stored as SHA-256 Hash
- BR-AUTH-020: Password Reset Revokes Refresh Tokens

## Flow
1. Validate request body (email, otp format, password length)
2. Find user by email; if not found → 400 "Invalid or already used password reset code" (same message as a bad OTP — no enumeration)
3. Load the user's latest unused OTP row; if none → same 400 invalid message
4. If `expires_at <= now` → 400 "Password reset code has expired. Please request a new one."
5. Compare SHA-256(submitted otp) with stored hash; mismatch → 400 invalid message
6. Set `user.password_hash = BCrypt(newPassword)`
7. Mark the OTP row `used_at = now`
8. Revoke all active refresh tokens for the user (`JwtTokenService.revokeAllUserRefreshTokens`)
9. Return 200

## Example

### Request
```bash
curl -X POST http://localhost:8080/api/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","otp":"482913","newPassword":"newSecurePass123"}'
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

### Error Response (400 — Invalid)
```json
{
  "success": false,
  "message": "Invalid or already used password reset code",
  "data": null
}
```

## Related
- Feature Spec: `docs/feats/forgot-password-reset.md`
- ADR: `docs/adrs/003-password-reset-otp-storage.md`
- Counterpart endpoint: `docs/apis/auth/post-forgot-password.md`
- Business Rules: `docs/brs/auth.md`
