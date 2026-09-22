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
| 401 | Invalid credentials | "Invalid email/username or password" |
| 403 | Account not activated | "Email not verified. Please verify your email before logging in." |
| 403 | Account suspended/deactivated | "Account is not active" |
| 500 | Server error | "Internal server error" |

## Business Rules
- BR-AUTH-008: Login Requires Valid Credentials and Activated Account
- BR-AUTH-009: Access Token Short Lifetime (15 minutes)
- BR-AUTH-010: Refresh Token Long Lifetime (7 days)
- BR-AUTH-011: Refresh Token Opaque Random with SHA-256 Hash

## Flow
1. Validate request body (identifier + password present)
2. Find user by email OR username
3. If user not found → 401 (generic message to prevent enumeration)
4. Verify password with BCrypt
5. If password invalid → 401 (generic message)
6. Check `emailVerified == true` and `status == ACTIVATED`
7. If not verified/active → 403
8. Generate access JWT (claims: sub, iss, aud, jti, iat, exp)
9. Generate refresh token (SecureRandom 32 bytes → Base64URL)
10. Store SHA-256(refreshToken) in `refresh_token` table with `expires_at = now + 7d`
11. Update `user.last_login_at = NOW()`
12. Return 200 with tokens

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

### Error Response (401 - Invalid Credentials)
```json
{
  "success": false,
  "message": "Invalid email/username or password",
  "data": null
}
```

### Error Response (403 - Not Verified)
```json
{
  "success": false,
  "message": "Email not verified. Please verify your email before logging in.",
  "data": null
}
```

## Related
- Feature Spec: `docs/feats/login-jwt-auth.md`
- ADR: `docs/adrs/002-jwt-authentication-architecture.md`
- Business Rules: `docs/brs/auth.md`