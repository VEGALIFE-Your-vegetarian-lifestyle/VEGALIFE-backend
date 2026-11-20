# Feature: Ingredient-Level Recipe Nutrition

## Status

Implemented

## Date

2026-11-05

## Owners

Backend implementation. Design approved by the project owner.

## Problem

Recipe nutrition was author-entered and stored directly on `recipe`. It was
unfalsifiable — nothing connected the number to the ingredients listed — so it
could not be trusted for meal planning, and the suggest-dishes nutrition filters
had to be advisory. Members were being asked for figures they do not know
(grams of protein per recipe) while the system already knew the ingredients.

An AI-estimate approach was built first and reverted: a generated number is
indistinguishable from a fabricated one. A third-party nutrition API was also
rejected: it adds a network dependency and per-request cost to a hot read path
without answering "what is in this catalogue row".

## Goal

Derive a recipe's nutrition from its ingredients, where each ingredient carries
its own measurement basis and per-unit nutrition. The number must be traceable,
comparable across recipes, and correctable at the source.

## Success Metrics

- Every recipe with fully curated ingredients reports nutrition that equals the
  arithmetic sum of its lines, with no manual entry anywhere in the member path.
- Editing one ingredient's nutrition updates every recipe that uses it, within
  the same transaction.
- The suggest-dishes endpoint filters on all five macros, in both directions.
- A recipe with any uncurated ingredient reports `null` nutrition rather than a
  partial sum, and clients can tell the two apart.

## Scope

### In scope

- Five `DECIMAL(12,6)` nutrition columns on `ingredient`, denominated per one
  unit.
- A single `unit` column on `ingredient`: `G`, `ML` or `PIECE`. A `PIECE` row's
  values are the estimate for one piece; there is no piece-weight column.
- `RecipeNutritionCalculator` (pure arithmetic) and `RecipeNutritionService`
  (persist-and-recompute).
- Recompute on recipe create, and on admin edits to ingredient unit or nutrition.
- `nutritionResolved` on recipe responses.
- Admin create/update/read of unit and nutrition, with all-or-nothing validation.
- Full macro ranges on `GET /api/dishes/suggestions` and the `suggest_dishes`
  AI tool.
- Seed 50 vegan ingredients from USDA FoodData Central (SR Legacy, CC0).

### Out of scope

- Per-serving nutrition. Totals describe the whole recipe (V42 convention);
  clients divide by `servings`.
- Any per-100 g canonical basis, density, portion table, ingredient group or
  `measurement_type`.
- Units beyond `G`/`ML`/`PIECE` (no `tsp`, `tbsp`, `cup`, `bunch`, `slice`).
- Member recipe editing. Members still cannot correct a recipe; that remains an
  admin path.
- Migrating legacy free-text units that have no honest mapping — those stay
  `null` by design.
- Nutrition on `dish` (a dish aggregates recipe variations; it has no amounts).

## Design Overview

### Data model

`ingredient` (see `db/migration/V49__ingredient_unit_and_nutrition.sql`):

| Column | Type | Meaning |
|---|---|---|
| `unit` | `VARCHAR(10)` | `G`, `ML` or `PIECE`; `null` = undecided |
| `calories` | `DECIMAL(12,6)` | kcal per **one** unit |
| `protein_g` | `DECIMAL(12,6)` | g per one unit |
| `carbohydrate_g` | `DECIMAL(12,6)` | g per one unit |
| `fat_g` | `DECIMAL(12,6)` | g per one unit |
| `fiber_g` | `DECIMAL(12,6)` | g per one unit |

Constraints:

- `chk_ingredient_unit` — the column is one of the three codes, or null.
- `chk_ingredient_nutrition_complete` — all five values, or none. A half-filled
  row cannot exist.
- `chk_ingredient_nutrition_non_negative` — no negative values.
- `idx_ingredient_missing_nutrition` — partial index over rows still awaiting
  curation, so the review queue is cheap.

`recipe_ingredient` loses `unit`: the amount's meaning comes from the ingredient.

### Computation

```
resolved(ingredient) ≡ ingredient.unit ≠ null
                    ∧ all five nutrition values ≠ null

total(macro) = Σ (line.amount × ingredient.macro_per_unit)
               over the recipe's lines, present only when every line resolved
```

Two details matter:

- **Round once for the total.** Line products are summed unrounded; only the final
  totals are scaled to 2 with `HALF_UP`. Three lines of 0.005 kcal contribute
  0.015 and become 0.02, not 0.03.
- **Round each line again for display.** A read also reports each line's own
  contribution, scaled to 2 individually, so displayed lines need not sum exactly
  to the displayed total (BR-RECP-007). The stored total stays authoritative.
- **All-or-nothing.** If any line is unresolved, all five totals are `null`. A
  partial sum would look authoritative while silently omitting a macro.

### Recompute triggers

| Trigger | Effect |
|---|---|
| `POST /api/recipes` | Totals computed from the lines just written |
| Admin updates an ingredient's unit or any nutrition value | Every referencing recipe recomputed in the same transaction |
| Admin approves an ingredient | No recompute — approval does not change arithmetic |
| Admin renames or soft-deletes an ingredient | No recompute |

Persisted totals (rather than derived-on-read) keep the suggest-dishes calorie
filter a plain indexed column comparison instead of a per-row aggregate.

### API surface

- `POST /api/recipes` — no nutrition in the request; `unit` is now optional and
  applies only to a brand-new ingredient.
- `GET /api/recipes/me`, `GET /api/recipes/{recipeId}` — include the five totals
  plus `nutritionResolved`, and per-line values on each ingredient line.
- `GET /api/ingredients` — `unit`, the five per-unit values and
  `nutritionResolved`, so a client can interpret the amount it is about to submit.
- `GET /api/admin/recipes` — five totals, `nutritionResolved`, and per-line values
  on each ingredient entry.
- `GET /api/dishes/suggestions` — `minCalories`/`maxCalories`,
  `minProtein`/`maxProtein`, `minCarbohydrate`/`maxCarbohydrate`,
  `minFat`/`maxFat`, `minFiber`/`maxFiber`.
- `suggest_dishes` AI tool — the same ten bounds.
- Admin ingredient endpoints — `unit`, five nutrition values, and
  `nutritionResolved` on read.

The read-surface completion is specified separately in
[nutrition-read-surfaces.md](nutrition-read-surfaces.md).

## Behaviour and edge cases

| Situation | Behaviour |
|---|---|
| New ingredient, no unit supplied | Created with `unit = null`; recipes using it are uncomputable until an admin resolves it |
| New ingredient, unit supplied | Unit stored; nutrition still null until curated |
| Existing ingredient, caller supplies a unit | Caller's unit **ignored**; the row's own unit governs |
| Caller supplies `kg` / `tbsp` / `cup` for a new ingredient | `400` — a different unit is not a spelling variant |
| Null vs `0` | Preserved end-to-end. Water is 0 kcal; uncurated is `null` |
| Ingredient unit changed `G` → `ML` | Allowed silently; all referencing recipes recomputed; WARN log with the affected count |
| Admin sends one nutrition value on a complete row | Accepted as a correction |
| Admin sends one nutrition value on a row with none | `400` — would leave a half-answer |
| Admin sends nutrition with no unit | `400` — a per-unit figure with no unit means nothing |
| Recipe with zero ingredient lines | Not creatable (request requires ≥1), and the calculator returns `null` |
| Legacy unit `bó`, `vừa đủ`, or a typo | Migration leaves `unit = null`; amount unchanged |

## Acceptance Criteria

1. Creating a recipe with fully curated ingredients returns computed totals that
   equal the sum of `amount × per-unit` per macro, rounded once to 2 dp.
2. Creating a recipe where one ingredient is uncurated returns all five totals as
   absent, with `nutritionResolved = false`.
3. Sending `calories`, `proteinG`, `carbohydrateG`, `fatG` or `fiberG` to
   `POST /api/recipes` has no effect on the stored recipe.
4. An admin changing an ingredient's nutrition value immediately changes the
   totals of every recipe referencing it.
5. An admin changing an ingredient's unit succeeds without a confirmation step,
   and the affected recipes are recomputed.
6. An admin creating an ingredient with two of five nutrition values gets `400`.
7. `GET /api/dishes/suggestions?maxFat=5` excludes recipes whose fat is unknown.
8. `GET /api/dishes/suggestions?minFiber=8` excludes recipes whose fibre is
   unknown.
9. `V50` seeds exactly 50 ingredients, every one with a unit and all five values.
10. Re-running the seed inserts nothing and modifies nothing.

## Risks

- **Silent unit change.** `200 g` of rice and `200 ml` of rice occupy the same
  cell. No error is raised. Mitigated only by the WARN log. Accepted.
- **Uncorrectable member recipes.** There is no member update endpoint, so a
  mis-unit-ed recipe can only be deleted and recreated.
- **Catalogue coverage is the real gate.** Until the catalogue is curated,
  nutrition filters return fewer dishes and AI menu building sees a thinner
  catalogue. The 50-row seed covers common staples, not everything.
- **USDA approximation.** Values are means over samples, and some Vietnamese
  staples map to the nearest FDC entry. Recorded in the migration so the mapping
  is inspectable.
- **`amount` cannot be "to taste".** `@Positive` makes `vừa đủ` inexpressible.

## References

- [ADR-022: Derive recipe nutrition from ingredient nutrition measured per one
  unit](../adrs/022-ingredient-level-nutrition.md)
- Business rules: [Recipes](../brs/recipes.md) — BR-RECP-004, BR-RECP-005,
  BR-RECP-006
- API references: [POST /api/recipes](../apis/recipes/post-recipes.md),
  [GET /api/recipes/me](../apis/recipes/get-recipes-me.md),
  [GET /api/recipes/{recipeId}](../apis/recipes/get-recipes-recipeid.md),
  [GET /api/dishes/suggestions](../apis/recipes/get-dishes-suggestions.md),
  [Admin dish and ingredient
  CRUD](../apis/admin/dish-and-ingredient-crud.md)
- Data dictionary: [Ingredient, Recipe,
  Recipe_Ingredient](../arch/data-dictionary.md)
