# Business Rules: User Profile (Profile)

## Rule Index

| Rule ID | Title | Status | Last Reviewed |
|---------|-------|--------|---------------|
| BR-PROFILE-001 | Profile Fields Validation | Active | 2026-09-23 |
| BR-PROFILE-002 | Profile Ownership | Active | 2026-09-23 |
| BR-PROFILE-003 | Profile Auto-Creation | Active | 2026-09-23 |

---

# Business Rule: Profile Fields Validation

## Rule ID
`BR-PROFILE-001`

## Status
Active

## Statement
Profile fields must satisfy the following validation constraints:
- **height_cm**: Positive decimal, maximum 300.00 (precision 5, scale 2)
- **weight_kg**: Positive decimal, maximum 500.00 (precision 5, scale 2)
- **age**: Integer between 1 and 150 (inclusive)
- **gender**: Must be exactly one of: `male`, `female`, `other`
- **description**: Maximum 2000 characters
- **avatar_url**: Must be a valid URL format (RFC 3986) if provided

## Rationale
Ensures physically plausible body metrics for meal planning calculations, valid gender values for personalization, reasonable description length for storage, and valid avatar URLs for display.

## Scope & Exceptions
Applies to all profile update requests via `PUT /api/profile`. All fields are optional (partial update), but at least one field must be provided.

## Enforcement
- DTO: Jakarta Validation annotations on `ProfileRequest` (`@DecimalMin`, `@DecimalMax`, `@Digits`, `@Min`, `@Max`, `@Pattern`, `@Size`)
- Service: `UserProfileService.updateProfile()` checks `request.hasAnyField()` before processing
- API: Returns 400 Bad Request with validation error message on violation

## Last Reviewed
2026-09-23, by <name/role>

---

# Business Rule: Profile Ownership

## Rule ID
`BR-PROFILE-002`

## Status
Active

## Statement
A user can only update their own profile. The userId is extracted from the authenticated JWT token and used to identify the profile to update.

## Rationale
Prevents unauthorized access to other users' profiles. Ensures data privacy and security.

## Scope & Exceptions
Applies to `PUT /api/profile` endpoint. No exceptions — users cannot update other users' profiles.

## Enforcement
- Security: `@AuthenticationPrincipal UUID userId` extracts userId from JWT (validated by `JwtAuthenticationFilter`)
- Service: `UserProfileService.updateProfile(userId, request)` uses the authenticated userId exclusively
- No userId in request body or path — cannot be overridden by client

## Last Reviewed
2026-09-23, by <name/role>

---

# Business Rule: Profile Auto-Creation

## Rule ID
`BR-PROFILE-003`

## Status
Active

## Statement
If a user profile does not exist for the authenticated user, the first profile update creates the profile with the provided data. The profile is linked 1:1 to the user via `user_id` foreign key.

## Rationale
Avoids creating empty profiles during registration (reduces unnecessary rows). Profile is created "surgically" only when the user actually provides profile data.

## Scope & Exceptions
Applies to `PUT /api/profile` when `UserProfileRepository.findByUserId()` returns empty. No exceptions.

## Enforcement
- Service: `UserProfileService.updateProfile()` uses `orElseGet()` to create new `UserProfile` from `ProfileRequest`, sets `user` relationship, then saves
- Database: `user_profile.user_id` has UNIQUE constraint (1:1 relationship)
- Migration: `V2__move_avatar_to_user_profile.sql` moved `avatar_url` from `user` to `user_profile`

## Last Reviewed
2026-09-23, by <name/role>