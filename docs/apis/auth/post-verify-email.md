# API Reference: POST /api/auth/verify-email

## Overview
Activate a newly registered account by submitting the 6-digit OTP emailed at registration. Replaces the former `GET /api/auth/verify-email?token=` link flow (ADR-001 §5, ADR-004, BR-AUTH-003, BR-AUTH-021).

## Endpoint
```
POST /api/auth/verify-email
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
  "email": "string — registered email address",
  "otp": "string — 6-digit verification code"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| email | string | Yes | Not blank, valid email format, max 100 chars |
| otp | string | Yes | Not blank, exactly 6 digits |

## Responses

### Success Response (200 OK)
```json
{
  "success": true,
  "message": "Email verified successfully",
  "data": {
    "id": "uuid",
    "email": "string",
    "username": "string",
    "emailVerified": true,
    "status": "activated",
    "createdAt": "timestamp",
    "updatedAt": "timestamp"
  }
}
```

> An already-verified account returns the same 200 without consuming an OTP (idempotent).

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Validation failed (missing/malformed email or otp) | "Validation failed" |
| 400 | Unknown email, wrong OTP, or already-used OTP (same message — anti-enumeration) | "Invalid or already used verification code" |
| 400 | Active OTP past `expires_at` | "Verification code has expired. Please request a new one." |

## Business Rules
- BR-AUTH-003: Email Verification Required for Activation
- BR-AUTH-004: Email Verification OTP Expiration (10 min)
- BR-AUTH-017: OTP Lifecycle (6-digit, 10 min, single-use, per user per purpose)
- BR-AUTH-019: OTP Stored as SHA-256 Hash
- BR-AUTH-021: Email Verification OTP Lifecycle and Resend

## Examples

### Request
```http
POST /api/auth/verify-email
Content-Type: application/json

{
  "email": "user@example.com",
  "otp": "123456"
}
```

### cURL
```bash
curl -X POST http://localhost:8080/api/auth/verify-email \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","otp":"123456"}'
```
