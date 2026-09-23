# Feature Specification: Edit User Profile API

## Overview
Allow authenticated users to update their profile fields (physical metrics and description) via a PUT endpoint. The profile data is stored in the `user_profile` table (1:1 with `user` table). If a profile doesn't exist for the user, it will be created.

## Actors
- **Authenticated User** - Logged-in user updating their own profile
- **System** - Backend services handling profile update, validation, and persistence

## Functional Requirements

### FR-01: Update User Profile
**Description**: Authenticated users can update their profile fields including height, weight, age, gender, description, and avatar URL.

**Acceptance Criteria**:
- All fields are optional (partial update allowed) — at least one field must be provided
- Returns 200 OK with updated profile data on success
- Returns 400 Bad Request for validation failures
- Returns 401 Unauthorized for missing/invalid JWT
- If user profile doesn't exist, it is created with the provided data
- Only the authenticated user can update their own profile

**API Contract**:
- Method: `PUT /api/profile`
- Request: `UpdateProfileRequest` (height_cm, weight_kg, age, gender, description, avatar_url — all optional)
- Response: `ApiResponse<ProfileResponse>` with 200 OK

### FR-02: Profile Validation
**Description**: Server-side validation for all profile fields.

**Acceptance Criteria**:
- height_cm: Positive decimal, max 300
- weight_kg: Positive decimal, max 500
- age: Integer between 1 and 150
- gender: One of `male`, `female`, `other`
- description: Max 2000 characters
- avatar_url: Valid URL format (if provided)
- Returns 400 with validation error details on any violation

## Non-Functional Requirements

### NFR-01: Security
- Endpoint requires valid JWT authentication
- User can only update their own profile (enforced by extracting userId from JWT)
- No sensitive data in logs beyond userId

### NFR-02: API Consistency
- Response wrapped in `ApiResponse<T>` envelope
- Standardized structure: `{success, message, data}`
- HTTP status codes: 200, 400, 401

### NFR-03: Observability
- Structured logging at key points (profile update)
- Log userId and updated fields

## Data Model

### UserProfile Entity (after migration)
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, generated |
| user_id | UUID | FK to User, Unique, Not Null |
| height_cm | BigDecimal | Not Null, > 0, <= 300 |
| weight_kg | BigDecimal | Not Null, > 0, <= 500 |
| age | Integer | Not Null, 1-150 |
| gender | String | Not Null, ENUM (male, female, other) |
| description | String | Nullable, max 2000 chars |
| avatar_url | String | Nullable, valid URL |
| updated_at | Instant | Auto-updated |

### Migration: Move avatar_url from user to user_profile
- V2 migration: Add `avatar_url` to `user_profile`, copy from `user`, drop from `user`

## Error Scenarios

| Scenario | HTTP Status | Error Code | Message |
|----------|-------------|------------|---------|
| No fields provided | 400 | VALIDATION_ERROR | "At least one profile field must be provided" |
| Invalid height_cm | 400 | VALIDATION_ERROR | "Height must be between 0 and 300 cm" |
| Invalid weight_kg | 400 | VALIDATION_ERROR | "Weight must be between 0 and 500 kg" |
| Invalid age | 400 | VALIDATION_ERROR | "Age must be between 1 and 150" |
| Invalid gender | 400 | VALIDATION_ERROR | "Gender must be male, female, or other" |
| Description too long | 400 | VALIDATION_ERROR | "Description must not exceed 2000 characters" |
| Invalid avatar_url | 400 | VALIDATION_ERROR | "Avatar URL must be a valid URL" |
| Missing/invalid JWT | 401 | UNAUTHORIZED | "Invalid or missing access token" |

## Business Rules
- BR-PROFILE-001: Profile Fields Validation (height, weight, age, gender)
- BR-PROFILE-002: Profile Ownership (users can only update their own profile)
- BR-PROFILE-003: Profile Auto-Creation (create profile if not exists on first update)

## Success Metrics
- Profile update success rate > 99%
- API response time < 300ms (p95)

## Dependencies
- Spring Boot 3.5.5
- Spring Data JPA / Hibernate
- PostgreSQL (prod), H2 (test)
- Spring Security (JWT authentication)
- MapStruct (DTO mapping)
- Jakarta Validation

## Test Coverage
- Unit tests: ProfileService, validation logic
- Integration tests: Full PUT /api/profile flow with PostgreSQL
- Test profiles: `test` (H2), `integration` (Testcontainers PostgreSQL)