# BR-DISH-002: Dish Ingredient Suggestions and Advisory Required Flag

## Rule ID

`BR-DISH-002`

## Status

Active

## Statement

A dish carries a curated set of associated ingredients (`dish_ingredient`). Each entry is either
**required** (`required = true`) or **suggested** (`required = false`). Only an administrator sets
or clears the flag. When a recipe for that dish omits a `required` ingredient, the omission is
reported as a warning on the recipe — it never prevents the recipe from being created, approved, or
listed.

## Rationale

"Dish X normally contains ingredient Y" is real domain knowledge, but it is not a hard constraint.
Legitimate vegan variants routinely substitute the defining ingredient (zucchini or rice noodles
standing in for wheat pasta; cauliflower rice; aquafaba), so a blocking rule would refuse correct
recipes. Making the flag advisory keeps the knowledge visible where it is useful — the create form
and the review queue — while leaving the judgement to the member and, ultimately, the reviewing
administrator. This is the same position ADR-016 took for name moderation: the human decides, the
system surfaces.

## Scope & Exceptions

- Applies to every authentication-visible read of a dish's suggestions and to every recipe mapped
  for its author or an administrator.
- `required` is advisory only: no request is rejected and no database constraint enforces the
  ingredient set. A recipe missing a required ingredient is a valid recipe.
- The rule does **not** extend to `menu_detail.dish_id`. A menu references a dish directly, with no
  recipe and therefore no ingredient set to compare against; that path is unguarded.
- Ingredients in the suggestion list are the dish's own curated entries, not a global catalogue. An
  ingredient can be required for one dish and absent from another.

## Enforcement

- Data: `dish_ingredient (dish_id, ingredient_id, required, removed_at)` — one row per dish/ingredient
  pair; `required` defaults to `false`.
- Curated writes: `AdminDishIngredientService` (`service/admin/`) — the only path that can set
  `required = true`.
- Derived warning: `missingRequiredIngredients` on `RecipeResponse` (author) and
  `AdminRecipeListResponse` (review queue), computed on read from the dish's active required entries
  minus the recipe's ingredient ids. Never stored, so an admin's later `required` change applies to
  existing recipes on their next read.
- Member read: `GET /api/dishes/{dishId}/ingredients/suggestions`.
- API references: `docs/apis/recipes/get-dishes-dishid-ingredients-suggestions.md`,
  `docs/apis/admin/dish-ingredient-crud.md`.

## Last Reviewed

2026-11-02, by Vegalife backend team

---

# BR-DISH-003: Dish Ingredient Entries Come From Approved Recipes or an Administrator, and Deletion Is Permanent for Seeding

## Rule ID

`BR-DISH-003`

## Status

Active

## Statement

A `dish_ingredient` entry is created by exactly two paths:

1. an administrator adds it explicitly, choosing `required`; or
2. a recipe for that dish is **approved**, which adds one `required = false` entry per ingredient
   that has no entry at all — neither active nor withdrawn.

Automatic seeding never modifies an existing entry: it never changes `required` and never clears
`removed_at`. Once an administrator withdraws an entry, no later recipe approval reinstates it;
only an explicit administrator re-add does.

## Rationale

Recipes start `PENDING` and can be rejected (BR-DISH-001), so seeding from unapproved submissions
would let rejected or junk content permanently pollute a list shared by every user, recipe and menu
that references the dish. Gating on approval keeps the shared list consistent with the moderation
model's own principle: a human approval is the guarantee. The permanence of a withdrawal follows
from the same idea — an administrator's correction must not be undone by the next member
submission, or the correction is not a correction at all.

## Scope & Exceptions

- Applies to both recipe-approval entry points: a recipe approved individually from the admin recipe
  endpoints, and a recipe approved as a component of an approved post. Both use one shared seeding
  method, so they cannot diverge.
- Seeding adds only `required = false`. It never marks anything required — that is exclusively an
  administrator decision.
- Seeding runs once per approval. Re-approving an already-approved recipe is a no-op, and an entry
  that already exists is left untouched.
- Approving a recipe also approves its `PENDING` dish and ingredient names in the same transaction
  (BR-DISH-001), so a seeded entry is normally visible to members as soon as the approval commits.
  A name that is `REJECTED` is left rejected, and a seeded entry pointing at it stays hidden from
  the member-facing list until the name is explicitly re-approved.
- Existing recipes are not backfilled. The rule takes effect from the first approval after it ships.

## Enforcement

- Shared seeding: `DishIngredientSeedingService` in `service/recipe/`, called from
  `AdminRecipeService.approveRecipe` and `AdminPostService.approveComponents`.
- Suppression: `dish_ingredient.removed_at` is the tombstone; the seeding insert checks for any
  existing row for the pair, active or withdrawn.
- Restore: `POST /api/admin/dishes/{dishId}/ingredients` re-add clears `removed_at`.
- Member visibility: `GET /api/dishes/{dishId}/ingredients/suggestions` returns `APPROVED`
  ingredients on an `APPROVED` dish only.
- Name approval: `AdminRecipeService.approveRecipe` and `AdminPostService.approveComponents` both
  approve `PENDING` dish/ingredient names they reference, in the same transaction
  (BR-DISH-001).
- API references: `docs/apis/admin/dish-ingredient-crud.md`,
  `docs/apis/recipes/get-dishes-dishid-ingredients-suggestions.md`,
  `docs/apis/admin/recipe-crud.md`.

## Last Reviewed

2026-11-02, by Vegalife backend team
