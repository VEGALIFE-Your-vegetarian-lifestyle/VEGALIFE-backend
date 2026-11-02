# Feature Spec: List Ingredients API

## Status

Implemented (pending PR review)

## Author / owner

zuyzz (issue #92), implemented by backend agent; owns the paging/filter contract.

## Summary

Adds a read-only, paginated `GET /api/ingredients` endpoint that returns the
`ingredient` master list as `PageResponse` DTOs, optionally filtered by a
case-insensitive substring on `name`, so the create-recipe form can autocomplete
against ingredients that already exist instead of free-typing duplicates.

## Problem / motivation

Recipe creation (#84) accepts ingredient names as free text and find-or-creates
each one by lowercased name. The client has no way to discover what is already
in the `ingredient` table: a user typing "Tofu" never sees that "tofu" is already
stored, so case-variant and spelling-variant duplicates get created at the point
of entry. The unique index on `lower(name)` (migration `V22`) only catches exact
case-insensitive collisions after they fail — it does not help the user find the
existing row.

## Goals

- Let an authenticated client list existing ingredients page by page.
- Let the client pre-filter that list with a substring on `name` so an
  autocomplete box narrows as the user types.
- Return ids that are exactly the rows #84's find-or-create resolves to, so a
  picked suggestion links to an existing row rather than creating a new one.

## Non-goals

- Creating / editing / deleting ingredients — this is a read-only suggestion
  list. #84's non-goal ("no standalone management API") still stands: rows
  continue to be created implicitly on recipe creation only.
- Filtering or sorting on the nutrition columns (`calories`, `protein_g`,
  `carbohydrate_g`, `fat_g`, `fiber_g`) — `name` is the only filter; nutrition
  columns are not returned in the DTO either (parity with `GET /api/admin/recipes`).
- Usage counts, popularity ranking, or "ingredients used by N recipes" ordering.
- Changing the recipe-create payload shape — #84 keeps sending ingredient names
  as strings.
- Making the endpoint public — unlike `GET /api/categories`, this one requires
  a JWT (see FR-006).

## Requirements

### Functional Requirements

- [x] FR-001: `GET /api/ingredients` returns `200 OK` with a `PageResponse` of
      ingredient DTOs (`content`, `page`, `size`, `totalElements`, `totalPages`,
      `first`, `last`) for any authenticated caller.
- [x] FR-002: When `page` and `size` are omitted, defaults `page=0`, `size=20`,
      `sort=name,asc` apply — the same defaults as `GET /api/categories`.
- [x] FR-003: `page < 0`, or `size` outside 1–100, returns `400 Bad Request`
      with no partial result.
- [x] FR-004: `name=tof` filters with a case-insensitive substring match on
      `ingredient.name`, so a row stored as "Tofu" matches.
- [x] FR-005: Each distinct ingredient appears exactly once and ordering by
      name is stable across pages — no row appears on two pages or is skipped.
- [x] FR-006: A request without a valid JWT returns `401 Unauthorized`.
- [x] FR-007: An empty `ingredient` table returns `200 OK` with empty `content`
      and `totalElements: 0` — never `404`.
- [x] FR-008: Each returned `id` is the same `ingredient` id #84's
      find-or-create resolves to.
- [x] FR-009: The endpoint performs no writes — it never creates an
      `ingredient` row.

### Non-Functional Requirements

- [x] NFR-SEC-001: The endpoint is reachable only with a valid, non-expired
      JWT; the existing `JwtAuthenticationFilter` + `anyRequest().authenticated()`
      chain enforces it with no new security configuration.
- [x] NFR-MAINT-001: The request/response/paging shape is the same as
      `GET /api/categories` (`CategoryListRequest` → `PageResponse.from(Page)`),
      so there is exactly one paging format in the codebase.
- [x] NFR-SCALE-001: Filtering and sorting run in the database (single query
      with `LIMIT/OFFSET`), not by loading the table into memory.

## Design overview

Package-by-layer, mirroring the categories feature:

- `repository/recipe/IngredientRepository` gains `JpaSpecificationExecutor<Ingredient>`
  so it can page with a specification (as `CategoryRepository` does).
- `repository/recipe/IngredientSpecifications.withNameFilter(name)` builds the
  predicate: `lower(name) LIKE %term%` — **no** `deletedAt` predicate, because
  Table 11 (`docs/arch/data-dictionary.md`) has no soft-delete column.
- `dto/request/ingredient/IngredientListRequest` — same `@Min`/`@Max` bounds and
  null-default getters as `CategoryListRequest`.
- `dto/response/ingredient/IngredientResponse` — `id`, `name`, `createdAt` only.
- `dto/mapper/ingredient/IngredientMapper` — MapStruct, same as `CategoryMapper`.
- `service/ingredient/IngredientService.listIngredients(...)` — `@Transactional(readOnly = true)`,
  `PageRequest.of(...)`, `parseSort` → `ValidationException` (400),
  `PageResponse.from(page.map(mapper::toResponse))`.
- `controller/ingredient/IngredientController` — `@GetMapping` with
  `@Valid @ModelAttribute`, wrapped in `ApiResponse.success(...)`.

No `SecurityConfig` change: `/api/ingredients` falls through to
`anyRequest().authenticated()`, which is exactly FR-006.

Ordering stability (FR-005): the default sort is `name,asc`, and migration
`V22__add_ingredient_dish_name_unique.sql` enforces
`UNIQUE INDEX idx_ingredient_name_unique ON ingredient (lower(name))`, so no two
rows can share a name modulo case. `name` is therefore a strict total order —
ties are impossible, and `OFFSET/LIMIT` paging cannot repeat or skip a row.

## Success metrics

- After shipping, new recipes stop introducing case-variant duplicate
  ingredients when the form uses the suggestion list: `SELECT lower(name), count(*)
  FROM ingredient GROUP BY 1 HAVING count(*) > 1` stays empty (it is already
  guaranteed empty by V22, so the real signal is that users report seeing
  existing ingredients instead of typing them blind).
- `GET /api/ingredients` answers 200 for a valid token and 401 without one —
  verified by the integration test in this change.

## Acceptance criteria

**As a** member filling in the create-recipe form, **I want to** pick an
ingredient that already exists from a paginated suggestion list, **so that** I
don't create a case-variant or spelling-variant duplicate row.

- [x] Given an authenticated user, when they call
      `GET /api/ingredients?page=&size=&name=`, then the API returns `200 OK`
      with a `PageResponse` of ingredient DTOs (`content`, `page`, `size`,
      `totalElements`, `totalPages`, `first`, `last`).
- [x] Given `page` and `size` are omitted, when the request is made, then
      defaults `page=0`, `size=20`, `sort=name,asc` apply.
- [x] Given `page < 0` or `size` outside 1–100, when the request is made, then
      `400 Bad Request` and no partial result is returned.
- [x] Given `name=tof`, when the request is made, then the result is a
      case-insensitive substring match on `ingredient.name`, so "Tofu" matches.
- [x] Given the table holds rows differing only by case, when results are
      returned, then each distinct ingredient appears exactly once and ordering
      by name is stable across pages.
- [x] Given an empty `ingredient` table, when the request is made, then
      `200 OK` with empty `content` and `totalElements: 0` — not `404`.
- [x] Given no valid JWT, when the request is made, then `401 Unauthorized`.
- [x] Each returned `id` is the same `ingredient` id #84's find-or-create
      resolves to.
- [x] The endpoint performs no writes.

## Risks / open questions

- The `sort` parameter is accepted for parity with `GET /api/categories`, but
  only `name` is a meaningful sort key — sorting by `calories` etc. would work
  at the SQL level while the issue lists nutrition ordering as a non-goal. Left
  permissive (same as categories) rather than adding an allowlist; tightening it
  is a one-line change if a reviewer objects.
- Package placement (`controller/ingredient/` vs. `controller/recipe/`) is a
  judgment call: `CategoryController` sits in `controller/post/` because
  `Category` lives in `model.post`, while `Ingredient` lives in `model.recipe`.
  This spec puts both controller and service under `.../ingredient/`, following
  the `AGENTS.md` "domain sub-packages as features grow" convention.
