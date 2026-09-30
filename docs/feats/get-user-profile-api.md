# Feature Spec: Get User Profile API

## Status

Approved

## Author / owner

Vegalife backend team; driving issue #88.

## Summary

Two read endpoints for user profiles — `GET /api/profile` returns the
authenticated user's own profile, `GET /api/profile/{userId}` returns any
member's public profile — both using the same `ProfileResponse` shape that
`PUT /api/profile` now also returns, with lazy profile creation so a user who
has never filled in a profile still gets a 200 response.

## Problem / motivation

The platform can write a profile (`PUT /api/profile`, issue #23) but cannot
read one back. The member-facing profile page, other members' post author
cards, and the frontend's own settings page all need profile data (metrics,
description, avatar) plus basic identity (`username`, `email`) in one call.

Today the only read path, `UserProfileService.getProfile`, is dead code that
throws `ResourceNotFoundException` when no `user_profile` row exists — and
the schema itself forbids creating that row without body metrics
(`height_cm`/`weight_kg`/`age`/`gender` are `NOT NULL` in
`V1__create_user_tables.sql`). So a user who never edited their profile has
no readable profile at all, and `PUT` with only `avatarUrl` on such a user
fails with a 500 (`DataIntegrityViolationException`).

## Goals

- Any member's profile is readable at `GET /api/profile/{userId}` without
  authentication.
- The authenticated user reads their own profile at `GET /api/profile`.
- Reading a profile never 404s merely because the user has not filled in
  metrics yet — an empty profile row is created on first read.
- Profile responses carry identity (`userId`, `username`, `email`) so the
  frontend renders author cards from a single response.
- `PUT /api/profile` stops failing when it is the first write for a user.

## Non-goals

- Listing/searching profiles in bulk (no paginated profile index).
- Profile visibility settings (public read is unconditional; no per-user
  privacy toggle).
- Changing what body metrics mean, their validation ranges, or BR-PROFILE-001.
- Editing another user's profile (BR-PROFILE-002 unchanged: writes stay
  self-only via JWT principal).
- Removing `passwordHash` or any auth field from the response — the response
  only ever exposes `ProfileResponse` fields, never the `User` entity.
- Fixing pre-existing casing mismatches in unrelated API docs.

## Requirements

### Functional Requirements

- [ ] FR-001: `GET /api/profile` returns `200` with
      `ApiResponse<ProfileResponse>` for the authenticated user (JWT
      required; `401` without a token).
- [ ] FR-002: `GET /api/profile/{userId}` returns `200` with the same
      `ProfileResponse` shape for any existing user, without requiring
      authentication.
- [ ] FR-003: Both endpoints return `404` with
      `"User not found"` when the `userId` does not exist (or is soft-deleted
      state where the user row itself is absent).
- [ ] FR-004: `ProfileResponse` contains exactly: `userId`, `username`,
      `email`, `heightCm`, `weightKg`, `age`, `gender`, `description`,
      `avatarUrl`, `updatedAt`. The profile row `id` is no longer exposed.
- [ ] FR-005: `PUT /api/profile` returns the same `ProfileResponse` shape
      (adds `username`/`email`, drops `id`) so callers need one response
      model.
- [ ] FR-006: When no `user_profile` row exists for an existing user, a GET
      creates the row with nullable metrics (all `null`) and returns `200`
      with `heightCm`/`weightKg`/`age`/`gender`/`description`/`avatarUrl` as
      `null`.
- [ ] FR-007: The `user_profile` columns `height_cm`, `weight_kg`, `age`,
      `gender` become nullable via a new Flyway migration; existing rows are
      untouched.
- [ ] FR-008: `GET /api/profile/{userId}` is permitted without
      authentication by `SecurityConfig`; `GET /api/profile` and
      `PUT /api/profile` remain authenticated.

### Non-Functional Requirements

- [ ] NFR-SEC-001: `passwordHash`, `role`, `status`, `emailVerified`,
      `lastLoginAt`, `createdAt`, `deletedAt` are never serialized in any
      profile response.
- [ ] NFR-SEC-002: The public endpoint exposes only the `ProfileResponse`
      fields listed in FR-004; no other `User` or `UserProfile` columns leak.
- [ ] NFR-MAINT-001: Response and error shapes reuse the existing
      `ApiResponse<T>` envelope and `GlobalExceptionHandler` mappings; no new
      wrapper styles or exception types.
- [ ] NFR-MAINT-002: One shared service method (find-or-create) backs both
      GET handlers and PUT's profile load, so empty-profile handling has a
      single implementation instead of three.
- [ ] NFR-MAINT-003: The new migration is additive/relaxing only
      (`DROP NOT NULL`); no data rewrite, trivially reversible in practice.
- [ ] NFR-SCALE-001: Reads hit `user_profile.user_id` (UNIQUE index from
      `V1`) and `user.id` (PK); no new queries beyond the existing 1:1
      lookup, no N+1.

## Design overview

**API surface** (both on the existing `ProfileController`,
`/api/profile`):

| Endpoint | Auth | Behavior |
|----------|------|----------|
| `GET /api/profile` | JWT | Own profile, principal-derived `userId` (`@AuthenticationPrincipal`, same as PUT) |
| `GET /api/profile/{userId}` | None (`permitAll`) | Public profile by path variable |

**Response DTO**: `ProfileResponse` loses its `id` field and gains
`username`/`email`, mapped from the parent `User` in `ProfileMapper`
(`toResponse` already maps `userId` ← `user.id`, so the same source supplies
the two new fields). PUT keeps returning this DTO — its existing docs and
tests are updated to the new shape.

**Service**: a single `UserProfileService.getProfile(UUID userId)` becomes
the find-or-create read path: `userRepository.findById` → 404
`ResourceNotFoundException("User not found")` if absent (matches
`GlobalExceptionHandler` line 68 → 404), then
`profileRepository.findByUserId().orElseGet(...)` persists an empty
`UserProfile` bound to the user and returns it mapped. Both GET handlers
call it; PUT's existing `orElseGet` creation path remains (same rule,
BR-PROFILE-003) and now succeeds because metrics are nullable.

**Schema**: `V22__make_user_profile_fields_nullable.sql` issues four
`ALTER TABLE user_profile ALTER COLUMN ... DROP NOT NULL` statements
(`height_cm`, `weight_kg`, `age`, `gender`); the `UserProfile` entity's
matching `nullable=false` annotations are flipped to `true`. `gender`'s
`CHECK (male/female/other)` stays — `NULL` passes PostgreSQL CHECK.

**Security**: `SecurityConfig` adds
`.requestMatchers(HttpMethod.GET, "/api/profile/*").permitAll()` next to the
existing public rules (`GET /api/users/*/posts`, `GET /api/categories`).
Class-level `@SecurityRequirement` on the controller remains as-is (repo
convention: it documents the security scheme globally; `GET /api/users/{userId}/posts`
does the same today while being `permitAll`).

Known product trade-off (confirmed): the public response includes `email`,
so any member's email is readable without auth. This matches issue #88's
stated scope — see `docs/apis/profile/get-profile-userid.md` and the risks
section.

## Success metrics

- Both GET endpoints return documented responses in
  `ProfileControllerIntegrationTest` after merge (part of the PR's
  verification gate).
- `PUT /api/profile` with only `avatarUrl` on a profile-less user returns
  200 instead of 500, covered by an integration test.
- No new checkstyle/spotless violations (`./mvnw checkstyle:check`,
  `./mvnw spotless:check` green in the PR checks).

## Acceptance criteria

**As a** frontend client, **I want to** fetch any member's profile with one
GET call, **so that** I can render profile pages and author cards without
client-side assembly from multiple endpoints.

- [ ] Given an authenticated user with a filled profile, when
      `GET /api/profile` is called, then `200` is returned with
      `userId`, `username`, `email`, and all profile fields.
- [ ] Given an authenticated user with no `user_profile` row, when
      `GET /api/profile` is called, then `200` is returned with null
      metric/description/avatar fields and an empty row now exists in
      `user_profile`.
- [ ] Given no authentication, when `GET /api/profile/{userId}` is called
      for an existing user, then `200` is returned with the same response
      shape.
- [ ] Given no authentication, when `GET /api/profile/{userId}` is called
      for a non-existent `userId`, then `404` with
      `"User not found"` is returned.
- [ ] Given no `Authorization` header, when `GET /api/profile` is called,
      then `401` is returned.
- [ ] Given a user with no profile row, when `PUT /api/profile` supplies
      only `avatarUrl`, then `200` is returned (regression guard for the
      NOT NULL fix).
- [ ] When `PUT /api/profile` returns, then its `data` has no `id` and does
      have `username`/`email`, matching both GET responses.

## Risks / open questions

- **Email exposure on the public endpoint** (accepted, confirmed with
  product): `GET /api/profile/{userId}` is unauthenticated and returns
  `email`. Issue #88 scope explicitly says "get profile by any user is
  public". Revisit if spam/harvesting becomes a concern — would need a
  product decision to strip `email` from the public variant or gate the
  endpoint.
- **Breaking response change on PUT**: removing `id` from `ProfileResponse`
  changes an already-merged contract (issue #23). Only this repo's own
  tests/docs consume it (verified by search), but the frontend team must be
  told in the PR description.
- **Empty rows created by GET**: FR-006 persists a row on first read, so
  idle users now have a `user_profile` row. Accepted: row is small, UNIQUE
  `user_id` makes creation idempotent under concurrent reads (one insert
  wins, loser re-reads — see plan phase for conflict handling).
