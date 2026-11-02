# ADR-002: JWT Authentication Architecture Decisions

## Status
Accepted

## Context
The Vegalife backend requires JWT-based authentication with access tokens and refresh tokens. This ADR documents the key architectural decisions for the login, token refresh, and logout flows (Issue #21).

## Decisions

### 1. Access Token: Short-Lived JWT (Not Stored in DB)

**Decision**: Access tokens are JWTs with 15-minute lifetime. They are validated via signature + expiration only — NOT stored in the database for normal operations.

**Rationale**:
- Stateless validation scales horizontally without DB round-trips
- Short lifetime (15 min) limits exposure if token leaked
- JWT carries user claims (sub, iss, aud, jti, iat, exp) natively
- Blacklist only used when immediate revocation required (logout)

**Consequences**:
- Access token validation = JWT parse + signature verify + exp check (+ optional blacklist lookup)
- Cannot revoke individual access tokens before expiry without blacklist
- Token size grows with claims — keep claims minimal

### 2. Refresh Token: Opaque Random Token with SHA-256 Hash in DB

**Decision**: Refresh tokens are cryptographically secure random values (>=256 bits, Base64URL encoded). Only SHA-256 hash stored in `refresh_token` table.

**Rationale**:
- High-entropy random tokens don't need BCrypt (no password-like low entropy)
- SHA-256 is fast for hashing/validation, BCrypt unnecessarily slow
- Opaque tokens reveal no information if leaked from DB
- DB storage enables revocation, expiration tracking, audit

**Consequences**:
- Refresh token validation = SHA-256 hash lookup in DB + expiry/revoked check
- UNIQUE constraint on `token_hash` handles collision via INSERT retry
- Non-rotating initially (same refresh token valid until expiry/revocation)

### 3. Blacklist Table: `blacklist_token` (Renamed from `revoked_token`)

**Decision**: Access token blacklist table named `blacklist_token` with composite PK `(jti, issuer)`. Stores JWT ID and issuer, not full token.

**Rationale**:
- `blacklist_token` is clearer than `revoked_token` (revoked_token implies refresh tokens)
- JWT's `jti` + `issuer` uniquely identifies a token — minimal storage
- Blacklist entry persists until token's natural expiry (never removed on re-login)
- Blacklist **always enabled** — no config flag. Filter always checks blacklist on authenticated requests.

**Consequences**:
- Blacklist lookup = single index seek on `(jti, issuer)` per authenticated request
- Cleanup job removes expired entries daily
- Future: Can migrate to Redis for distributed blacklist with TTL-based expiry

### 4. Non-Rotating Refresh Tokens (Initial Implementation)

**Decision**: Same refresh token remains valid for multiple `/refresh` calls until expiry or revocation.

**Rationale**:
- Simpler implementation — no token family/reuse detection complexity
- Client should implement single-flight refresh to avoid concurrent requests
- Rotation can be added later as separate enhancement

**Consequences**:
- Multiple concurrent `/refresh` with same token may all succeed
- Refresh token revocation = single DB update (`revoked_at = NOW()`)
- No need to track token families or detect reuse

### 5. Separate JWT Secrets: Access Token vs Verification Token

**Decision**: Access tokens use `app.auth.jwt.secret`; email verification tokens use existing `app.verification.secret`.

**Rationale**:
- Key separation limits blast radius if one secret compromised
- Different expiry/claim requirements (access: 15min, verification: 24h)
- Independent rotation policies

### 6. Security Filter: JwtAuthenticationFilter Before UsernamePasswordAuthenticationFilter

**Decision**: Custom `JwtAuthenticationFilter` added to Spring Security filter chain before standard auth filter.

**Rationale**:
- Stateless JWT auth replaces session-based auth for API endpoints
- Filter extracts Bearer token, validates via `JwtTokenService`, sets `SecurityContext`
- Public endpoints (`/api/auth/**`, `/actuator/**`, Swagger) remain permitAll

### 7. Separate DTOs: LoginResponse vs RegisterResponse

**Decision**: `LoginResponse` contains tokens; `RegisterResponse` (formerly `AuthResponse`) does not.

**Rationale**:
- Registration doesn't log user in — no tokens returned
- Clear semantic separation: register → verify → login
- API contracts explicit about what each endpoint returns

### 8. Cleanup Job: Scheduled Daily Cleanup

**Decision**: `@Scheduled` job runs daily to delete expired `refresh_token` and `blacklist_token` entries.

**Rationale**:
- Prevents unbounded table growth
- Preserves revoked-but-unexpired refresh tokens for audit
- Idempotent, safe to run multiple times
- Uses existing Spring scheduling (no new framework)

## Alternatives Considered

| Decision | Alternative | Rejected Because |
|----------|-------------|------------------|
| Access token in DB | Store all access tokens | DB round-trip on every request, kills scalability |
| Refresh token = JWT | JWT refresh tokens | Cannot revoke individually without DB lookup; opaque better for revocation |
| BCrypt for refresh hash | BCrypt all token hashes | Overkill for high-entropy random; SHA-256 sufficient |
| Rotating refresh tokens | Rotate on every refresh | Adds token family complexity; defer to future |
| Single auth table | One table for both token types | Different lifecycles, indexes, cleanup policies |
| Blacklist on every request | Always check blacklist | Blacklist always enabled for immediate revocation guarantee |

## Related
- Feature Spec: `docs/feats/login-jwt-auth.md`
- API Docs: `docs/apis/auth/post-login.md`, `docs/apis/auth/post-refresh.md`, `docs/apis/auth/post-logout.md`
- Business Rules: `docs/brs/auth.md` (BR-AUTH-008 through BR-AUTH-015)
- Data Dictionary: `docs/arch/data-dictionary.md` (Tables: refresh_token, blacklist_token)