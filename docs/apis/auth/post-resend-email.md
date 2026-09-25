# API Reference: POST /api/auth/resend-email

## Overview
Request a fresh email-verification OTP for a registered, unverified account. Always returns the same success response whether or not the account exists or is already verified (prevents account enumeration); when the account exists and is unverified, a new 6-digit code valid for 10 minutes is emailed and supersedes any prior unused verification OTP for that user only (BR-AUTH-021).

## Endpoint
```
POST /api/auth/resend-email
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
  "email": "string — email address to resend verification code to"
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
  "message": "If an account with that email exists, a verification code has been sent",
  "data": null
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | Generic anti-enumeration message — identical for unknown, already-verified, and newly-issued cases |
| data | object | Always null |

> The identical 200 is returned for unregistered emails, already-verified accounts, and emails whose send path completed. No code is sent for unknown or already-verified accounts. Calling this endpoint again supersedes any previous unused verification OTP for the account (purpose `EMAIL_VERIFICATION` only — pending password-reset OTPs are unaffected).

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Validation failed (missing/malformed email) | "Validation failed" |
| 500 | Email delivery failure (OTP row rolled back with the transaction) | "Internal server error" |

## Business Rules
- BR-AUTH-007: Email Format and Length (valid, max 100 chars)
- BR-AUTH-017: OTP Lifecycle (6-digit, 10 min, single-use, per user per purpose)
- BR-AUTH-021: Email Verification OTP Lifecycle and Resend

## Examples

### Request
```http
POST /api/auth/resend-email
Content-Type: application/json

{
  "email": "user@example.com"
}
```

### cURL
```bash
curl -X POST http://localhost:8080/api/auth/resend-email \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com"}'
```
