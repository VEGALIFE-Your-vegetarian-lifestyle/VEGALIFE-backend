# Feature Spec: Login API + JWT Authentication

## Status
Draft

## Author / owner
Duy (backend)

## Summary
Implement JWT-based authentication with short-lived access tokens and long-lived opaque refresh tokens. Users log in with credentials to receive an access JWT and refresh token. Access tokens are validated via signature + optional blacklist; refresh tokens are validated via DB hash lookup with revocation support. Logout revokes the refresh token and optionally blacklists the current access token.

## Problem / motivation
Users need to authenticate and maintain sessions. JWT enables stateless auth for scalable microservices. The current implementation only has user registration and email verification (Issue #20). Login, token refresh, and logout are missing.

## Goals
- Users can log in with email/username + password and receive access + refresh tokens
- Short-lived access JWTs (5-15 min) with standard claims (sub, iss, aud, jti, iat, exp)
- Long-lived opaque refresh tokens (7+ days) with SHA-256 hash stored in DB
- Refresh endpoint issues new access tokens without rotating refresh token (initially)
- Logout revokes refresh token; optional immediate access-token revocation via blacklist
- Scheduled cleanup of expired tokens and blacklist entries

## Non-goals
- Refresh token rotation (reuse detection) — can be added later
- MFA / social login — separate issues
- Rate limiting on login — separate issue
- Access token storage in DB for normal operations — only blacklist on demand

## Requirements

### Functional Requirements
- [ ] FR-001: POST `/api/auth/login` accepts email/username + password, returns access token + refresh token
- [ ] FR-002: POST `/api/auth/refresh` accepts raw refresh token, returns new access token (same refresh token valid)
- [ ] FR-003: POST `/api/auth/logout` revokes refresh token; optionally blacklists current access token
- [ ] FR-004: Access JWT contains claims: sub (userId), iss (api issuer), aud (intended audience), jti (unique JWT ID), iat, exp
- [ ] FR-005: Refresh token is cryptographically secure random (>=256 bits), Base64URL encoded; only SHA-256 hash stored in DB
- [ ] FR-006: UNIQUE constraint on refresh_token.token_hash; collision handled by INSERT retry
- [ ] FR-007: Expired access token rejected (401) even if signature valid
- [ ] FR-008: Revoked/expired refresh token rejected on refresh (401)
- [ ] FR-009: Blacklisted access token rejected (401) even if signature + exp valid
- [ ] FR-010: Scheduled job deletes expired refresh tokens and expired blacklist entries daily

### Non-Functional Requirements
- [ ] NFR-SEC-001: Refresh token hash uses SHA-256 (not BCrypt) — high-entropy random token
- [ ] NFR-SEC-002: Access token secret separate from verification token secret
- [ ] NFR-SEC-003: Raw refresh token never logged or stored
- [ ] NFR-SEC-004: Blacklist entry never removed on subsequent login (stays until exp)
- [ ] NFR-SCALE-001: Token validation on hot path uses JWT parsing + blacklist lookup (no DB for normal access tokens)
- [ ] NFR-MAINT-001: Cleanup job idempotent, safe to run multiple times, efficient
- [ ] NFR-PERF-001: Access token validation p95 < 10ms (JWT parse + optional blacklist check)

## Design overview

### Components
1. **Database**: New `refresh_token` table (user_id, token_hash, expires_at, revoked_at, created_at); `blacklist_token` table (jti, issuer, expires_at, revoked_at)
2. **Token Service**: `JwtTokenService` — generates/validates access JWTs; generates/validates refresh tokens with DB storage
3. **Security Filter**: `JwtAuthenticationFilter` — extracts Bearer token, validates via JwtTokenService, sets SecurityContext
4. **AuthService**: Adds `login()`, `refreshToken()`, `logout()` methods
5. **AuthController**: Adds `POST /login`, `POST /refresh`, `POST /logout` endpoints
6. **DTOs**: `LoginRequest`, `LoginResponse` (accessToken, refreshToken, tokenType, expiresIn), `LogoutRequest`
7. **Cleanup Job**: `@Scheduled` job or Spring Batch to clean expired tokens

### Data Flow
**Login**:
```
credentials → verify password → generate access JWT (store nothing) → generate refresh token (store SHA-256 hash) → return both
```

**Refresh**:
```
raw refresh token → SHA-256 → find in DB → verify not revoked/expired → load user → generate new access JWT → return
```

**Logout**:
```
access token (from auth context) → extract jti/issuer → add to blacklist (optional) → find user's refresh token → set revoked_at
```

**Auth Filter** (per request):
```
Authorization: Bearer <token> → validate JWT signature/exp → check jti/issuer not in blacklist_token → set Authentication
```

### Database Schema

#### refresh_token
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK, gen_random_uuid() |
| user_id | UUID | FK → user(id), ON DELETE CASCADE |
| token_hash | CHAR(64) | NOT NULL, UNIQUE (SHA-256 hex) |
| expires_at | TIMESTAMPTZ | NOT NULL |
| revoked_at | TIMESTAMPTZ | NULLABLE |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

Indexes: idx_refresh_token_user_id (user_id), idx_refresh_token_hash (token_hash), idx_refresh_token_expires (expires_at)

#### blacklist_token (access token blacklist)
| Column | Type | Constraints |
|--------|------|-------------|
| jti | VARCHAR(36) | PK part 1 |
| issuer | VARCHAR(100) | PK part 2 |
| expires_at | TIMESTAMPTZ | NOT NULL |
| revoked_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

Primary Key: (jti, issuer)
Index: idx_blacklist_token_expires (expires_at)

## Success metrics
- Login success rate > 99.5%
- Token refresh success rate > 99%
- Access token validation p95 < 10ms
- Zero token leakage in logs
- Cleanup job runs daily without errors

## Acceptance criteria

**As a** registered user, **I want to** log in with my credentials **so that** I receive tokens to access protected APIs.

- [ ] Given valid credentials, when POST /api/auth/login, then returns 200 with accessToken + refreshToken
- [ ] Given invalid credentials, when POST /api/auth/login, then returns 401
- [ ] Given inactive/unverified user, when POST /api/auth/login, then returns 403

**As a** logged-in user, **I want to** refresh my access token **so that** I can continue accessing APIs without re-login.

- [ ] Given valid refresh token, when POST /api/auth/refresh, then returns 200 with new accessToken
- [ ] Given expired refresh token, when POST /api/auth/refresh, then returns 401
- [ ] Given revoked refresh token, when POST /api/auth/refresh, then returns 401
- [ ] Given same refresh token used concurrently, when multiple POST /api/auth/refresh, then all succeed (non-rotating)

**As a** logged-in user, **I want to** log out **so that** my tokens are invalidated.

- [ ] Given valid session, when POST /api/auth/logout, then refresh token revoked AND access token blacklisted
- [ ] After logout, when using old access token on protected endpoint, then returns 401 (blacklisted)
- [ ] After logout, when using old refresh token, then returns 401

**As a** system, **I want** expired tokens cleaned up **so that** DB doesn't grow unbounded.

- [ ] Given expired refresh tokens exist, when cleanup runs, then they are deleted
- [ ] Given revoked but unexpired refresh tokens exist, when cleanup runs, then they are preserved
- [ ] Given expired blacklist_token entries exist, when cleanup runs, then they are deleted

## Risks / open questions
- Blacklist_token table growth: depends on logout frequency; cleanup handles expiration
- Concurrent refresh with non-rotating token: acceptable per design, but client should implement single-flight
- Future: Migrate blacklist to Redis for distributed deployment with TTL-based expiry
- Separate JWT secrets for access vs verification tokens? → Yes, separate config keys
- Refresh token expiry: 7 days (configurable) vs 30 days? → 7 days default