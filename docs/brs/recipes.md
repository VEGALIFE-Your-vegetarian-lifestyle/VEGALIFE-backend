# Business Rules: Recipes

## Rule Index

| Rule ID | Title | Status | Last Reviewed |
|---------|-------|--------|---------------|
| BR-RECP-001 | Recipe Ownership Comes from Authentication | Active | 2026-10-01 |
| BR-RECP-002 | Ingredient and Dish Names Are Unique Case-Insensitively | Active | 2026-10-01 |
| BR-RECP-003 | Recipe Name, Instructions, Servings, and Dish Are Required | Active | 2026-11-05 |
| BR-RECP-004 | Recipe Nutrition Is Derived From Ingredient Nutrition, Never Authored | Active | 2026-11-05 |
| BR-RECP-005 | Ingredient Nutrition Is Per One Unit, and All-or-Nothing | Active | 2026-11-05 |
| BR-RECP-006 | A Recipe Reports No Nutrition Unless Every Ingredient Is Resolved | Active | 2026-11-05 |
| BR-RECP-007 | A Line's Nutrition Is Derived, Rounded Per Line, and Never Partial | Active | 2026-11-06 |

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

A recipe must have a non-blank `name`, non-blank `instructions`, `servings` of at
least 1, a non-blank `dishName` (the recipe always belongs to a dish), and at
least one ingredient entry with a name and a positive amount. A request missing
any of these is rejected with `400 Bad Request` and no recipe is persisted.

A line's `unit` is **not** required. It is accepted only for an ingredient name
that does not exist yet, where it becomes that ingredient's own unit
(BR-RECP-005). For an existing ingredient the submitted unit is ignored.

## Rationale

Every column backing these fields in the schema is `NOT NULL` (`recipe.name`,
`recipe.instructions`, `recipe.servings`, `recipe.dish_id`), and a recipe without
ingredients has no culinary value and cannot be linked meaningfully to nutrition
or meal-plan features. Rejecting at the API boundary keeps partial rows from ever
reaching the database.

`unit` moved out of this rule when it moved off the recipe line: a member writing
`"2 cà chua"` is describing a real recipe, and forcing them to name a unit they do
not think in would either block valid input or manufacture a value.

## Scope & Exceptions

Applies to `POST /api/recipes`. Optional fields (`description`,
`prepTimeMinutes`, `cookTimeMinutes`, `difficulty`) may be omitted; when supplied,
`difficulty` must be one of `EASY`, `MEDIUM`, `HARD` and times must be
non-negative.

`amount` is `@NotNull` and `@Positive` with up to 3 decimals, which means
"as much as needed" (`vừa đủ`) cannot be expressed. Salt-to-taste must carry a
number or be omitted.

## Enforcement

- DTO: Bean Validation constraints on `RecipeCreateRequest` and its nested
  ingredient request (`RecipeIngredientItem`).
- Controller: invalid payloads are handled by the existing `GlobalExceptionHandler` → `400 Validation failed`.
- API reference: `docs/apis/recipes/post-recipes.md`.

## Last Reviewed

2026-11-05, by Vegalife backend team

---

# Business Rule: Recipe Nutrition Is Derived From Ingredient Nutrition, Never Authored

## Rule ID

`BR-RECP-004`

## Status

Active

## Statement

A recipe's `calories`, `proteinG`, `carbohydrateG`, `fatG` and `fiberG` are
computed from its ingredient lines and stored, never taken from a request. No API
accepts a recipe nutrition value from a client, and no code path assigns one
except the recompute service.

```
total(macro) = Σ (line.amount × ingredient.macro_per_unit)
```

Totals are rounded once, at the end, to 2 decimal places with `HALF_UP`. Line
values are not rounded.

## Rationale

An authored total is unfalsifiable: nothing connects it to the ingredients
listed, so two recipes with identical ingredients can carry different figures and
no query can say which is wrong. Deriving it makes the number traceable to the
rows that produced it, comparable across recipes, and correctable by fixing the
ingredient rather than by editing every recipe.

Rounding once at the end matters because rounding per line accumulates error:
three lines of 0.005 kcal are 0.015 in total and become 0.02, but rounded
individually they become 0.03.

## Scope & Exceptions

- Applies to every path that creates or changes a recipe's ingredient lines, and
  to every path that changes an ingredient's unit or nutrition.
- Persisted rather than computed on read, so the suggest-dishes nutrition filters
  remain an indexed column comparison. The recompute-on-write step is what keeps
  the stored value honest.
- There is deliberately no per-serving figure. Totals describe the whole recipe
  (V42 convention); clients divide by `servings`.

## Enforcement

- `RecipeNutritionCalculator` — the pure arithmetic, unit-tested with no Spring
  or database.
- `RecipeNutritionService.recomputeForRecipe` / `recomputeForIngredient` —
  persists the result inside the caller's transaction.
- `RecipeService.createRecipe` recomputes after writing the lines;
  `AdminIngredientService` recomputes after any unit or nutrition change.
- `RecipeCreateRequest` carries no nutrition field, so an offered value is
  ignored rather than stored.
- ADR: `docs/adrs/022-ingredient-level-nutrition.md`.

## Last Reviewed

2026-11-05, by Vegalife backend team

---

# Business Rule: Ingredient Nutrition Is Per One Unit, and All-or-Nothing

## Rule ID

`BR-RECP-005`

## Status

Active

## Statement

Every ingredient has exactly one measurement unit — `G`, `ML` or `PIECE` — and its
five nutrition values are denominated **per one of that unit**, never per 100 g
and never per some other unit.

An ingredient is *resolved* only when its unit is non-null **and** all five values
are present. A partially filled ingredient cannot be stored: an admin request that
would leave some values set and others null is rejected with `400`.

`null` means "not provided" and is never coerced to `0`.

## Rationale

A value with no basis is unreadable: 365 kcal means nothing until you know whether
it is per gram, per 100 g or per serving. Putting the basis in the same row
removes the question. Restricting each ingredient to one of three units means a
recipe total is a sum with no conversion anywhere — and therefore no conversion
to get wrong. Spoons, cups and bunches would each require a density or portion
table to reach a gram, since they cannot be converted without knowing the specific
food.

All-or-nothing prevents a half-answer from being published as a whole one: a
recipe total built from three of five macros looks complete and is not.

`null` stays distinct from `0` because water is legitimately 0 kcal with 0 g of
everything. Treating missing as zero would make every uncurated ingredient look
like water.

## Scope & Exceptions

- `PIECE` is the honest exception: a piece has no constant size, so its per-piece
  value is a curation decision rather than a measurement. That estimate is stored
  as the per-one-unit value; there is no separate piece-weight column.
- Changing an ingredient's unit is allowed **silently** — no guard, no
  confirmation. Stored amounts are bare numbers, so `200 g` of rice reinterpreted
  as `200 ml` of rice changes the recipe's meaning with no error. Every affected
  recipe is recomputed and the change is logged at WARN with the affected count.
- Recording nutrition without a unit is refused: a per-unit figure with no unit is
  a number with no meaning.
- Legacy free-text units with no honest mapping (`bó`, `vừa đủ`, typos) are left
  `null` by the migration rather than guessed.

## Enforcement

- Database: `chk_ingredient_unit` (`IN ('G','ML','PIECE')` or null) and
  `chk_ingredient_nutrition_complete` (five values or none) in
  `V49__ingredient_unit_and_nutrition.sql`; `DECIMAL(12,6)` precision so
  per-gram figures such as 0.0130 g protein/g survive.
- `AdminIngredientService.requireCompleteNutrition` and
  `requireUnitForNutrition` reject a half-answer after merging with the stored
  row, so a single value can still be corrected on a complete row.
- `Unit.fromInput` normalises spelling variants and refuses genuinely different
  units (`kg`, `tbsp`, `cup`).
- ADR: `docs/adrs/022-ingredient-level-nutrition.md`.

## Last Reviewed

2026-11-05, by Vegalife backend team

---

# Business Rule: A Recipe Reports No Nutrition Unless Every Ingredient Is Resolved

## Rule ID

`BR-RECP-006`

## Status

Active

## Statement

A recipe has nutrition only when **every** one of its ingredient lines references
a resolved ingredient. If any line's ingredient is unresolved, or the recipe has
no lines, all five recipe nutrition columns are `null` and the response reports
`nutritionResolved = false`.

A partial sum is never returned.

## Rationale

A sum over the ingredients the system happens to know about is not a total, but it
reads like one. A recipe listing rice, tofu and an uncurated vegetable would
advertise the calories of rice and tofu alone, and the reader has no way to see
that the largest contribution is missing. Refusing to answer is the honest
response, and `nutritionResolved` makes the refusal explicit rather than
indistinguishable from an empty field.

The consequence is accepted: nutrition filters treat `null` as failing, so a
partly curated recipe is invisible to `minCalories` and friends until the
catalogue is fixed.

## Scope & Exceptions

- Applies to every read surface that returns recipe nutrition, including
  `GET /api/recipes/me`, `GET /api/recipes/{recipeId}`, the suggest-dishes
  recommendations, the recommended recipes embedded in a menu detail read, the AI
  menu tools, and `GET /api/admin/recipes`.
- Not applied to surfaces that do not return nutrition at all: the AI
  `suggest_dishes` tool filters on the columns without echoing them.
- Applies equally to `0`: a resolved recipe whose every ingredient is 0 kcal is a
  legitimate answer and reports `nutritionResolved = true` with totals of `0.00`.
- Not applied to a dish: a dish aggregates recipe variations and has no amounts of
  its own, so it carries no nutrition.

## Enforcement

- `RecipeNutritionCalculator.totals` returns `null` if any line is unresolved or
  the line list is empty.
- `RecipeNutritionCalculator.lineNutrition` returns `null` for an ingredient
  failing `Ingredient.hasResolvedNutrition()` (unit present plus all five values).
- `RecipeNutritionService` writes all five columns or none.
- `RecipeService.toResponse`, `RecipeSummaryMapper`, `AdminRecipeService.toResponse`
  and `AdminIngredientResponse` set `nutritionResolved` explicitly rather than
  leaving it to a mapper default.
- API references: `docs/apis/recipes/get-recipes-me.md`,
  `docs/apis/recipes/get-recipes-recipeid.md`,
  `docs/apis/recipes/get-dishes-suggestions.md`,
  `docs/apis/admin/get-recipes.md`.

## Last Reviewed

2026-11-05, by Vegalife backend team

---

# Business Rule: A Line's Nutrition Is Derived, Rounded Per Line, and Never Partial

## Rule ID

`BR-RECP-007`

## Status

Active

## Statement

Every response that returns a recipe ingredient line also returns that line's own
nutrition: the line's `amount` multiplied by the ingredient's per-unit value,
rounded `HALF_UP` to scale 2. The five values are either all present or all
`null`, and a line carries `nutritionResolved` to say which.

Line values are rounded **individually**. Recipe totals remain rounded once over
the exact sum of unrounded lines (BR-RECP-006). The two therefore need not agree
exactly: three lines of `0.005` kcal display as `0.01` each against a total of
`0.02`.

No client may assert that the displayed line values sum to the displayed total.

## Rationale

A total the reader cannot decompose is not reviewable. Before this rule a member
saw five recipe totals and an ingredient list with no connection between them: a
total that looked wrong could only be diagnosed by opening the admin ingredient
screen. Per-line values make the arithmetic auditable and let the interface say
which ingredient drives the figure.

Rounding each line to the same scale as the total is a deliberate trade-off,
recorded in ADR-022 decision 11. The alternative — unrounded per-line products of
a `DECIMAL(12,6)` value by an arbitrary amount — would preserve the sum identity
but push the formatting of a very long decimal onto every client. The selected
scale keeps the wire format uniform at the cost of a discrepancy below the
display precision of any kcal or gram figure the product shows.

Totals stay authoritative: they are persisted, filtered on in SQL, and rounded
once. They are never recomputed from the rounded per-line values.

## Scope & Exceptions

- Applies to `RecipeIngredientResponse` (`POST /api/recipes`,
  `GET /api/recipes/me`, `GET /api/recipes/{recipeId}`) and to
  `AdminRecipeListResponse.IngredientItem` (`GET /api/admin/recipes` and the
  admin recipe detail/update/approve/reject responses).
- Both shapes are produced by one shared helper, so they cannot diverge.
- An unresolved ingredient line reports five `null`s and
  `nutritionResolved = false` — never a zero, never a partial set (BR-RECP-006).
- Not applied to the dish ingredient suggestion surfaces
  (`DishIngredientSuggestionResponse`, `AdminDishIngredientResponse`): they return
  a curated name list with no `amount`, so there is no line to price.
- Not applied to a recipe total, which is rounded once rather than per line.

## Enforcement

- `RecipeNutritionCalculator.scaledLineNutrition` is the only implementation of
  the per-line derivation; it returns `null` for an unresolved ingredient.
- `RecipeService.ingredientResponseOf` and `AdminRecipeService.toIngredientItem`
  both call it rather than multiplying locally.
- `RecipeNutritionCalculator.totals` deliberately does **not** use the scaled
  helper, so the total stays exact-then-rounded-once.
- Tests pin the divergence explicitly:
  `RecipeNutritionCalculatorTest.scaledLineNutrition_roundsEachLineIndependently`.
- API references: `docs/apis/recipes/post-recipes.md`,
  `docs/apis/recipes/get-recipes-me.md`,
  `docs/apis/recipes/get-recipes-recipeid.md`,
  `docs/apis/admin/get-recipes.md`.

## Last Reviewed

2026-11-06, by Vegalife backend team
