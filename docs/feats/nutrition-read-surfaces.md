# Feature Spec: Nutrition on Read Surfaces

## Status

Implemented

## Author / owner

Vegalife backend team. Scope confirmed by the project owner on 2026-11-06.

## Summary

Complete the read contract for ingredient-level nutrition: expose an ingredient's
`unit` and per-unit values on the two member-facing ingredient lists, expose the
per-unit values on every recipe line, and give the admin recipe list the same
totals and per-line values the member recipe surfaces already return.

## Problem / motivation

[ADR-022](../adrs/022-ingredient-level-nutrition.md) moved nutrition onto
`ingredient` and made `amount` meaningful only in the ingredient's own unit. Three
read surfaces were not updated, so clients hold half the contract:

1. **`GET /api/ingredients` returns no `unit`.** This is the autocomplete behind
   `POST /api/recipes`. For an existing ingredient the submitted unit is ignored
   and `amount` is read in the stored unit — which the client cannot see. A member
   who autocompletes `cà chua` and types `2 quả` believes they described two
   tomatoes; if the row is curated in grams, they created 2 g. The request succeeds
   and the stored recipe is wrong, with no error and no way for the member to
   detect it.
2. **`RecipeIngredientResponse` returns no nutrition.** A recipe read shows five
   totals and the ingredient list, but nothing connects them. A member cannot see
   which line contributed what, and a total that looks wrong cannot be diagnosed
   from the response — the client would have to open the admin ingredient screen.
3. **`AdminRecipeListResponse` returns no nutrition.** It carries `unit` and
   `amount` per line but no totals and no `nutritionResolved`, so the admin recipe
   list cannot show — or filter for — the recipes whose nutrition is still
   uncomputable. That is the exact queue an admin needs to work through after V50.

`IngredientResponse` also withholds the per-unit values. Unlike a nutrition
*label* (a rendered claim about a finished dish), these are the numbers a client
needs to render a line contribution, and they are already public to every
authenticated member through the recipe totals they roll up into.

## Goals

- A client that has an ingredient id can state which unit its `amount` is in.
- A recipe read exposes per-line nutrition that visibly reconciles with the totals.
- The admin recipe list exposes recipe nutrition and per-line nutrition, so
  unresolved recipes are identifiable without opening each ingredient.
- No surface that already lacks nutrition gains it beyond what is listed here.

## Non-goals

- **No `unit` on dish ingredient surfaces.** `GET /api/admin/dishes/{dishId}`'s
  `suggestedIngredients` and `DishIngredientSuggestionResponse`
  (`GET /api/dishes/{dishId}/ingredients/suggestions`) stay unchanged. They
  describe a curated *name list*, not a priced quantity; there is no `amount` for
  a unit to govern.
- No `dish`-level nutrition. A dish aggregates recipe variations and has no amounts.
- No per-serving values. Totals stay whole-recipe.
- No change to `AdminDishIngredientResponse`.
- No member recipe-update endpoint (still an admin path).
- No new filter, sort or pagination parameter on any endpoint.
- No recompute on read: recipe totals stay persisted columns.

## Requirements

### Functional Requirements

- [ ] FR-001: `GET /api/ingredients` items include `unit` — `G`, `ML`, `PIECE` or
      `null` — from the ingredient row.
- [ ] FR-002: `GET /api/ingredients` items include `calories`, `proteinG`,
      `carbohydrateG`, `fatG`, `fiberG` — per **one** unit, each `null` when
      uncurated, never `0` to mean unknown.
- [ ] FR-003: `GET /api/ingredients` items include `nutritionResolved`, `true` only
      when the unit and all five values are present.
- [ ] FR-004: `RecipeIngredientResponse` (returned by `POST /api/recipes`,
      `GET /api/recipes/me` and `GET /api/recipes/{recipeId}`) includes the same
      five per-line values, computed as `line.amount × ingredient.value` and rounded
      `HALF_UP` to scale 2, plus `nutritionResolved` for that line.
- [ ] FR-005: A line whose ingredient is unresolved reports all five values as
      `null` and `nutritionResolved` as `false` — never a partial set.
- [ ] FR-006: `AdminRecipeListResponse` includes `calories`, `proteinG`,
      `carbohydrateG`, `fatG`, `fiberG` and `nutritionResolved`, read from the
      persisted recipe columns.
- [ ] FR-007: `AdminRecipeListResponse.IngredientItem` includes the same five
      per-line values and `nutritionResolved` as FR-004, produced by the same code
      path so the two shapes cannot drift.
- [ ] FR-008: Dish ingredient suggestion responses are byte-identical to before
      this change.
- [ ] FR-009: `docs/apis/` references for `GET /api/ingredients`,
      `POST /api/recipes`, `GET /api/recipes/me`, `GET /api/recipes/{recipeId}` and
      `GET /api/admin/recipes` list the new fields.

### Non-Functional Requirements

- [ ] NFR-SCALE-001: No new query. Every added field is read from a row already
      loaded by the existing query — `IngredientResponse` from the `ingredient` page,
      per-line values from the `RecipeIngredient` rows already fetched by the admin
      mapper and the recipe service.
- [ ] NFR-SEC-001: No new endpoint and no change to authorization. `GET /api/ingredients`
      remains authenticated-only; admin nutrition stays confined to the admin list,
      which is already `ADMIN`-guarded.
- [ ] NFR-MAINT-001: One line-nutrition function, called from both the member and
      admin mappers. Two independent multiply-and-round implementations are the
      failure mode this requirement exists to prevent.
- [ ] NFR-MAINT-002: The layered package ranking is unchanged; the shared
      computation lives in `service/` (rank 5) and is consumed by `dto/mapper`
      (rank 4) only through the value it already produced, not by importing upward.
- [ ] NFR-PERF-001: Would be performance-critical at scale at ~70 dishes x 10
      recipes for `GET /api/dishes/suggestions`; that endpoint is unchanged here.
      For the batch-fetch endpoints touched here, see Design overview.

## Design overview

Schema, entities, migrations and write paths are untouched. This is a read-contract
completion.

- **Per-line values.** `RecipeNutritionCalculator.lineNutrition(amount, ingredient)`
  already exists and returns the unrounded product; it is currently used only by
  `totals(...)`. Both mappers call it and round with a single shared helper, so the
  member and admin line shapes are produced by one expression. Rounding each line is
  the confirmed choice; see Risks for the display consequence.
- **Member recipe lines** are built in `RecipeService` (two call sites: the read path
  and the create path). Both already hold the `Ingredient`, so the values are a pure
  function of data in hand.
- **Admin recipe lines** are built in `AdminRecipeMapper.toIngredientItem`, which
  already receives the `RecipeIngredient` with its `Ingredient` resolved. The batch
  list path therefore gains no query — which is why NFR-SCALE-001 holds without a
  batch lookup design: the association is already joined.
- **Admin recipe totals** come from the persisted `recipe` columns via the existing
  MapStruct mapping, so they cannot disagree with what the suggest-dishes filter
  sees.
- **`IngredientResponse`** is mapped by MapStruct from the `Ingredient` page already
  loaded. Adding fields is additive.

Because `AdminRecipeListResponse` feeds the admin batch list, the per-line
computation runs per line per recipe. At 20 recipes x 10 lines that is 200
multiplies of in-memory `BigDecimal`s — no I/O, no measurable cost. The requirement
is recorded so a future change that swaps the eagerly-joined association for a lazy
one does not silently turn this into an N+1.

## Success metrics

- A member recipe read for a resolved recipe returns per-line values whose sum
  equals the recipe totals to within rounding at 2 dp.
- Zero `unit: null` ambiguity remains on the create-recipe autocomplete: every item
  the member can select states its unit.
- The admin recipe list can identify an unresolved recipe from a single response,
  with no follow-up call.

## Acceptance criteria

**As a** member creating a recipe from autocompleted ingredients, **I want** to see
the unit each amount will be read in, **so that** the quantity I typed is the
quantity stored.

**As a** member reading my recipe, **I want** to see each line's contribution,
**so that** I can tell which ingredient drives the total.

**As an** administrator, **I want** the recipe list to show nutrition and which
recipes lack it, **so that** I can work the curation queue without opening each
recipe.

- [ ] Given a curated ingredient, when `GET /api/ingredients` is called, then its
      item carries `unit`, all five per-unit values and `nutritionResolved: true`.
- [ ] Given an uncurated ingredient, when `GET /api/ingredients` is called, then its
      item carries `unit: null`, five `null` values and `nutritionResolved: false`.
- [ ] Given a recipe whose ingredients are all curated, when it is read, then each
      line carries five non-null values and `nutritionResolved: true`, and their sum
      reconciles with the recipe totals.
- [ ] Given a recipe with one uncurated ingredient, when it is read, then the
      uncurated line reports five `null`s and `false`, the recipe totals remain
      `null`, and no line reports a partial sum.
- [ ] Given `GET /api/admin/recipes`, when a recipe is unresolved, then the item
      shows `nutritionResolved: false` and the offending line is identifiable.
- [ ] Given `GET /api/dishes/{dishId}/ingredients/suggestions`, when called, then
      the response shape is unchanged.

## Risks / open questions

- **Per-line rounding makes the breakdown not add up exactly.** Each line is
  rounded independently to 2 dp while the total is rounded once over the exact sum.
  Three lines of `0.005` kcal display as `0.01 + 0.01 + 0.01 = 0.03` against a total
  of `0.02`. This is the confirmed trade-off (exactness of the stored total is kept;
  the displayed breakdown may differ from the displayed total by under `0.01`).
  Accepting it means the UI must not assert that the visible line values sum to the
  visible total.
- **Per-unit values become skeleton-key visible.** After FR-002, any authenticated
  member can read a curated ingredient's per-unit nutrition directly, rather than
  only as a rolled-up total. This is intended (it is the same information), but it
  makes the catalogue explicitly public and should not later be treated as a leak.
- **`null` is serialised, not omitted.** There is no `NON_NULL` inclusion on these
  DTOs, so every new field is present with `null` when unknown. Clients must branch
  on `nutritionResolved`, not on field absence.
- **`docs/apis/recipes/get-dishes-suggestions.md` incorrectly states that nutrition
  fields are omitted when `null`.** Corrected in the same change as FR-009 since the
  new fields make the distinction load-bearing.
- Open question (not blocking): should `GET /api/ingredients` expose
  `moderationStatus`, so the form can stop offering a `PENDING` name that will
  implicitly create a duplicate-free but unreviewed ingredient? Out of scope here;
  the existing endpoint already returns every approved and unapproved row.
