# API Reference: POST /api/auth/google

## Overview
Sign in with a Google account. Exchanges a Google authorization code for an ID
token, verifies it, then logs the matching Vegalife account in — creating one,
or auto-linking an existing verified account, when needed.

## Endpoint
```
POST /api/auth/google
```

## Authentication
None (public endpoint). The Google authorization code stands in for credentials.

## Request

### Path Parameters
None

### Query Parameters
None

### Request Body
```json
{
  "code": "string — the authorization code returned by Google Identity Services"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| code | string | Yes | Not blank |

## Responses

### Success Response (200 OK)
```json
{
  "success": true,
  "message": "Google login successful",
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

`data` is identical in shape to `POST /api/auth/login`, so a client can handle
both responses with one code path.

| Field | Type | Description |
|-------|------|-------------|
| data.userId | uuid | The Vegalife user id — the same one password login returns for a linked account |
| data.username | string | Account username (generated for a Google-created account) |
| data.email | string | Account email |
| data.accessToken | string | Short-lived JWT (15 min) |
| data.refreshToken | string | Long-lived opaque token (7 days) |
| data.tokenType | string | Always "Bearer" |
| data.expiresIn | integer | Access token lifetime in seconds (900) |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `code` missing or blank | "Validation failed" |
| 400 | Google rejected the code, or the token endpoint failed | "Google sign-in failed. Please try again." |
| 400 | ID token invalid (signature, issuer, audience, expiry) | "Google sign-in failed. Please try again." |
| 400 | ID token has no verified email | "Google account email is not verified" |
| 400 | Matched account's email is not verified | "Email not verified. Please verify your email before logging in." |
| 400 | Matched account is suspended | "Account is suspended" |
| 400 | Matched account is deactivated | "Account is not active" |
| 400 | Matched account is soft-deleted | "Invalid email/username or password" |
| 500 | Server error | "Internal server error" |

> Note: like the rest of `/api/auth/**`, business failures are raised as
> `InvalidTokenException`/`GoogleAuthException` and mapped to HTTP 400 by
> `GlobalExceptionHandler` (not 401).

## Business Rules
- BR-AUTH-001: Unique User Identity
- BR-AUTH-003: Email Verification Required for Activation
- BR-AUTH-006: Username Format and Length (3-50 chars)
- BR-AUTH-008: Login Requires Valid Credentials and Activated Account
- BR-AUTH-022: Google Sign-In Resolves to an Existing Identity or Auto-Links on Verified Email
- BR-AUTH-023: Accounts Created Through Google Start Verified and Activated

## Flow
1. Validate the request body (`code` not blank).
2. Exchange the code at Google's token endpoint; on failure → 400 generic message.
3. Verify the returned ID token: RS256 signature against Google's JWKS, `aud` = configured client id, `iss` ∈ Google issuers, `exp` with clock skew.
4. Require `email_verified=true` in the token → otherwise 400.
5. Look up `user_identity(provider=GOOGLE, provider_subject=sub)`.
   - Found → apply the account-state gate (same messages as `AuthService.login`) and sign in.
6. Else look up a user by the token's email.
   - Found → auto-link only when the token is verified **and** the account is `email_verified=true` and `status=activated`; otherwise → 400 with the matching message. Insert the identity row, log at WARN, sign in.
7. Else create the account (`email_verified=true`, `status=activated`, `password_hash=NULL`, generated username), insert the identity row, sign in.
8. Common tail: mint access + refresh tokens, stamp `last_login_at`, return 200.

## Example

### Request
```bash
curl -X POST http://localhost:8080/api/auth/google \
  -H "Content-Type: application/json" \
  -d '{"code":"4/0AfJohXn..."}'
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Google login successful",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "username": "johndoe",
    "email": "john.doe@gmail.com",
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "aBcDeFgHiJkLmNoPqRsTuVwXyZ1234567890",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
}
```

### Error Response (400 - Unverified Google email)
```json
{
  "success": false,
  "message": "Google account email is not verified",
  "data": null
}
```

## Related
- Feature Spec: `docs/feats/google-login.md`
- ADR: `docs/adrs/017-google-login.md`
- Business Rules: `docs/brs/auth.md`
