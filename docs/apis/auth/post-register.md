# API Reference: POST /api/auth/register

## Overview
Register a new user account with username, email, and password. Queues a 6-digit email-verification OTP (no link) for asynchronous delivery (ADR-005) — registration succeeds even while the mail server is unreachable.

## Endpoint
```
POST /api/auth/register
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
  "username": "string — 3-50 characters, unique",
  "email": "string — valid email format, max 100 characters, unique",
  "password": "string — minimum 8 characters, max 100 characters",
  "confirmPassword": "string — must match password field exactly"
}
```

## Responses

### Success Response (201 Created)
```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "userId": "uuid",
    "username": "string",
    "email": "string"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | "User registered successfully" |
| data.userId | uuid | Unique user identifier |
| data.username | string | Registered username |
| data.email | string | Registered email |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Validation failed | "Validation failed" |
| 400 | Password mismatch | "Fields must be equal" |
| 409 | Duplicate email | "Email already registered" |
| 409 | Duplicate username | "Username already taken" |
| 500 | Server error | "Internal server error" |

## Validation Rules
- **username**: Required, 3-50 characters, unique (BR-AUTH-006)
- **email**: Required, valid email format, max 100 chars, unique (BR-AUTH-001, BR-AUTH-007)
- **password**: Required, minimum 8 characters, max 100 chars (BR-AUTH-005)
- **confirmPassword**: Required, must match password (BR-AUTH-002)

## Business Rules
- BR-AUTH-001: Unique User Identity (email and username)
- BR-AUTH-002: Password Confirmation Match
- BR-AUTH-005: Password Minimum Length (8 chars)
- BR-AUTH-006: Username Format and Length (3-50 chars)
- BR-AUTH-007: Email Format and Length (valid format, max 100 chars)

## Flow
1. Validate request body
2. Check email uniqueness (BR-AUTH-001)
3. Check username uniqueness (BR-AUTH-001)
4. Hash password with BCrypt
5. Create user with status=CREATED, emailVerified=false (BR-AUTH-003)
6. Issue 6-digit `EMAIL_VERIFICATION` OTP (10-min expiry, supersede prior unused verification OTPs for this user) (BR-AUTH-004, BR-AUTH-017, BR-AUTH-021)
7. Enqueue the verification email containing the OTP (no link) on the outbound message queue — delivered asynchronously by the background drainer with retries; SMTP failure never fails registration (ADR-005)
8. Return 201 with user data

## Example

### Request
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "johndoe",
    "email": "john@example.com",
    "password": "securePass123",
    "confirmPassword": "securePass123"
  }'
```

### Success Response (201)
```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "username": "johndoe",
    "email": "john@example.com"
  }
}
```

### Error Response (400 - Validation)
```json
{
  "success": false,
  "message": "Validation failed",
  "data": null
}
```

### Error Response (409 - Duplicate)
```json
{
  "success": false,
  "message": "Email already registered",
  "data": null
}
```

## Related
- Feature Spec: `docs/feats/user-registration.md`, `docs/feats/email-verification-otp.md`
- ADR: `docs/adrs/001-user-registration-architecture.md`, `docs/adrs/004-generalized-otp-storage.md`
- Business Rules: `docs/brs/auth.md`