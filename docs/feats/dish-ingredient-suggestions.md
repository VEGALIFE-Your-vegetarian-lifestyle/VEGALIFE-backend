# Feature Spec: Dish Ingredient Suggestions

## Status

Implemented (pending PR review)

## Author / owner

Vegalife backend team; requested during the "dish must have mandatory ingredients" discussion. Not yet driven by a GitHub issue — this spec is the intake artifact until one is filed.

## Summary

Give the shared `dish` row a set of associated ingredients (`dish_ingredient`) that an
administrator curates: each entry is either **required** or **suggested**. Members get that
list as a suggestion source when writing a recipe for the dish, and admins get a warning flag
when an approved recipe for the dish omits a required ingredient.

## Problem / motivation

`dish` is a shared, ownerless name (`docs/adrs/016-component-level-moderation.md`);
ingredients hang off `recipe` only (`recipe_ingredient`). Consequently:

- The create-recipe form has nothing to suggest beyond free-typed names, so a member writing
  "spaghetti" starts from an empty ingredient list and may submit a recipe that contains no
  noodles at all.
- An admin reviewing a recipe has no reference point for what that dish normally contains. Their
  only tools are approve / reject / soft-delete the whole recipe, and
  `AdminRecipeUpdateRequest` deliberately cannot change the ingredient list — so an admin cannot
  fix "spaghetti without noodles", only discard it.
- ADR-016's moderation model answers *"may this name be shown?"*, never *"is this a plausible
  recipe for this dish?"*. Nothing in the system currently accumulates the second kind of
  knowledge.

The underlying problem is not "reject bad recipes". It is that **no dish-level ingredient
knowledge exists**, so neither the member nor the reviewer has anything to work from.

## Goals

- A dish carries a curated, admin-controlled list of associated ingredients, each flagged
  `required` or not.
- A member creating a recipe for a dish can retrieve that list and use it to prefill / prompt
  their ingredient entry.
- Admin-approved recipes contribute their ingredients to the dish's list automatically, so the
  list improves without manual curation.
- An admin deleting a bad entry stays deleted — a later recipe containing that ingredient does
  not silently restore it.
- A recipe that omits a `required` dish ingredient is surfaced to its author and to the review
  queue as a **warning**, not rejected.

## Non-goals

- **Blocking** recipe creation when a required ingredient is absent. The flag is informational
  only; no HTTP 400 and no database constraint. Legitimate variations (zucchini noodles, rice
  noodles) must remain submittable, and an admin marking a new ingredient `required` must not
  retroactively make previously valid recipes unsubmittable.
- Enforcing the list on `menu_detail.dish_id`. A menu references a dish with no recipe, so there
  is no ingredient set to compare against; that path stays unguarded.
- Curating dish knowledge programmatically from names or embeddings. ADR-016 Option C already
  rejected list/centroid checks for names on capability grounds.
- Letting members edit dishes, ingredient lists, or recipes. BR-DISH-001 stands: a member's only
  write path is `POST /api/recipes`.
- Backfilling `dish_ingredient` from existing recipes. Existing data has no approval signal
  attached to it at backfill time, and seeding from unreviewed recipes is exactly what FR-012
  prevents.
- Ingredient-level quantities in the suggestion list. `dish_ingredient` answers "what normally
  goes in this dish", not "how much" — amounts belong to a specific recipe.

## Requirements

### Functional Requirements

- [x] FR-001: New table `dish_ingredient` with composite primary key `(dish_id, ingredient_id)`,
  FKs to `dish(id)` and `ingredient(id)` (both `ON DELETE CASCADE`), `is_required BOOLEAN NOT NULL
  DEFAULT FALSE` (exposed as `required` in the API), `removed_at TIMESTAMPTZ NULL`, `created_at`,
  `updated_at`.
- [x] FR-002: `GET /api/admin/dishes/{dishId}/ingredients` returns every active (`removed_at IS
  NULL`) entry for the dish, each with `ingredientId`, `name`, `moderationStatus`, `required`,
  `createdAt`, `updatedAt`. Admin-only.
- [x] FR-003: `POST /api/admin/dishes/{dishId}/ingredients` accepts a body of `{ingredientName,
  required}`; the name is trimmed and lowercased and resolved find-or-create the way
  `RecipeService` resolves ingredient names. The created entry's `required` defaults to `false`
  when omitted. Returns `201`.
- [x] FR-004: `PATCH /api/admin/dishes/{dishId}/ingredients/{ingredientId}` accepts `{required}`
  and nothing else. The ingredient identity cannot be changed — that is a delete plus a create.
- [x] FR-005: `DELETE /api/admin/dishes/{dishId}/ingredients/{ingredientId}` sets `removed_at`
  (soft delete) and returns `200`. The row is retained as a tombstone. Repeating it on an
  already-withdrawn entry is an idempotent `200` with no second write.
- [x] FR-006: Re-creating an entry whose row is soft-deleted clears `removed_at` and applies the
  supplied `required`, answering `200` (the row existed all along) rather than `201`. This is the
  only recovery path from an accidental delete.
- [x] FR-007: Admin create/update/delete refuse a dish that is missing or soft-deleted (`404`),
  and refuse a `REJECTED` or soft-deleted ingredient name (`400`), matching `RecipeService`'s
  existing handling.
- [x] FR-008: `GET /api/dishes/{dishId}/ingredients/suggestions` returns the dish's active entries
  for any authenticated member, each with `ingredientId`, `name`, `required`. It resolves only
  when the dish is `APPROVED` and not soft-deleted, and only returns entries whose ingredient is
  `APPROVED` — otherwise `404` for the dish, and non-`APPROVED` ingredients are omitted rather
  than returned with a flag (BR-DISH-001).
- [x] FR-009: The suggestions response is a plain list, not paginated: a dish's ingredient set is
  bounded by the curation effort, and pagination would add a paging loop to a form-prefill call.
- [x] FR-010: `AdminDishResponse` gains `suggestedIngredients`: the same shape as FR-002, ordered
  by `required DESC, name ASC` so mandatory entries read first.
- [x] FR-011: Approving a recipe seeds `dish_ingredient` from its `recipe_ingredient` rows: one
  entry per ingredient, `required = false`, for that recipe's dish. Existing entries are left
  untouched — neither `required` nor `removed_at` changes.
- [x] FR-012: Seeding skips an ingredient whose `dish_ingredient` row exists but is soft-deleted.
  An admin's deletion is permanent for the auto-add path.
- [x] FR-013: Seeding runs from **both** recipe-approval entry points — `AdminRecipeService`
  approving a recipe directly, and `AdminPostService` approving a post that references recipes —
  through one shared method, so the two cannot diverge.
- [x] FR-014: `RecipeResponse` gains `missingRequiredIngredients: List<String>` — the names of
  the dish's active `required` ingredients absent from this recipe's ingredient set. Empty list
  when nothing is missing. Computed on read; never stored.
- [x] FR-015: `AdminRecipeListResponse` gains the same `missingRequiredIngredients` field, so the
  review queue can show the warning alongside the existing `score` / `moderationStatus` columns.
- [x] FR-016: `missingRequiredIngredients` is advisory only: it never changes `moderationStatus`,
  never blocks create/approve/reject, and never appears in an error response.
- [x] FR-017: An ingredient added to the dish by an admin after a recipe was created appears in
  that recipe's `missingRequiredIngredients` on the next read. No migration or re-scan required —
  the value is derived.
- [x] FR-018: Migration is additive and reversible in the Flyway sense: one new table, no column
  dropped or altered on `dish`, `ingredient`, `recipe` or `recipe_ingredient`.
- [x] FR-019: Approving a recipe also approves every `PENDING` dish name and `PENDING` ingredient
  name that recipe references, in the same transaction. Names already `APPROVED` are untouched;
  names `REJECTED` are **left rejected** — rejection stays permanent (BR-DISH-001) and the
  explicit per-name approve endpoint remains the only way back.
- [x] FR-020: Each name approved by FR-019 writes its own `APPROVE_COMPONENT` `moderation_log` row
  naming the dish or ingredient as the target, alongside the recipe's own row.
- [x] FR-021: Rejecting a recipe does **not** change the review state of its dish or ingredient
  names, including ones still `PENDING`. Approval publishes names; rejection never unpublishes
  them, matching post rejection.

### Non-Functional Requirements

- [x] NFR-SCALE-001: `POST /api/recipes` performs no `dish_ingredient` write, so the member write
  path gains no extra query. Seeding happens once per approved recipe.
- [x] NFR-SEC-001: Admin endpoints require the `ADMIN` role via `@PreAuthorize` (ADR-009); the
  suggestions endpoint requires only `isAuthenticated()`, matching `DishController`.
- [x] NFR-SEC-002: The suggestions endpoint never exposes a `PENDING` or `REJECTED` ingredient
  name, so it cannot be used to enumerate unreviewed names (BR-DISH-001).
- [x] NFR-MAINT-001: `DishIngredient` is a JPA entity in `model/recipe/`; reads go through
  `repository/recipe/DishIngredientRepository`; business logic stays in `service/recipe/` and
  `service/admin/`. No layer inversion against ADR-018's ranking.
- [x] NFR-MAINT-002: One seeding method, called by both approval paths, so post approval and
  recipe approval cannot produce different `dish_ingredient` state.
- [x] NFR-PERF-001: Suggestion lookup is one indexed query on `dish_id` with a join to
  `ingredient` for the name and review state; the derived missing-ingredient check is one
  additional indexed query per recipe read, reusing the ingredient ids the response already
  carries instead of re-reading `recipe_ingredient`.

## Design overview

**Data.** New join table `dish_ingredient`, PK `(dish_id, ingredient_id)`, indexed on
`dish_id`. `removed_at` is a tombstone for the auto-add suppression rule (FR-012); there is no
`origin` column — who introduced an entry is not needed, only whether it is currently offered and
whether an admin has deliberately withdrawn it.

**Writes.** Two sources only.

1. Admin CRUD under `/api/admin/dishes/{dishId}/ingredients` — create, toggle `required`, soft
   delete. Admin-created entries may be `required` on arrival.
2. Recipe approval — `AdminRecipeService.approveRecipe` and the recipe loop inside
   `AdminPostService.approveComponents` both call one seeding method, which upserts
   `required = false` entries for ingredients that have no row at all.

**Reads.** Member: `GET /api/dishes/{dishId}/ingredients/suggestions` on `DishController`.
Admin: `GET /api/admin/dishes/{dishId}/ingredients` plus the embedded `suggestedIngredients` on
`AdminDishResponse`.

**Warning flag.** `missingRequiredIngredients` is computed at mapping time from the dish's active
`required` rows minus the recipe's own ingredient ids. It is exposed on `RecipeResponse` (so the
author sees it immediately after create) and on `AdminRecipeListResponse` (so the reviewer sees
it in the queue). It is not persisted and not part of any request DTO.

**Moderation interaction.** Recipe approval now approves the recipe's `PENDING` dish and
ingredient names too (FR-019), so a recipe approved from the recipe queue contributes suggestions
that are visible to members immediately — the earlier asymmetry where seeded entries stayed hidden
until each name was approved separately is gone. `REJECTED` names are still left alone. Approving
a *post* reaches the same end state by the same rule, so both paths agree. BR-DISH-001 carries the
updated state table.

**No ADR.** ADR-016 already fixes the governing principle (moderation state lives on the name row;
derived detail is computed at read time, never stored). Dropping `origin` removes the only field
that would have introduced a new architectural axis. This feature adds a table and two API
surfaces inside the existing model.

## Success metrics

- Share of new recipes whose ingredient set includes at least one dish suggestion, measured over
  the 30 days after launch (target: > 40%).
- Count of `recipe` rows reaching `APPROVED` with a non-empty `missingRequiredIngredients` at
  approval time (baseline to be recorded at launch; the metric's purpose is to show whether the
  warning is being seen, not to drive it to zero — a zero would also be what "admins stopped
  marking anything required" looks like).
- Number of `dish_ingredient` entries created by seeding versus by admin action, per month, to
  confirm the auto-add path is doing the curation work it was added for.

## Acceptance criteria

**As a** member writing a recipe, **I want to** see which ingredients normally belong to the dish
I picked, **so that** I don't submit a dish that is missing the thing that defines it.

- [x] Given an approved dish "spaghetti" with a `required` entry "spaghetti", when a member calls
  `GET /api/dishes/{dishId}/ingredients/suggestions`, then 200 returns that entry with
  `required: true`.
- [x] Given an admin marks "tomato" as `required` for a dish, when a member creates a recipe for
  that dish without tomato, then the recipe is still created (`201`) and its response carries
  `missingRequiredIngredients: ["tomato"]`.
- [x] Given the same recipe is listed in the admin review queue, when an admin reads
  `GET /api/admin/recipes`, then the item carries the same `missingRequiredIngredients` list.
- [x] Given a recipe containing "noodle" is approved directly from the recipe queue, when the
  member then reads that dish's suggestions, then "noodle" appears with `required: false` and the
  ingredient name is `APPROVED` — recipe approval approved it.
- [x] Given a recipe whose dish name is still `PENDING`, when an admin approves that recipe, then
  the dish name becomes `APPROVED` and an `APPROVE_COMPONENT` log row targets the dish.
- [x] Given a recipe references a `REJECTED` ingredient name, when an admin approves the recipe,
  then the recipe is approved but that ingredient stays `REJECTED`.
- [x] Given a recipe references a `PENDING` ingredient name, when an admin rejects the recipe, then
  the ingredient stays `PENDING` — rejection does not cascade.
- [x] Given an admin soft-deletes "noodle" from a dish, when another recipe for that dish
  containing "noodle" is approved, then "noodle" does not reappear in the suggestion list.
- [x] Given a dish with no ingredient entries, when a member reads its suggestions, then 200 with
  an empty list — not 404.
- [x] Given a `PENDING` dish, when a member reads its suggestions, then 404.
- [x] Given a non-admin token, when the admin ingredient endpoints are called, then 403.

## Risks / open questions

- **`required` cannot be enumerated reliably.** Spaghetti is `required` for "spaghetti" but not
  for "spaghetti squash". Zucchini noodles satisfy the intent without the ingredient. The design
  accepts this: the flag is a warning, so a false positive costs a reviewer a glance, not a
  rejected recipe. Keeping it advisory is what makes the rule survivable.
- **Only approved recipes contribute.** Suggestions lag until review. Accepted so the shared list
  cannot be polluted by rejected content; the alternative (seed on create) was explicitly
  rejected during requirement analysis.
- **The member's own recipe does not pre-seed its dish.** A member gets suggestions from prior
  approved recipes only, so the first recipe for a new dish starts empty. This is the cost of the
  approval-gated seeding choice.
- **`removed_at` is permanent for the auto-add path.** An admin who deletes by mistake must
  re-create the entry explicitly (FR-006); nothing restores it automatically. Intentional, but it
  means the admin UI should present delete as "stop suggesting this", not "delete row".
- **`menu_detail` remains unguarded** — a scheduled menu can reference a dish with no recipe and
  therefore no ingredient data. Out of scope here; recorded so it is not mistaken for covered.

## Related

- ADR: `docs/adrs/016-component-level-moderation.md` (moderation state on the name row; derived
  detail computed at read time)
- Business rule: `docs/brs/dish-ingredient-suggestions.md` (BR-DISH-002, BR-DISH-003)
- Business rule: `docs/brs/dish-ingredient-name-approval.md` (BR-DISH-001)
- API: `docs/apis/recipes/get-dishes-dishid-ingredients-suggestions.md`
- API: `docs/apis/admin/dish-ingredient-crud.md`
- Companions: `docs/feats/create-recipe-api.md`, `docs/feats/list-dishes-api.md`
- Data model: `docs/arch/data-dictionary.md` Tables 11–14 (Ingredient, Recipe, Recipe_Ingredient,
  Dish) and Table 25 (Dish_Ingredient)
