# API Reference: POST /api/auth/verify-password-reset

## Overview
First step of the two-step password reset: proves the caller holds the valid 6-digit OTP issued by `POST /api/auth/forgot-password` and marks it verified (`otp_code.verified_at`). It does not change the password and does not consume the OTP — consumption happens in `POST /api/auth/reset-password`.

## Endpoint
```
POST /api/auth/verify-password-reset
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
  "otp": "string — 6-digit numeric code from the email"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| email | string | Yes | Not blank, valid email format, max 100 chars |
| otp | string | Yes | Exactly 6 digits (`^\d{6}$`) |

## Responses

### Success Response (200 OK)
```json
{
  "success": true,
  "message": "Password reset code verified successfully",
  "data": null
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | Confirmation message |
| data | object | Always null |

The OTP row now has `verified_at` set while `used_at` remains null. Verifying twice is idempotent: a repeat call with the same code returns the same 200 (original `verified_at` kept). The password and refresh tokens are untouched — the old password still logs in until the reset step succeeds.

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Validation failed | "Validation failed" |
| 400 | OTP expired (past `expires_at`) | "Password reset code has expired. Please request a new one." |
| 400 | Unknown email, wrong, superseded, or already-used OTP | "Invalid or already used password reset code" |
| 500 | Server error | "Internal server error" |

> Business errors are raised as `ExpiredTokenException` / `InvalidTokenException` and mapped to HTTP 400 by `GlobalExceptionHandler` (same convention as login). An unknown email returns the same invalid message as a wrong code — no account enumeration.

## Business Rules
- BR-AUTH-017: OTP Lifecycle (6-digit, 10 minutes, single-use; verification sets `verified_at`, consumption sets `used_at` at the reset step)
- BR-AUTH-018: Forgot Password Does Not Reveal Account Existence (unknown email → same invalid message)
- BR-AUTH-019: Password Reset OTP Stored as SHA-256 Hash

## Flow
1. Validate request body (email format, otp 6 digits)
2. Find user by email; if not found → 400 "Invalid or already used password reset code" (same message as a bad OTP — no enumeration)
3. Load the user's latest unused OTP row; if none (never issued, superseded, or already consumed) → same 400 invalid message
4. If `expires_at <= now` → 400 "Password reset code has expired. Please request a new one."
5. Compare SHA-256(submitted otp) with stored hash; mismatch → 400 invalid message
6. If `verified_at` is already set → return 200 (idempotent, nothing written)
7. Set `verified_at = now` on the row; save
8. Return 200 — the OTP is verified but still unused

## Example

### Request
```bash
curl -X POST http://localhost:8080/api/auth/verify-password-reset \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","otp":"482913"}'
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Password reset code verified successfully",
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
- ADR: `docs/adrs/006-optional-otp-verified-stage.md`
- Counterpart endpoints: `docs/apis/auth/post-forgot-password.md`, `docs/apis/auth/post-reset-password.md`
- Business Rules: `docs/brs/auth.md`
