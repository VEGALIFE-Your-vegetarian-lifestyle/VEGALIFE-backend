# Feature Spec: List Dishes API

## Status
Implemented (pending PR review)

## Author / owner
Backend team; driven by GitHub issue #93 (assigned zuyzz), under Epic "Content & Rec", Sprint 2.

## Summary
Let an authenticated caller retrieve canonical dishes, paginated and optionally filtered by a case-insensitive name substring, so the create-recipe form can autocomplete an existing `dishName` before submit and keep `recipe.dish_id` pointing at one canonical dish row per real-world dish.

## Problem / motivation
Recipe creation (#84, `POST /api/recipes`) accepts a `dishName` string and find-or-creates the canonical `dish` row by lowercased name, but the client has no way to see which dishes already exist. Users therefore free-type near-duplicates ("Vegetarian Pho" next to an existing "vegetarian pho") whenever the form can't suggest the existing row. A paginated suggestion list gives the form the autocomplete source it needs, which is what keeps `dish_id` referencing one canonical row rather than a pile of case/spacing variants (data model: `docs/arch/data-dictionary.md` Table 14).

## Goals
- An authenticated client can list active dishes, paginated, with the same paging contract as `GET /api/categories`.
- A `name` query param filters dishes by case-insensitive substring, powering typeahead.
- Soft-deleted dishes are excluded from results and from `totalElements`.
- Each returned `id` is exactly the id #84's find-or-create resolves to, so picking a suggestion makes `dish_id` reference an existing canonical row.

## Non-goals
- Creating / editing / deleting dishes — read-only suggestion list; #84's "no standalone management API" still stands (rows continue to be created implicitly on recipe creation only).
- Changing the recipe-create payload — #84 keeps accepting a `dishName` string; this endpoint is a read helper for the form, not a switch to `dishId`.
- Filtering by `cuisine_type`, or `image_url`/`description`-driven search ranking.
- Recipe counts per dish or "most-used dish" ordering.

## Requirements

### Functional Requirements
- [x] FR-001: `GET /api/dishes` requires a valid JWT; requests without one get 401.
- [x] FR-002: Supports `page` (default 0), `size` (default 20, max 100), and `sort` (default `name,asc`), matching `CategoryListRequest`'s conventions.
- [x] FR-003: Supports an optional `name` query param: a case-insensitive substring filter on `dish.name` (e.g. `name=pho` matches "Pho Chay").
- [x] FR-004: Response is 200 with a `PageResponse<DishResponse>` (`content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`) of active (`deletedAt IS NULL`) dishes matching the filter.
- [x] FR-005: Each item exposes `id`, `name`, `description`, `imageUrl`, `cuisineType`, `createdAt` — enough for the form to render a suggestion and submit its `name`.
- [x] FR-006: No dishes match (or the table is empty) → 200 with an empty `content` array (not 404).
- [x] FR-007: Invalid `page`/`size` (page < 0, size outside 1–100), or a malformed `sort` → 400, with no partial result.
- [x] FR-008: The endpoint performs no writes — it never creates or mutates a `dish` row.
- [x] FR-009: Each distinct dish appears exactly once per result set; ordering by name is stable across pages (guaranteed by the active-row unique index `idx_dish_active_name_unique ON dish (lower(name)) WHERE deleted_at IS NULL`, V22).

### Non-Functional Requirements
- [x] NFR-PERF-001: Single indexed query per page (`idx_dish_active` covers `deleted_at IS NULL`; the `name` filter is a `LIKE` scan, acceptable at the dish table's expected size); no N+1 risk since the response carries no related entities.
- [x] NFR-SEC-001: Read-only for any authenticated role; no admin elevation and no new public (unauthenticated) surface.
- [x] NFR-MAINT-001: Reuses the existing `ApiResponse<T>` / `PageResponse<T>` wrappers and the `CategoryListRequest` paging shape — no second paging format in the codebase.

## Design overview
New `DishController` at `/api/dishes` (recipe domain, alongside `RecipeController`), backed by `DishService.listDishes(DishListRequest)`. A new `DishListRequest` mirrors `CategoryListRequest` (`page`/`size`/`sort` defaults, `name` filter, `@Min`/`@Max` bound annotations → 400 via `MethodArgumentNotValidException`). A new `DishSpecifications.activeWithNameFilter(name)` mirrors `CategorySpecifications` (`deletedAt IS NULL` + optional `lower(name) LIKE`). `DishRepository` additionally extends `JpaSpecificationExecutor<Dish>` (kept `JpaRepository` for the existing `findByNameIgnoreCaseAndDeletedAtIsNull` used by #84). The service parses `sort` the way `CategoryService.parseSort` does (throwing `ValidationException` → 400 on a malformed value), runs `findAll(spec, pageable)` read-only, and maps through a new `DishMapper` into `PageResponse.from(page.map(...))`. No `SecurityConfig` change: `/api/dishes` already falls under `.anyRequest().authenticated()`, giving the required 401 for missing/invalid JWT.

## Success metrics
- The create-recipe form can populate its dish typeahead from this endpoint, and new recipes created through the form resolve to pre-existing dish ids instead of inserting case-variant rows (observable as no growth in `dish` rows per distinct lowercased name).

## Acceptance criteria
**As a** member writing a recipe, **I want to** pick my dish from a suggestion list, **so that** the recipe links to the canonical dish instead of creating a near-duplicate.

- [x] Given an authenticated user, when they call `GET /api/dishes?page=&size=&name=`, then 200 with a `PageResponse` of dish DTOs.
- [x] Given `page` and `size` are omitted, when the request is made, then defaults `page=0`, `size=20`, `sort=name,asc` apply — same as `GET /api/categories`.
- [x] Given `page < 0` or `size` outside 1–100, when the request is made, then 400 and no partial result.
- [x] Given `name=pho`, when the request is made, then results are a case-insensitive substring match on `dish.name` (a dish stored as "Pho Chay" matches).
- [x] Given a dish with `deleted_at` set, when the request is made, then it is excluded from results and from `totalElements`.
- [x] Given an empty `dish` table, when the request is made, then 200 with empty `content` and `totalElements: 0` — not 404.
- [x] Given no valid JWT, when the request is made, then 401.
- [x] Each returned `id` is the same `dish` id #84's find-or-create resolves to, so a picked suggestion makes `dish_id` reference an existing canonical dish.
- [x] The endpoint performs no writes.

## Risks / open questions
- None open: auth requirement (401) and read-only scope are explicit in #93; uniqueness of case-variant names is already enforced by `idx_dish_active_name_unique`, so no dedup logic is needed in the query.

## Related
- API Reference: `docs/apis/recipes/get-dishes.md`
- Driving issue: https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/93
- Companion: `docs/feats/create-recipe-api.md` (find-or-create dish), `docs/brs/recipes.md` (BR-RECP-002)
- Pattern source: `docs/feats/list-categories-api.md`, `docs/apis/post/get-categories.md`
- Data model: `docs/arch/data-dictionary.md` Table 14 (Dish)
