# Business Rules: Authentication (Auth)

## Rule Index

| Rule ID | Title | Status | Last Reviewed |
|---------|-------|--------|---------------|
| BR-AUTH-001 | Unique User Identity | Active | 2026-09-22 |
| BR-AUTH-002 | Password Confirmation Match | Active | 2026-09-22 |
| BR-AUTH-003 | Email Verification Required for Activation | Active | 2026-09-22 |
| BR-AUTH-004 | Verification Token Expiration (24h) | Active | 2026-09-22 |
| BR-AUTH-005 | Password Minimum Length (8 chars) | Active | 2026-09-22 |
| BR-AUTH-006 | Username Format and Length (3-50 chars) | Active | 2026-09-22 |
| BR-AUTH-007 | Email Format and Length (valid, max 100 chars) | Active | 2026-09-22 |
| BR-AUTH-008 | Login Requires Valid Credentials and Activated Account | Active | 2026-09-22 |
| BR-AUTH-009 | Access Token Short Lifetime (15 minutes) | Active | 2026-09-22 |
| BR-AUTH-010 | Refresh Token Long Lifetime (7 days) | Active | 2026-09-22 |
| BR-AUTH-011 | Refresh Token Opaque Random with SHA-256 Hash | Active | 2026-09-22 |
| BR-AUTH-012 | Non-Rotating Refresh Token Initially | Active | 2026-09-22 |
| BR-AUTH-013 | Logout Revokes Refresh Token | Active | 2026-09-22 |
| BR-AUTH-014 | Access Token Blacklist on Demand | Active | 2026-09-22 |
| BR-AUTH-015 | Expired Token Cleanup Daily | Active | 2026-09-22 |

---

# Business Rule: Unique User Identity

## Rule ID
`BR-AUTH-001`

## Status
Active

## Statement
A user's email and username must each be unique across the system.

## Rationale
Prevents account confusion, ensures reliable login/identification, and supports password recovery via email.

## Scope & Exceptions
Applies to all user registrations. No exceptions.

## Enforcement
- `AuthService.register()` checks `userRepository.existsByEmail()` and `existsByUsername()` before creation
- Database: Unique constraints on `user.email` and `user.username`
- API: Returns 409 Conflict with message "Email already registered" or "Username already taken"

## Last Reviewed
2026-09-22, by <name/role>

---

# Business Rule: Password Confirmation Match

## Rule ID
`BR-AUTH-002`

## Status
Active

## Statement
The `confirmPassword` field must exactly match the `password` field during registration.

## Rationale
Reduces typos during account creation, preventing lockout due to mistyped passwords.

## Scope & Exceptions
Applies to all registration requests. No exceptions.

## Enforcement
- DTO: `@FieldsEqual({"password", "confirmPassword"})` on `RegisterRequest`
- Jakarta Validation: Class-level constraint validator
- API: Returns 400 Bad Request with "Fields must be equal"

## Last Reviewed
2026-09-22, by <name/role>

---

# Business Rule: Email Verification Required for Activation

## Rule ID
`BR-AUTH-003`

## Status
Active

## Statement
A newly registered user starts with `emailVerified=false` and `status=CREATED`. The account must be activated by verifying the email via token before full access.

## Rationale
Ensures valid email ownership, reduces fake/spam accounts, enables reliable communication.

## Scope & Exceptions
Applies to all user registrations. Future: Admin-created users may bypass.

## Enforcement
- `AuthService.register()`: Sets `emailVerified=false`, `status=CREATED`
- `AuthService.verifyEmail()`: On valid token, sets `emailVerified=true`, `status=ACTIVATED`
- Future: Auth guards should check `emailVerified` and `status=ACTIVATED`

## Last Reviewed
2026-09-22, by <name/role>

---

# Business Rule: Verification Token Expiration (24h)

## Rule ID
`BR-AUTH-004`

## Status
Active

## Statement
Email verification tokens expire after 24 hours.

## Rationale
Limits exposure window for token leakage, encourages prompt verification.

## Scope & Exceptions
Applies to all email verification tokens. No exceptions.

## Enforcement
- `VerificationTokenService.generateToken()`: Sets 24h expiration in JWT
- `VerificationTokenService.getUserIdFromToken()`: Throws `ExpiredTokenException` if expired
- `AuthService.verifyEmail()`: Catches and re-throws with user-friendly message

## Last Reviewed
2026-09-22, by <name/role>

---

# Business Rule: Password Minimum Length (8 chars)

## Rule ID
`BR-AUTH-005`

## Status
Active

## Statement
Passwords must be at least 8 characters long.

## Rationale
Basic security baseline against brute-force attacks.

## Scope & Exceptions
Applies to all password fields in registration. No exceptions.

## Enforcement
- DTO: `@Size(min=8, max=100)` on `RegisterRequest.password`
- API: Returns 400 with "Password must be at least 8 characters"

## Last Reviewed
2026-09-22, by <name/role>

---

# Business Rule: Username Format and Length (3-50 chars)

## Rule ID
`BR-AUTH-006`

## Status
Active

## Statement
Usernames must be 3-50 characters.

## Rationale
Prevents extremely short/long usernames, ensures display compatibility.

## Scope & Exceptions
Applies to all username fields in registration. No exceptions.

## Enforcement
- DTO: `@Size(min=3, max=50)` on `RegisterRequest.username`
- API: Returns 400 with "Username must be between 3 and 50 characters"

## Last Reviewed
2026-09-22, by <name/role>

---

# Business Rule: Email Format and Length (valid, max 100 chars)

## Rule ID
`BR-AUTH-007`

## Status
Active

## Statement
Emails must be valid format per RFC 5322 and not exceed 100 characters.

## Rationale
Ensures deliverability, prevents storage issues.

## Scope & Exceptions
Applies to all email fields in registration. No exceptions.

## Enforcement
- DTO: `@Email` + `@Size(max=100)` on `RegisterRequest.email`
- API: Returns 400 with "Email must be valid" / "Email must not exceed 100 characters"

## Last Reviewed
2026-09-22, by <name/role>

---

# Business Rule: Login Requires Valid Credentials and Activated Account

## Rule ID
`BR-AUTH-008`

## Status
Active

## Statement
A user can only log in with valid email/username and password. The account must have `emailVerified=true` and `status=ACTIVATED`.

## Rationale
Prevents unverified or deactivated accounts from accessing protected resources.

## Scope & Exceptions
Applies to all login attempts. No exceptions.

## Enforcement
- `AuthService.login()`: Verifies password with `PasswordEncoder`, checks `user.getEmailVerified()` and `user.getStatus() == ACTIVATED`
- Returns 401 for invalid credentials, 403 for unverified/inactive account

## Last Reviewed
2026-09-22, by <name/role>

---

# Business Rule: Access Token Short Lifetime (15 minutes)

## Rule ID
`BR-AUTH-009`

## Status
Active

## Statement
JWT access tokens expire 15 minutes after issuance.

## Rationale
Limits exposure window if access token is intercepted. Short lifetime balances security with user experience.

## Scope & Exceptions
Applies to all access tokens issued via login or refresh. Configurable via `app.auth.access-token-expiry-minutes`.

## Enforcement
- `JwtTokenService.generateAccessToken()`: Sets `exp` claim to `now + 15 minutes`
- `JwtTokenService.validateAccessToken()`: Rejects if `exp` in past (throws `ExpiredTokenException`)
- Security filter returns 401 for expired access tokens

## Last Reviewed
2026-09-22, by <name/role>

---

# Business Rule: Refresh Token Long Lifetime (7 days)

## Rule ID
`BR-AUTH-010`

## Status
Active

## Statement
Refresh tokens expire 7 days after issuance.

## Rationale
Allows users to stay logged in across sessions without frequent re-authentication. Long enough for weekly usage, short enough to limit exposure.

## Scope & Exceptions
Applies to all refresh tokens issued at login. Configurable via `app.auth.refresh-token-expiry-days`.

## Enforcement
- `JwtTokenService.generateRefreshToken()`: Stores `expires_at = now + 7 days` in `refresh_token` table
- `JwtTokenService.validateRefreshToken()`: Rejects if `expires_at < NOW()`

## Last Reviewed
2026-09-22, by <name/role>

---

# Business Rule: Refresh Token Opaque Random with SHA-256 Hash

## Rule ID
`BR-AUTH-011`

## Status
Active

## Statement
Refresh tokens are cryptographically secure random values (>=256 bits, Base64URL encoded). Only SHA-256 hash stored in database.

## Rationale
High-entropy random tokens don't need slow hashing (BCrypt). SHA-256 is fast and collision-resistant. Raw token never persisted.

## Scope & Exceptions
Applies to all refresh token generation. No exceptions.

## Enforcement
- `JwtTokenService.generateRefreshToken()`: Uses `SecureRandom` for 32 bytes, Base64URL encodes, hashes with SHA-256
- `refresh_token.token_hash` column: CHAR(64) UNIQUE (hex SHA-256)
- Raw token returned to client only once at creation

## Last Reviewed
2026-09-22, by <name/role>

---

# Business Rule: Non-Rotating Refresh Token Initially

## Rule ID
`BR-AUTH-012`

## Status
Active

## Statement
The same refresh token remains valid for multiple `/refresh` calls until it expires or is revoked.

## Rationale
Simpler implementation without token family/reuse detection. Client should implement single-flight refresh.

## Scope & Exceptions
Applies to initial implementation. Rotation may be added in future enhancement.

## Enforcement
- `JwtTokenService.validateRefreshToken()`: Does not revoke token after successful validation
- `/refresh` endpoint returns new access token, same refresh token
- Collision handled by UNIQUE constraint + INSERT retry

## Last Reviewed
2026-09-22, by <name/role>

---

# Business Rule: Logout Revokes Refresh Token

## Rule ID
`BR-AUTH-013`

## Status
Active

## Statement
On logout, the user's refresh token is revoked by setting `revoked_at = NOW()`.

## Rationale
Prevents further use of refresh token to obtain new access tokens. Effectively ends the session.

## Scope & Exceptions
Applies to explicit logout calls. Optional: also blacklist current access token.

## Enforcement
- `AuthService.logout()`: Finds user's active refresh token, sets `revoked_at`
- Optional: `JwtTokenService.blacklistAccessToken(jti, issuer, exp)` if blacklist enabled
- Returns 200 on success

## Last Reviewed
2026-09-22, by <name/role>

---

# Business Rule: Access Token Always Blacklisted on Logout

## Rule ID
`BR-AUTH-014`

## Status
Active

## Statement
On logout, the JWT's `jti` and `issuer` are stored in `blacklist_token` table. Blacklisted tokens are rejected on all subsequent requests even if signature and `exp` are valid. Blacklist is always enabled — no config flag.

## Rationale
Enables instant access token invalidation without waiting for natural expiry. Blacklist entry persists until token's natural expiration. Always-on ensures consistent security posture.

## Scope & Exceptions
Applies to all logout requests. Blacklist check runs on every authenticated request.

## Enforcement
- `JwtTokenService.blacklistAccessToken(jti, issuer, exp)`: Inserts into `blacklist_token`
- `JwtAuthenticationFilter`: Always checks blacklist, returns 401 if found
- Blacklist entry never removed on re-login — stays until `expires_at`

## Last Reviewed
2026-09-22, by <name/role>

---

# Business Rule: Expired Token Cleanup Daily

## Rule ID
`BR-AUTH-015`

## Status
Active

## Statement
A scheduled job runs daily to delete expired refresh tokens and expired blacklist entries. Revoked but unexpired refresh tokens are preserved.

## Rationale
Prevents unbounded table growth. Preserves audit trail for revoked tokens until natural expiry.

## Scope & Exceptions
Runs daily via `@Scheduled`. Idempotent and safe to run multiple times.

## Enforcement
- `TokenCleanupJob.cleanup()`: 
  - `DELETE FROM refresh_token WHERE expires_at < NOW()`
  - `DELETE FROM blacklist_token WHERE expires_at < NOW()`
- Indexes on `expires_at` for efficient cleanup

## Last Reviewed
2026-09-22, by <name/role>