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