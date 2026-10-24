# Feature Spec: Dish Suggestions and Recipe Recommendations

Linked issue: #163 — Add suggest_dishes tool with allergy-aware filtering and
recipe recommendations.

## Problem

The AI chat could plan a week of meals, but it had no way to discover dishes:
`create_menu` requires real `dish` ids, and the system prompt could only tell
the model to reuse ids "the member has given or that appear in menus you have
already read". A member asking for suggestions got an invented id or a refusal.
There was also no way to answer "what can I actually cook with my allergy?" —
recipe nutrition lived on the *ingredient*, which has no recorded basis (per
100 g? per serving?) and so could never be turned into a dish figure.

## Goals

- Let a member (through chat or REST) browse dishes that can be planned, each
  with a few concrete recipes and their nutrition.
- Never recommend a dish whose recipes all conflict with the member's stored
  allergies.
- Expose real dish ids so `create_menu` no longer needs invented ones.
- Attach recommended recipes to meal-plan reads, so a plan can be read aloud
  with something concrete behind each meal.
- Move nutrition to where it can be meaningful: author-entered totals on the
  recipe.

## Non-goals

- **No allergen modelling beyond name equality.** No `ingredient_allergen`
  table, no allergen-term vocabulary, no synonyms, no groups, no substring
  matching. See "Known limitation" below.
- **No allergen flag on menus**, and no handling of allergy-profile changes for
  already-saved menus.
- **No allergy rejection on `create_menu` / `update_menu`.** The menu write path
  validates structure only, unchanged.
- No nutrition derivation from ingredients, no macro targets, no numeric mapping
  of `healthGoal`. Nutrition is returned as data and judged by the AI.
- No changes to `MealResponse` (the write path). Only `MealDetailResponse`
  gains recommendations.

## Requirements

### Functional Requirements

- FR-001: `GET /api/dishes/suggestions` returns dishes that have at least one
  approved recipe passing the caller's allergy check and filters, paginated,
  ordered by dish name.
- FR-002: query parameters `name`, `cuisineType`, `difficulty`, `maxPrepTime`,
  `maxCookTime`, `maxTotalTime`, `minCalories`, `maxCalories`, `minProtein`,
  `page`, `size`, `recipesPerDish`.
- FR-003: an AI tool `suggest_dishes` exposes the same parameters, so the model
  can browse from free-text conversation.
- FR-004: `recommendedRecipes` (up to `recipesPerDish`, default 3) is attached
  to each meal in `GET /api/menus/details` and to each suggestion.
- FR-005: a recipe is recommended only when `moderation_status = APPROVED` and
  `deleted_at IS NULL`.
- FR-006: recipes are ranked `score DESC NULLS LAST, createdAt DESC, id ASC`.
- FR-007: allergies are read from the caller's profile on every call; no
  parameter supplies them.
- FR-008: `recipe` carries author-entered total nutrition (`calories`,
  `protein_g`, `carbohydrate_g`, `fat_g`, `fiber_g`), all nullable.
- FR-009: recipe nutrition is accepted on `POST /api/recipes`.

### Non-functional Requirements

- NFR-001: recipe lookups are batched by distinct dish id — at most one recipe
  query and one ingredient query per meal plan, never one per meal.
- NFR-002: `totalElements` counts only dishes the caller receives.
- NFR-003: unknown nutrition never passes a numeric filter.
- NFR-004: the suggestion surface is read-only.

## Allergy Matching

This is the part most likely to be misread, so it is stated explicitly.

- The stored allergy strings are free text from
  `user_profile.dietary_preferences.allergies`.
- A recipe is allergenic when **any** of its ingredient names equals **any**
  stored allergy, after trimming and case folding.
- It is exact: `peanut` does not match `peanut butter`, `peanuts`, or
  `groundnut`.

### Known limitation

Because the backend models no allergen vocabulary, matching is only as good as
what members and authors typed. A member who writes `nuts` will still be shown
a dish containing `almond`. This is a deliberate trade-off: the alternative was
a curated vocabulary nobody maintains. The AI prompt tells the model not to
claim a dish is allergen-free beyond what the tool returned.

### Why "block on all"

A dish is dropped only when **every** approved recipe conflicts. A dish with one
safe variant stays, and only the safe variants are recommended. Dropping a dish
on a single conflicting recipe would hide an entire dish because of one
author's version, which is worse for a member looking for something to eat.

## Decisions

| Decision | Rationale |
|---|---|
| Nutrition is a recipe total, not per serving | The author knows the amounts; `recipe.servings` lets per-serving be derived without storing a second number |
| Nutrition columns are nullable, never defaulted to 0 | `0` means "no calories"; `null` means "not provided". ADR-016 uses the same convention for `score` |
| `ingredient` nutrition columns dropped | No recorded per-100 g basis made them unusable for a dish figure; irreversible, explicitly approved |
| Menu reads allergy-filter `recommendedRecipes` but never drop a meal | A saved menu is the member's plan. Removing a meal they saved would be destructive; an empty list is honest |
| No allergen flag on menus | There is no per-meal allergy state, and recomputing on read is enough |
| `recommendedRecipes` only on `MealDetailResponse` | The write path returns what was written, not derived suggestions |
| Fixed ordering, no `sort` parameter | A caller-supplied sort would let a request name a nonexistent property and surface a `PropertyReferenceException` |
| Recipe summary shape excludes owner/moderation/ingredients | A recommendation is read-only catalogue content; the full recipe stays reachable through the recipe endpoints |

## Schema Changes

- `V42__recipe_nutrition_totals.sql` — adds five nullable `DECIMAL(10,2)`
  columns to `recipe`, each `CHECK (>= 0)`.
- `V43__drop_ingredient_nutrition.sql` — drops the same five columns from
  `ingredient`. Destructive and irreversible.

## Verification

- `RecipeRecommendationServiceTest` — allergy exactness, case/whitespace
  handling, block-on-all, ranking, capping, unknown-nutrition rejection.
- `DishControllerIntegrationTest` — 401, allergy exclusion end to end,
  dishes with no approved recipe hidden, null nutrition, calorie ceiling,
  validation.
- `MenuServiceQueryTest` — recipes attached to meals; meal kept with an empty
  list when nothing qualifies.
- `MenuToolsTest` / `MenuToolsEndToEndIntegrationTest` — parameter defaults and
  clamping, unattributed caller refused, six-tool surface.
- Full gate: `./mvnw clean verify -Pintegration-test`, `checkstyle:check`,
  `spotless:check`.

## Related

- `docs/apis/recipes/get-dishes-suggestions.md`
- `docs/brs/mcp-tools.md` — BR-MCP-002 (six tools), BR-MCP-008 (allergies)
- `docs/feats/mcp-menu-tools.md` — amended non-goal
- ADR-013 (in-process tools), ADR-016 (moderation and `score`)
