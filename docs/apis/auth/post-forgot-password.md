# API Reference: POST /api/auth/forgot-password

## Overview
Request a password-reset OTP for a registered email. Always returns the same success response whether or not the account exists (prevents account enumeration); when the account exists, a 6-digit code valid for 10 minutes is queued for asynchronous delivery (ADR-005).

## Endpoint
```
POST /api/auth/forgot-password
```

## Authentication
None (public endpoint)

## Request

### Path Parameters
None

### Query Parameters
None

### Request Body
```json
{
  "email": "string — registered email address"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| email | string | Yes | Not blank, valid email format, max 100 chars |

## Responses

### Success Response (200 OK)
```json
{
  "success": true,
  "message": "If an account with that email exists, a password reset code has been sent",
  "data": null
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | Generic anti-enumeration message — identical for known and unknown emails |
| data | object | Always null |

> The identical 200 is returned for unregistered emails and for emails whose OTP was queued for delivery. Calling this endpoint again supersedes any previous unused OTP for the account.

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Validation failed (missing/malformed email) | "Validation failed" |
| 500 | Server error | "Internal server error" |

## Business Rules
- BR-AUTH-007: Email Format and Length (valid, max 100 chars)
- BR-AUTH-017: Password Reset OTP Lifecycle (6-digit, 10 minutes, single-use, latest supersedes)
- BR-AUTH-018: Forgot Password Does Not Reveal Account Existence
- BR-AUTH-019: Password Reset OTP Stored as SHA-256 Hash

## Flow
1. Validate request body (`email` present and well-formed)
2. Look up user by email; if not found → still return the generic 200 (no email sent)
3. If found: mark any existing unused OTP rows for the user as used (supersede)
4. Generate a 6-digit numeric OTP (SecureRandom), store SHA-256(otp) with `expires_at = now + 10 minutes` (`app.password-reset.otp-expiry-minutes`)
5. Enqueue the OTP email on the outbound message queue (same transaction — delivered asynchronously with retries; SMTP failure never fails the request, ADR-005)
6. Return 200 with the generic message

## Example

### Request
```bash
curl -X POST http://localhost:8080/api/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com"}'
```

### Success Response (200)
```json
{
  "success": true,
  "message": "If an account with that email exists, a password reset code has been sent",
  "data": null
}
```

### Error Response (400)
```json
{
  "success": false,
  "message": "Validation failed",
  "data": null
}
```

## Related
- Feature Spec: `docs/feats/forgot-password-reset.md`
- ADR: `docs/adrs/003-password-reset-otp-storage.md`
- Counterpart endpoint: `docs/apis/auth/post-reset-password.md`
- Business Rules: `docs/brs/auth.md`
