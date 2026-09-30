# Feature Spec: List All Recipes API (Admin)

## Status
Draft

## Author / owner
Backend team; driving issue: https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/4

## Summary
Allow admin users to retrieve a paginated list of every recipe across the platform — all authors, including soft-deleted rows — filterable by author, creation date range, and category, with each item carrying its metadata, ingredients, instructions, and a derived status.

## Problem / motivation
Admins need to review recipes for quality and safety (issue #4, Content Moderation epic, Sprint 2), but the backend has no Recipe API at all: the `recipe`, `ingredient`, and `recipe_ingredient` tables exist only as Flyway migrations (V5/V6) with no JPA mapping, no repository, no service, and no endpoint. Reviewing recipe content today requires direct database access.

## Goals
- Admins can page through every recipe regardless of author, with full metadata, ingredients, instructions, author identity, and a derived status on each row.
- Admins can narrow the list by author (`userId`), category (`categoryId`), and `createdAt` range.
- Non-admin authenticated users are denied access (403); anonymous callers get 401.

## Non-goals
- Recipe nutritional analysis (issue #4 non-goal); nutrition columns on `ingredient` are not returned.
- Bulk approval (issue #4 non-goal) — this endpoint is read-only.
- A `status` column or status query filter — `status` is derived from `deleted_at` (decided 2026-10-01, see Risks / open questions).
- Recipe create/edit/delete endpoints or any write operation.
- Full-text search over recipe name/description.
- Meal-plan (`menu`) or dish management endpoints.

## Requirements

### Functional Requirements
- [ ] FR-001: `GET /api/admin/recipes` returns a paginated list of recipes to callers with role ADMIN.
- [ ] FR-002: The result spans all authors; soft-deleted recipes are included and reported with `status = DELETED`.
- [ ] FR-003: Each item includes `id`, `name`, `description`, `instructions`, `prepTimeMinutes`, `cookTimeMinutes`, `servings`, `difficulty`, `status`, `categoryIds`, `ingredients` (`ingredientId`, `name`, `amount`, `unit`), `dishId`, `dishName`, `createdAt`, `updatedAt`, plus author `userId`, `username`, `email`.
- [ ] FR-004: `status` is derived, not stored: `ACTIVE` when `deletedAt` is null, `DELETED` otherwise. There is no `status` query parameter.
- [ ] FR-005: Optional query filters: `userId` (UUID), `categoryId` (UUID, matched through `post_recipe` → `post_category`), `createdFrom`, `createdTo` (ISO-8601 datetime on `createdAt`).
- [ ] FR-006: Pagination via `page` (0-based, default 0), `size` (default 20, max 100), optional `sort` (default `createdAt,desc`); sort property must be one of `createdAt`, `updatedAt`, `name`, otherwise 400.
- [ ] FR-007: A recipe matching a `categoryId` filter is returned exactly once even if linked to several posts carrying that category (join de-duplicated).
- [ ] FR-008: Non-admin authenticated requests receive 403; missing/invalid JWT receives 401.
- [ ] FR-009: `createdFrom` after `createdTo` is rejected with 400.

### Non-Functional Requirements
- [ ] NFR-SEC-001: Endpoint path `/api/admin/**` is restricted to `ROLE_ADMIN` by the existing security filter chain rule (`SecurityConfig`).
- [ ] NFR-SEC-002: The response DTO exposes no credentials or account secrets; author fields are limited to `userId`, `username`, `email`.
- [ ] NFR-MAINT-001: Response wrapped in `ApiResponse<T>` with pagination shape from `shared/dto/PageResponse`.
- [ ] NFR-MAINT-002: Controller stays thin; filter parsing and page assembly live in a service; predicates live in a `RecipeSpecifications` class, mirroring `PostSpecifications`.
- [ ] NFR-MAINT-003: No Flyway migration is added — the feature maps the existing schema as-is.

## Design overview
New Recipe domain mapped for the first time: entities `model/recipe/{Recipe, Dish, Ingredient, RecipeIngredient}` against the existing V5/V6 tables (no schema change), `repository/recipe/{RecipeRepository, RecipeSpecifications}` with `JpaSpecificationExecutor`, and an admin listing trio mirroring the established `AdminPost*` pattern — `controller/admin/AdminRecipeController` (`GET /api/admin/recipes`), `service/admin/AdminRecipeService`, `dto/mapper/admin/AdminRecipeMapper` (MapStruct), with `dto/request/admin/RecipeListRequest` and `dto/response/admin/AdminRecipeListResponse`. Authorization needs no new code: `SecurityConfig` already applies `hasRole("ADMIN")` to `/api/admin/**`. `categoryId` is resolved through the `post_recipe` → `post_category` junction (recipes have no direct category link), joined and de-duplicated exactly as the posts listing does; `status` is computed in the mapper from `deletedAt`, so no column and no migration are introduced.

## Success metrics
- Issue #4's acceptance criteria are covered by integration tests before PR merge.
- Endpoint p95 < 300ms on dev with < 10k recipes (manual check optional).

## Acceptance criteria
**As an** admin, **I want to** list and filter every recipe on the platform, **so that** I can review recipe content for quality and safety without database access.

- [ ] Given an admin JWT, when requesting `GET /api/admin/recipes`, then 200 with a paginated list of recipes is returned.
- [ ] Given recipes authored by several users, when requesting the endpoint, then recipes from all users are returned.
- [ ] Given any list response, when inspecting items, then each item shows metadata, ingredients with amount/unit, the full instructions text, and `status` (`ACTIVE` or `DELETED`).
- [ ] Given an admin JWT, when filtering by `userId`, `createdFrom`/`createdTo`, or `categoryId`, then only matching recipes are returned.
- [ ] Given a non-admin JWT, when requesting the endpoint, then 403 is returned.
- [ ] Given no JWT, when requesting the endpoint, then 401 is returned.

## Risks / open questions
- Deviation from issue #4's literal AC, decided by the product owner 2026-10-01: there is **no status filter** and **no stored status** — `status` is derived from `deleted_at` (`ACTIVE`/`DELETED`). The issue text says "Supports filtering by status"; this spec supersedes that line. A future moderation status (PENDING/APPROVED/REJECTED) would need a migration and is out of scope here.
- Soft-deleted recipes are shown to admins (unlike `GET /api/admin/posts`, which hides them) so that `status = DELETED` is meaningful; there is no way to filter them out yet.
- `categoryId` only matches recipes that are linked to a post (`post_recipe`) carrying that category; recipes never attached to a post match only when `categoryId` is omitted.
- Ingredients and categories are fetched per row (lazy associations), bounded by `size` (max 100) per page. Acceptable at current scale; an `@EntityGraph` is the fix if profiling shows it up.
- The 403 body is Spring Boot's default error shape (no custom `AccessDeniedHandler` exists), same as every other admin endpoint today.
