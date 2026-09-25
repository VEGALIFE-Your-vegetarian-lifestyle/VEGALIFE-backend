# Feature Specification: User Registration with Email Verification

## Overview

Implement user registration for the Vegalife platform with email verification flow. Users can create an account with username, email, and password, then verify their email via a token sent to their inbox.

## Actors

- **Anonymous User** - Unauthenticated visitor registering for an account
- **System** - Backend services handling registration, token generation, email delivery

## Functional Requirements

### FR-01: User Registration

**Description**: Anonymous users can register with username, email, password, and password confirmation.

**Acceptance Criteria**:
- Username: 3-50 characters, unique
- Email: Valid format, unique, max 100 characters
- Password: Minimum 8 characters, max 100 characters
- Password confirmation must match password
- Returns 201 Created with user data on success
- Returns 400 Bad Request for validation failures
- Returns 409 Conflict for duplicate email/username

**API Contract**:
- Method: `POST /api/auth/register`
- Request: `RegisterRequest` (username, email, password, confirmPassword)
- Response: `ApiResponse<AuthResponse>` with 201 Created

### FR-02: Email Verification

**Description**: After registration, a 6-digit OTP is emailed to the user. Submitting the OTP verifies the email and activates the account. (Replaces the former token-link flow — see `docs/feats/email-verification-otp.md`.)

**Acceptance Criteria**:
- 6-digit OTP generated, stored hashed, 10-minute expiry, single-use
- Verification email sent with the OTP (no link)
- POST `/api/auth/verify-email` with `{email, otp}` verifies and activates
- Returns 200 OK with user data on success
- Returns 400 Bad Request for invalid/expired OTPs
- Unknown email and wrong OTP return the same 400 message (anti-enumeration)
- Already verified emails return 200 OK idempotently (no OTP consumed)
- POST `/api/auth/resend-email` with `{email}` always returns a generic 200; a new code is sent only for existing unverified accounts

**API Contract**:
- Method: `POST /api/auth/verify-email`
- Body: `{ email, otp }`
- Method: `POST /api/auth/resend-email`
- Body: `{ email }`
- Response: `ApiResponse<AuthResponse>` with 200 OK (verify); generic 200 (resend)

### FR-03: Password Confirmation Validation

**Description**: Client-side and server-side validation that password and confirmPassword match.

**Acceptance Criteria**:
- `@FieldsEqual({"password", "confirmPassword"})` annotation on DTO
- Returns 400 with validation error message on mismatch

## Non-Functional Requirements

### NFR-01: Security
- Passwords hashed with BCrypt (via Spring Security PasswordEncoder)
- Verification OTPs stored as SHA-256 hashes only; raw codes never logged
- No sensitive data in logs

### NFR-02: API Consistency
- All responses wrapped in `ApiResponse<T>` envelope
- Standardized structure: `{success, message, data}`
- HTTP status codes: 200, 201, 400, 404, 409, 500

### NFR-03: Observability
- Structured logging at key points (registration, verification)
- No PII in logs beyond username/email

## Data Model

### User Entity
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, generated |
| username | String | Unique, 3-50 chars |
| email | String | Unique, valid email, max 100 chars |
| passwordHash | String | BCrypt, not null |
| emailVerified | Boolean | Default false |
| status | Enum | created, activated, deactivated, suspended |
| role | Enum | USER, ADMIN |
| createdAt | Instant | Auto-set |
| updatedAt | Instant | Auto-updated |

## Error Scenarios

| Scenario | HTTP Status | Error Code | Message |
|----------|-------------|------------|---------|
| Missing username | 400 | VALIDATION_ERROR | "Username is required" |
| Invalid email format | 400 | VALIDATION_ERROR | "Email must be valid" |
| Short password | 400 | VALIDATION_ERROR | "Password must be at least 8 characters" |
| Password mismatch | 400 | VALIDATION_ERROR | "Fields must be equal" |
| Duplicate email | 409 | DUPLICATE_RESOURCE | "Email already registered" |
| Duplicate username | 409 | DUPLICATE_RESOURCE | "Username already taken" |
| Invalid OTP | 400 | INVALID_TOKEN | "Invalid or already used verification code" |
| Expired OTP | 400 | EXPIRED_TOKEN | "Verification code has expired. Please request a new one." |

## Success Metrics

- Registration success rate > 99%
- Email delivery rate > 98%
- Verification completion rate > 80% within 24 hours
- API response time < 500ms (p95)

## Dependencies

- Spring Boot 3.5.5
- Spring Data JPA / Hibernate
- PostgreSQL (prod), H2 (test)
- Spring Security (password encoding)
- JavaMailSender (email)
- Testcontainers (integration tests)
- See also: `docs/feats/email-verification-otp.md`

## Test Coverage

- Unit tests: AuthService, AuthController, validation
- Integration tests: Full register → verify flow with PostgreSQL
- Test profiles: `test` (H2), `integration` (Testcontainers PostgreSQL)