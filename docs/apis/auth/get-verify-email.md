# API Reference: GET /api/auth/verify-email

## Overview
Verify user's email address using the token sent via verification email. Activates the account on successful verification.

## Endpoint
```
GET /api/auth/verify-email
```

## Authentication
None (public endpoint, token passed as query parameter)

## Request

### Path Parameters
None

### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| token | string | Yes | JWT verification token from email |

### Request Body
No request body

## Responses

### Success Response (200 OK) — First-time Verification
```json
{
  "success": true,
  "message": "Email verified successfully",
  "data": {
    "userId": "uuid",
    "username": "string",
    "email": "string"
  }
}
```

### Success Response (200 OK) — Already Verified
```json
{
  "success": true,
  "message": "Email already verified. You can now log in.",
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
| message | string | "Email verified successfully" or "Email already verified. You can now log in." |
| data.userId | uuid | Unique user identifier |
| data.username | string | Registered username |
| data.email | string | Registered email |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Invalid token | "Invalid verification link." |
| 400 | Expired token | "Verification link has expired. Please register again." |
| 404 | User not found | "User not found" |
| 500 | Server error | "Internal server error" |

## Business Rules
- BR-AUTH-003: Email Verification Required for Activation
- BR-AUTH-004: Verification Token Expiration (24 hours)

## Flow
1. Receive token from query parameter
2. Validate token signature and expiry (BR-AUTH-004)
3. Extract userId from token
4. Find user by ID
5. If user not found → 404 (BR-AUTH-003)
6. If already verified → return 200 with "already verified" message
7. Set emailVerified=true, status=ACTIVATED (BR-AUTH-003)
8. Save user
9. Return 200 with user data

## Example

### Request
```bash
curl "http://localhost:8080/api/auth/verify-email?token=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
```

### Success Response (200 - First Verification)
```json
{
  "success": true,
  "message": "Email verified successfully",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "username": "johndoe",
    "email": "john@example.com"
  }
}
```

### Success Response (200 - Already Verified)
```json
{
  "success": true,
  "message": "Email already verified. You can now log in.",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "username": "johndoe",
    "email": "john@example.com"
  }
}
```

### Error Response (400 - Invalid Token)
```json
{
  "success": false,
  "message": "Invalid verification link.",
  "data": null
}
```

### Error Response (400 - Expired Token)
```json
{
  "success": false,
  "message": "Verification link has expired. Please register again.",
  "data": null
}
```

### Error Response (404 - User Not Found)
```json
{
  "success": false,
  "message": "User not found",
  "data": null
}
```

## Related
- Feature Spec: `docs/feats/user-registration.md`
- ADR: `docs/adrs/001-user-registration-architecture.md`
- Business Rules: `docs/brs/auth.md`