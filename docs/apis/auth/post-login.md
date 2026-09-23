# API Reference: POST /api/auth/login

## Overview
Authenticate a user with email/username and password. Returns short-lived access token and long-lived refresh token.

## Endpoint
```
POST /api/auth/login
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
  "identifier": "string — email or username",
  "password": "string — user's password"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| identifier | string | Yes | Not blank, can be email or username |
| password | string | Yes | Not blank |

## Responses

### Success Response (200 OK)
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "userId": "uuid",
    "username": "string",
    "email": "string",
    "accessToken": "string — JWT access token",
    "refreshToken": "string — opaque refresh token",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | "Login successful" |
| data.userId | uuid | Unique user identifier |
| data.username | string | Registered username |
| data.email | string | Registered email |
| data.accessToken | string | Short-lived JWT (15 min) |
| data.refreshToken | string | Long-lived opaque token (7 days) |
| data.tokenType | string | Always "Bearer" |
| data.expiresIn | integer | Access token lifetime in seconds (900) |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Validation failed | "Validation failed" |
| 400 | Invalid credentials | "Invalid email/username or password" |
| 400 | Account not activated | "Email not verified. Please verify your email before logging in." |
| 400 | Account suspended | "Account is suspended" |
| 400 | Account deactivated | "Account is not active" |
| 500 | Server error | "Internal server error" |

> Note: login business errors are raised as `InvalidTokenException` and mapped to HTTP 400 by `GlobalExceptionHandler` (not 401/403).

## Business Rules
- BR-AUTH-008: Login Requires Valid Credentials and Activated Account
- BR-AUTH-009: Access Token Short Lifetime (15 minutes)
- BR-AUTH-010: Refresh Token Long Lifetime (7 days)
- BR-AUTH-011: Refresh Token Opaque Random with SHA-256 Hash

## Flow
1. Validate request body (identifier + password present)
2. Find user by email OR username
3. If user not found → 400 (generic message to prevent enumeration)
4. Verify password with BCrypt
5. If password invalid → 400 (generic message)
6. Check status: suspended → 400 "Account is suspended"; deactivated → 400 "Account is not active"
7. Check `emailVerified == true` and `status == ACTIVATED`
8. If not verified/active → 400
9. Generate access JWT (claims: sub, iss, aud, jti, iat, exp)
10. Generate refresh token (SecureRandom 32 bytes → Base64URL)
11. Store SHA-256(refreshToken) in `refresh_token` table with `expires_at = now + 7d`
12. Update `user.last_login_at = NOW()`
13. Return 200 with tokens

## Example

### Request
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "identifier": "johndoe",
    "password": "securePass123"
  }'
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "username": "johndoe",
    "email": "john@example.com",
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI1NTBlODQwMC1lMjliLTQxZDQtYTcxNi00NDY2NTU0NDAwMDAiLCJpc3MiOiJ2ZWdhbGlmZS1iYWNrZW5kIiwiYXVkIjoidmVnYWxpZmUtYXBpIiwianRpIjoiYWIxMmMzZDQtNTZlZi00YzY3LTk4YmQtMTIzNDU2Nzg5MDEyMyIsImlhdCI6MTY5OTk5OTk5OSwiZXhwIjoxNzAwMDAwODk5fQ.signature",
    "refreshToken": "aBcDeFgHiJkLmNoPqRsTuVwXyZ1234567890AbCdEfGhIjKlMnOpQrStUvWxYz",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
}
```

### Error Response (400 - Invalid Credentials)
```json
{
  "success": false,
  "message": "Invalid email/username or password",
  "data": null
}
```

### Error Response (400 - Not Verified)
```json
{
  "success": false,
  "message": "Email not verified. Please verify your email before logging in.",
  "data": null
}
```

### Error Response (400 - Suspended)
```json
{
  "success": false,
  "message": "Account is suspended",
  "data": null
}
```

## Related
- Feature Spec: `docs/feats/login-jwt-auth.md`
- ADR: `docs/adrs/002-jwt-authentication-architecture.md`
- Business Rules: `docs/brs/auth.md`