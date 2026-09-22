# API Reference: POST /api/auth/refresh

## Overview
Refresh an expired access token using a valid refresh token. Returns a new access token. The refresh token is non-rotating initially (same token remains valid).

## Endpoint
```
POST /api/auth/refresh
```

## Authentication
None (public endpoint, refresh token passed in request body)

## Request

### Path Parameters
None

### Query Parameters
None

### Request Body
```json
{
  "refreshToken": "string — opaque refresh token from login"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| refreshToken | string | Yes | Not blank, valid refresh token |

## Responses

### Success Response (200 OK)
```json
{
  "success": true,
  "message": "Token refreshed successfully",
  "data": {
    "accessToken": "string — new JWT access token",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | "Token refreshed successfully" |
| data.accessToken | string | New short-lived JWT (15 min) |
| data.tokenType | string | Always "Bearer" |
| data.expiresIn | integer | Access token lifetime in seconds (900) |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Validation failed | "Validation failed" |
| 400 | Missing refresh token | "Refresh token is required" |
| 401 | Invalid refresh token | "Invalid refresh token" |
| 401 | Expired refresh token | "Refresh token has expired" |
| 401 | Revoked refresh token | "Refresh token has been revoked" |
| 500 | Server error | "Internal server error" |

## Business Rules
- BR-AUTH-010: Refresh Token Long Lifetime (7 days)
- BR-AUTH-011: Refresh Token Opaque Random with SHA-256 Hash
- BR-AUTH-012: Non-Rotating Refresh Token Initially

## Flow
1. Validate request body (refreshToken present)
2. Compute SHA-256 hash of provided refresh token
3. Look up `refresh_token` by `token_hash`
4. If not found → 401 "Invalid refresh token"
5. If `revoked_at` is not null → 401 "Refresh token has been revoked"
6. If `expires_at < NOW()` → 401 "Refresh token has expired"
7. Load associated user
9. Generate new access JWT (fresh jti, iat, exp)
10. Return 200 with new access token (same refresh token remains valid)

## Example

### Request
```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "aBcDeFgHiJkLmNoPqRsTuVwXyZ1234567890AbCdEfGhIjKlMnOpQrStUvWxYz"
  }'
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Token refreshed successfully",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI1NTBlODQwMC1lMjliLTQxZDQtYTcxNi00NDY2NTU0NDAwMDAiLCJpc3MiOiJ2ZWdhbGlmZS1iYWNrZW5kIiwiYXVkIjoidmVnYWxpZmUtYXBpIiwianRpIjoiZGVmNDU2Nzg5LWFhYmMtNGRlZi05YmMxLTIzNDU2Nzg5MDEyMyIsImlhdCI6MTcwMDAwMDg5OSwiZXhwIjoxNzAwMDAxNzk5fQ.newsignature",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
}
```

### Error Response (401 - Expired)
```json
{
  "success": false,
  "message": "Refresh token has expired",
  "data": null
}
```

### Error Response (401 - Revoked)
```json
{
  "success": false,
  "message": "Refresh token has been revoked",
  "data": null
}
```

## Related
- Feature Spec: `docs/feats/login-jwt-auth.md`
- ADR: `docs/adrs/002-jwt-authentication-architecture.md`
- Business Rules: `docs/brs/auth.md`