# API Reference: POST /api/auth/logout

## Overview
Log out the current user by revoking their refresh token and blacklisting the current access token for immediate revocation.

## Endpoint
```
POST /api/auth/logout
```

## Authentication
Bearer token (JWT access token in Authorization header)

## Request

### Path Parameters
None

### Query Parameters
None

### Request Body
```json
{}
```
(Empty body — access token is extracted from Authorization header)

## Responses

### Success Response (200 OK)
```json
{
  "success": true,
  "message": "Logged out successfully",
  "data": null
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | "Logged out successfully" |
| data | null | No data returned |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | Invalid/expired access token | "Invalid or expired access token" |
| 401 | Blacklisted access token | "Access token has been revoked" |
| 500 | Server error | "Internal server error" |

## Business Rules
- BR-AUTH-013: Logout Revokes Refresh Token
- BR-AUTH-014: Access Token Always Blacklisted on Logout

## Flow
1. Extract access token from Authorization header (validated by JwtAuthenticationFilter)
2. Get user from SecurityContext (set by filter)
3. Extract `jti` and `issuer` from current access token
4. Call `JwtTokenService.blacklistAccessToken(jti, issuer, exp)` — adds to `blacklist_token` table
5. Find user's active refresh token (`revoked_at IS NULL`)
6. If found, set `revoked_at = NOW()`
7. Return 200 success

## Example

### Request
```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI1NTBlODQwMC1lMjliLTQxZDQtYTcxNi00NDY2NTU0NDAwMDAiLCJpc3MiOiJ2ZWdhbGlmZS1iYWNrZW5kIiwiYXVkIjoidmVnYWxpZmUtYXBpIiwianRpIjoiYWIxMmMzZDQtNTZlZi00YzY3LTk4YmQtMTIzNDU2Nzg5MDEyMyIsImlhdCI6MTY5OTk5OTk5OSwiZXhwIjoxNzAwMDAwODk5fQ.signature" \
  -H "Content-Type: application/json" \
  -d '{}'
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Logged out successfully",
  "data": null
}
```

## Notes
- Logout **always** revokes the refresh token AND blacklists the current access token
- Blacklist is always enabled — no config flag
- Blacklist entry persists until token's natural `exp` — never removed on re-login
- Future: Blacklist may be migrated to Redis for distributed deployment with TTL-based expiry

## Related
- Feature Spec: `docs/feats/login-jwt-auth.md`
- ADR: `docs/adrs/002-jwt-authentication-architecture.md`
- Business Rules: `docs/brs/auth.md`