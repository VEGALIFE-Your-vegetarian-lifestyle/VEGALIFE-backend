# Business Rules: Recipes

## Rule Index

| Rule ID | Title | Status | Last Reviewed |
|---------|-------|--------|---------------|
| BR-RECP-001 | Recipe Ownership Comes from Authentication | Active | 2026-10-01 |
| BR-RECP-002 | Ingredient and Dish Names Are Unique Case-Insensitively | Active | 2026-10-01 |
| BR-RECP-003 | Recipe Name, Instructions, Servings, and Dish Are Required | Active | 2026-10-01 |

---

# Business Rule: Recipe Ownership Comes from Authentication

## Rule ID

`BR-RECP-001`

## Status

Active

## Statement

When a user creates a recipe, the owner is taken from the authenticated JWT. A client-supplied user ID must not determine recipe ownership.

## Rationale

Deriving ownership from the authenticated identity prevents users from creating recipes under another account. Recipes are later linked to posts, so a forged owner would leak into downstream content attribution.

## Scope & Exceptions

Applies to `POST /api/recipes` and any future recipe endpoints that read or write a user's recipes. Admin or system-generated recipes are outside this rule and require their own authorization contract.

## Enforcement

- Controller: `RecipeController` receives `@AuthenticationPrincipal UUID userId`.
- Service: `RecipeService.createRecipe()` assigns the loaded user to the entity; no request field maps to `userId`.
- API reference: `docs/apis/recipes/post-recipes.md`.

## Last Reviewed

2026-10-01, by Vegalife backend team

---

# Business Rule: Ingredient and Dish Names Are Unique Case-Insensitively

## Rule ID

`BR-RECP-002`

## Status

Active

## Statement

`ingredient.name` and `dish.name` are unique case-insensitively: a submitted name is trimmed and lowercased before it is resolved against existing rows, and a row is created only when no row with that lowercase name exists. "Tofu" and "tofu" are the same ingredient; no two rows may ever differ only by case.

## Rationale

Ingredients and dishes are shared master data referenced by recipes. Case-variant duplicates ("Tofu" vs "tofu") would fragment search, meal-plan generation, and analytics, and would let the same logical ingredient accumulate inconsistent nutrition data.

## Scope & Exceptions

- Applies to every path that inserts an `ingredient` or `dish` row — today only recipe creation (`POST /api/recipes`), but the rule is not endpoint-specific.
- Uniqueness is scoped to non-deleted dishes: a soft-deleted `dish` row's name is free to reuse (partial unique index `WHERE deleted_at IS NULL`), matching the category precedent in `V21`. `ingredient` has no soft-delete column, so its uniqueness is unconditional.
- Scope is case-insensitivity only: accented variants ("Tofú" vs "tofu") remain distinct rows.

## Enforcement

- Database (authoritative): migration `V22__add_ingredient_dish_name_unique.sql` creates `UNIQUE INDEX idx_ingredient_name_unique ON ingredient (lower(name))` and `UNIQUE INDEX idx_dish_active_name_unique ON dish (lower(name)) WHERE deleted_at IS NULL` — concurrent requests cannot insert case-variant duplicates.
- Service (fast path): `RecipeService` trims and lowercases names, looks up existing rows first, and creates only when absent; on `DataIntegrityViolationException` from a race it re-fetches the winning row and reuses it.
- API reference: `docs/apis/recipes/post-recipes.md`.

## Last Reviewed

2026-10-01, by Vegalife backend team

---

# Business Rule: Recipe Name, Instructions, Servings, and Dish Are Required

## Rule ID

`BR-RECP-003`

## Status

Active

## Statement

A recipe must have a non-blank `name`, non-blank `instructions`, `servings` of at least 1, a non-blank `dishName` (the recipe always belongs to a dish), and at least one ingredient entry with a name, a positive amount, and a unit. A request missing any of these is rejected with `400 Bad Request` and no recipe is persisted.

## Rationale

Every column backing these fields in the schema is `NOT NULL` (`recipe.name`, `recipe.instructions`, `recipe.servings`, `recipe.dish_id`), and a recipe without ingredients has no culinary value and cannot be linked meaningfully to nutrition or meal-plan features. Rejecting at the API boundary keeps partial rows from ever reaching the database.

## Scope & Exceptions

Applies to `POST /api/recipes`. Optional fields (`description`, `prepTimeMinutes`, `cookTimeMinutes`, `difficulty`) may be omitted; when supplied, `difficulty` must be one of `EASY`, `MEDIUM`, `HARD` and times must be non-negative.

## Enforcement

- DTO: Bean Validation constraints on `RecipeCreateRequest` and its nested ingredient request.
- Controller: invalid payloads are handled by the existing `GlobalExceptionHandler` → `400 Validation failed`.
- API reference: `docs/apis/recipes/post-recipes.md`.

## Last Reviewed

2026-10-01, by Vegalife backend team
