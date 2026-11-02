# Feature Spec: Create Recipe API

## Status
Draft

## Author / owner
Backend team; tracked as GitHub issue #84 (Feature, Sprint 2, Epic: Content & Rec).

## Summary
Allow an authenticated user to create a recipe — a dish plus its ingredients and cooking instructions — via `POST /api/recipes`, resolving dish and ingredient names to shared rows case-insensitively so "Tofu" and "tofu" are the same ingredient.

## Problem / motivation
Posts need to link to recipes (`post_recipe` junction, data dictionary Table 7), but nothing lets a user create a recipe yet: the `recipe`, `dish`, `ingredient`, and `recipe_ingredient` tables exist (migrations `V5`/`V6`) but have no entities, no repository, and no API — the tables stay empty. Without a create endpoint there is nothing for the future post↔recipe linking feature to link to.

## Goals
- Authenticated user creates a recipe with `dishName`, ingredient entries (`name`, `amount`, `unit`), `name`, `instructions`, and `servings`.
- Ingredient and dish names are trimmed, normalized to lowercase, and resolved to existing shared rows — no duplicate row ever differs only by case.
- The saved recipe has `dish_id` set, `recipe_ingredient` rows persisted, and `user_id` taken from the JWT.
- The returned recipe `id` is referenceable by the future post↔recipe linking endpoint.

## Non-goals
- Linking posts to recipes (`post_recipe` writes) — separate follow-up issue.
- Editing, deleting, or soft-deleting recipes.
- Listing recipes (including any admin list) — separate follow-up.
- Standalone ingredient/dish master-data CRUD endpoints — rows are created implicitly on recipe creation only; no management API.
- Recipe search, meal-plan generation, or AI integration over recipes.

## Requirements

### Functional Requirements
- FR-001: `POST /api/recipes` requires a valid JWT; the owner comes from the token. Client-supplied `userId`, `createdAt`, `updatedAt`, and `deletedAt` are ignored.
- FR-002: Required fields — `name`, `instructions`, `servings`, `dishName`, and a non-empty `ingredients` list; each ingredient entry requires `name`, `amount`, and `unit`.
- FR-003: Optional fields — `description`, `prepTimeMinutes`, `cookTimeMinutes`, `difficulty` (`EASY` | `MEDIUM` | `HARD`).
- FR-004: On success, respond `201 Created` with the recipe DTO: generated `id`, resolved `dishId`, normalized (lowercase) names, linked ingredients, and timestamps.
- FR-005: Each ingredient name is trimmed and lowercased; the recipe links to the existing `ingredient` row whose `lower(name)` matches, or a new row is inserted with the lowercased name.
- FR-006: The dish name is trimmed and lowercased; the recipe links to the existing non-deleted `dish` row whose `lower(name)` matches, or a new row is inserted. `dish_id` is never null.
- FR-007: `recipe_ingredient` rows are persisted with `amount` and `unit` for every ingredient entry.
- FR-008: Missing/blank required fields, an empty or oversized `ingredients` list (more than 50 entries), `servings < 1`, `amount <= 0`, negative prep/cook time, unknown `difficulty`, or a string longer than its column allows (`name`/`dishName` ≤ 255, ingredient `name` ≤ 100, `unit` ≤ 30) → `400 Bad Request`, and no recipe is created.
- FR-009: Two ingredient entries in one request that collide case-insensitively → `400 Bad Request` (the `recipe_ingredient` primary key is `(recipe_id, ingredient_id)`, so the link would be ambiguous).
- FR-010: Missing, invalid, or expired JWT → `401 Unauthorized`.

### Non-Functional Requirements
- NFR-DATA-001: Case-insensitive uniqueness is enforced at the database level, not only in application code — migration `V22` adds a unique index on `ingredient (lower(name))` and a partial unique index on `dish (lower(name)) WHERE deleted_at IS NULL`, so concurrent requests cannot create case-variant duplicates.
- NFR-RACE-001: A `DataIntegrityViolationException` caused by a concurrent insert of the same name is handled by re-fetching and reusing the winning row, not surfacing as a 500.
- NFR-MAINT-001: Response and error shapes use the existing `ApiResponse<T>` and `GlobalExceptionHandler`; no new wrapper styles.

## Design overview
New `RecipeService.createRecipe(request, userId)`:

1. Validate the request DTO with Bean Validation (mirrors `PostController`/`CategoryController` practice).
2. Trim and lowercase `dishName` and every ingredient `name` (the API response echoes the normalized names).
3. Find-or-create the dish: lookup by lowercased name among rows with `deleted_at IS NULL`; insert if absent (a soft-deleted dish counts as absent — its name is free, matching the partial index scope and the `V21` category precedent).
4. Find-or-create each ingredient by lowercased name; `Ingredient` has no `deleted_at`, so the lookup is unconditional.
5. Duplicate ingredient names within one request (case-insensitive) fail validation before any write.
6. Build and save the `Recipe` (owner = the JWT user), then save one `RecipeIngredient` link per ingredient.
7. On `DataIntegrityViolationException` from a concurrent find-or-create, re-fetch the existing row by its lowercased name and reuse it (NFR-RACE-001).
8. Map to `RecipeResponse` with MapStruct.

New `RecipeController` exposes `POST /api/recipes` and reads `@AuthenticationPrincipal UUID userId`. No `SecurityConfig` change: `/api/recipes` already falls under `anyRequest().authenticated()`.

New migration `V22__add_ingredient_dish_name_unique.sql`:

```sql
CREATE UNIQUE INDEX idx_ingredient_name_unique ON ingredient (lower(name));
CREATE UNIQUE INDEX idx_dish_active_name_unique ON dish (lower(name)) WHERE deleted_at IS NULL;
```

New code lives in `model/recipe/` + `repository/recipe/` (entities `Recipe`, `Dish`, `Ingredient`, `RecipeIngredient`), `controller/recipe/`, `service/recipe/`, `dto/recipe/` — package-by-layer with a domain sub-package, per `AGENTS.md`.

## Success metrics
- Issue #84 acceptance criteria all pass: unit tests for normalization/validation/authorization, plus a Testcontainers integration test proving `POST /api/recipes` twice with `"Tofu"` and `"tofu"` links both recipes to one `ingredient` row.
- The created `recipe.id` is stable and referenceable by the follow-up post↔recipe linking issue.

## Acceptance criteria
**As an** authenticated user, **I want to** create a recipe with a dish and ingredients, **so that** my cooking content can later be linked to posts and reused by others.

- [ ] Given an authenticated user, when they submit valid recipe data (name, instructions, servings, dishName required; description, prep/cook time, difficulty optional), then `201 Created` with a recipe DTO owned by that user.
- [ ] Given a recipe with ingredients, when it is created, then `recipe_ingredient` rows with amount and unit are persisted.
- [ ] Given an ingredient name in any casing (e.g. "Tofu"), when the recipe is saved, then the name is normalized to lowercase and the recipe links to the existing `ingredient` row whose lowercase name matches — no duplicate row differing only by case is ever created.
- [ ] Given an ingredient name that does not exist yet (e.g. "tofu"), when the recipe is saved, then a new `ingredient` row is created with the lowercased name and linked.
- [ ] Same rules for dish: the dish name is normalized to lowercase and the recipe reuses the existing non-deleted `dish` row with that lowercase name, or creates one if none exists (`dish_id` is never null).
- [ ] Given two recipe creations using "Tofu" and "tofu", when both are saved, then both link to the same `ingredient` row and case-insensitive uniqueness holds on `ingredient.name` and `dish.name`.
- [ ] Given the client sends `userId`, timestamps, or `deletedAt`, when the request is submitted, then those fields are ignored (ownership comes from the JWT).
- [ ] Given missing or blank required fields, when the request is submitted, then `400 Bad Request` and no recipe is created.
- [ ] Given no valid JWT, when the request is submitted, then `401 Unauthorized`.
- [ ] The returned recipe `id` can be referenced by the future post↔recipe linking endpoint.

## Risks / open questions
- `lower(name)` uniqueness is case-insensitive but not accent- or locale-insensitive ("Tofú" and "tofu" remain distinct rows) — accepted scope, consistent with the `V21` category index.
- A soft-deleted dish's name is free to reuse (partial index `WHERE deleted_at IS NULL`); recipe soft-delete does not exist yet, so `dish.deletedAt` can only be set by future dish-management work — revisit if retire semantics change (same open question recorded for categories).
- Multiple recipes may share one dish row — intended (dishes are shared master data), but the future recipe-list feature must not assume dish-per-recipe.
- The post↔recipe linking follow-up depends on this endpoint landing first.

## Related
- API Reference: `docs/apis/recipes/post-recipes.md`
- Business rules: `docs/brs/recipes.md` (BR-RECP-001 … BR-RECP-003)
- Data dictionary: `docs/arch/data-dictionary.md` Tables 12–14 (Recipe, Recipe_Ingredient, Ingredient, Dish)
- Schema: `src/main/resources/db/migration/V5__create_dish_table.sql`, `V6__create_ingredient_recipe_tables.sql`, `V21__add_category_unique_active_name.sql` (index precedent)
- Driving issue: https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/84
