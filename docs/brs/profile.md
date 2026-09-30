# Business Rules: User Profile (Profile)

## Rule Index

| Rule ID | Title | Status | Last Reviewed |
|---------|-------|--------|---------------|
| BR-PROFILE-001 | Profile Fields Validation | Active | 2026-09-23 |
| BR-PROFILE-002 | Profile Ownership | Active | 2026-09-23 |
| BR-PROFILE-003 | Profile Auto-Creation | Active | 2026-10-01 |
| BR-PROFILE-004 | Public Profile Read | Active | 2026-10-01 |

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
If a user profile does not exist, the first interaction that needs it creates
the profile: the first profile update (`PUT /api/profile`) or the first read
(`GET /api/profile`, `GET /api/profile/{userId}`). The created row starts
with all metrics/description/avatar fields null and is linked 1:1 to the user
via `user_id` foreign key.

## Rationale
Avoids creating empty profiles during registration (reduces unnecessary rows) while guaranteeing that a read never fails merely because the user has
never filled in a profile. Profile rows are created "surgically" only when a
real interaction needs one.

## Scope & Exceptions
Applies when `UserProfileRepository.findByUserId()` returns empty on
`PUT /api/profile` or on either GET endpoint. No exceptions.

## Enforcement
- Service: `UserProfileService.updateProfile()` uses `orElseGet()` to create new `UserProfile` from `ProfileRequest`, sets `user` relationship, then saves; `UserProfileService.getProfile()` does the same find-or-create before mapping
- Database: `user_profile.user_id` has UNIQUE constraint (1:1 relationship); metrics columns are nullable (migration `V22__make_user_profile_fields_nullable.sql`)
- Migration: `V12__move_avatar_to_user_profile.sql` moved `avatar_url` from `user` to `user_profile`

## Last Reviewed
2026-10-01, by backend team (issue #88)

---

# Business Rule: Public Profile Read

## Rule ID
`BR-PROFILE-004`

## Status
Active

## Statement
Any member's profile is readable without authentication at
`GET /api/profile/{userId}`. The public response contains exactly the
`ProfileResponse` fields — `userId`, `username`, `email`, `heightCm`,
`weightKg`, `age`, `gender`, `description`, `avatarUrl`, `updatedAt` — and
nothing else. There is no per-user visibility toggle; read access is
unconditional for every existing user.

## Rationale
Author cards, member directories, and other members' post pages need profile
data (avatar, bio, metrics) before the viewer has any relationship with the
author. Gating reads would require a privacy model the product has not
asked for; the confirmed trade-off (issue #88) is that `email` is part of the
public shape and is accepted for now.

## Scope & Exceptions
Applies to `GET /api/profile/{userId}` only. `GET /api/profile` (own) and
`PUT /api/profile` remain authenticated. A `userId` that does not exist
returns 404, not an empty profile — the user must exist; only the
`user_profile` row may be auto-created (BR-PROFILE-003).

## Enforcement
- Security: `SecurityConfig` `permitAll` on
  `GET /api/profile/*`; both GET handlers return `ProfileResponse` only —
  never the `User` entity, so `passwordHash`, `role`, `status`,
  `emailVerified`, `lastLoginAt`, `createdAt`, `deletedAt` cannot leak
- Service: `UserProfileService.getProfile(UUID)` 404s via
  `ResourceNotFoundException("User not found")` when the user row is absent
- OpenAPI: documented as a public endpoint in
  `docs/apis/profile/get-profile-userid.md`

## Last Reviewed
2026-10-01, by backend team (issue #88)