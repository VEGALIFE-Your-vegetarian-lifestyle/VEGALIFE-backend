# API Reference: Admin Management of Dish Ingredients

## Overview

Curated association between a dish and the ingredients that normally belong to it. An
administrator adds entries, marks each one `required` or leaves it advisory, and withdraws entries
that should stop being suggested. Members read the result through
`GET /api/dishes/{dishId}/ingredients/suggestions`; they never write here.

| Endpoint | Purpose |
|---|---|
| `GET /api/admin/dishes/{dishId}/ingredients` | List the dish's active entries |
| `POST /api/admin/dishes/{dishId}/ingredients` | Add an entry, with `required` |
| `PATCH /api/admin/dishes/{dishId}/ingredients/{ingredientId}` | Flip `required` |
| `DELETE /api/admin/dishes/{dishId}/ingredients/{ingredientId}` | Withdraw the entry (soft delete) |

There is no `PUT`: the update body is partial, and calling a partial update `PUT` would tell
clients they can omit fields and replace the rest.

## Authentication

All require a JWT access token with the `ADMIN` role (`@PreAuthorize`) — see
[ADR-009](../../adrs/009-authorize-endpoints-with-method-security.md). Other roles get `403`.

## GET /api/admin/dishes/{dishId}/ingredients

No query parameters. Returns every entry with `removed_at IS NULL`, ordered
`required DESC, name ASC`, including entries whose ingredient is still `PENDING` or `REJECTED` —
an admin curating the list must be able to see that a name is unrunnable before it is approved.

### Response (200 OK)

```json
{
  "success": true,
  "message": "Dish ingredients retrieved successfully",
  "data": [
    {
      "ingredientId": "3f9a1c22-7d4e-4a80-9b31-2c6d5e8f0a11",
      "name": "spaghetti",
      "moderationStatus": "APPROVED",
      "required": true,
      "createdAt": "2026-11-02T08:00:00Z",
      "updatedAt": "2026-11-02T08:00:00Z"
    },
    {
      "ingredientId": "8b2c4d66-1e3f-4a55-8c07-5d9e2b1a7f34",
      "name": "tomato",
      "moderationStatus": "PENDING",
      "required": false,
      "createdAt": "2026-11-02T08:05:00Z",
      "updatedAt": "2026-11-02T08:05:00Z"
    }
  ]
}
```

The same list is embedded on `AdminDishResponse` as `suggestedIngredients`, so the dish detail
screen does not need a second call.

## POST /api/admin/dishes/{dishId}/ingredients

```json
{ "ingredientName": "spaghetti", "required": true }
```

| Field | Required | Notes |
|---|---|---|
| ingredientName | Yes | Trimmed and lowercased before lookup. Max 100 chars (the `ingredient.name` limit). |
| required | No | Defaults to `false`. `true` marks the ingredient as normally mandatory for this dish. |

The name is resolved find-or-create exactly as `POST /api/recipes` resolves ingredient names
(BR-RECP-002): an existing row is reused, a new one is created `APPROVED` — the admin introducing
it is the approver, the same rule as `POST /api/admin/dishes`. An existing row keeps whatever state
it already has, so reusing a `PENDING` name does not quietly approve it.

Re-posting an entry that was previously withdrawn clears `removed_at` and applies the supplied
`required`. This is the only way back from a delete — the automatic seeding path will not restore
it.

### Responses

| Status | Condition |
|---|---|
| 201 | Created |
| 200 | Entry existed but was withdrawn; it was reinstated |
| 400 | Blank name, name over 100 chars, or the ingredient name is `REJECTED` (BR-DISH-001) |
| 404 | Dish missing or soft-deleted |

Re-posting an entry that is **already active** is a `409 Conflict`, mirroring the admin dish and
ingredient CRUD: an accidental double submit should not silently overwrite an existing `required`
decision.

## PATCH /api/admin/dishes/{dishId}/ingredients/{ingredientId}

```json
{ "required": false }
```

Partial update. Only `required` is accepted — the ingredient identity cannot be changed; changing
which ingredient an entry points at is a delete plus a create, so history stays legible.

| Status | Condition |
|---|---|
| 200 | Updated |
| 400 | Body has no `required` field |
| 404 | Dish or entry missing, or the entry is already withdrawn |

## DELETE /api/admin/dishes/{dishId}/ingredients/{ingredientId}

Soft delete: sets `removed_at`. The row is retained as a tombstone, because the auto-seed path
must be able to tell "never known" from "an admin removed this" — otherwise the next recipe
containing that ingredient would silently restore it.

The member-facing suggestion list stops returning the entry immediately. Existing recipes are
untouched: nothing is removed from `recipe_ingredient`, and no recipe's
`missingRequiredIngredients` is retroactively satisfied or invalidated.

| Status | Condition |
|---|---|
| 200 | Withdrawn, or already withdrawn (idempotent: no second write) |
| 404 | Dish or entry missing |

## Business Rules

- **`required` is advisory.** An admin marking an ingredient required never blocks a member from
  creating or an admin from approving a recipe that omits it. The omission surfaces as
  `missingRequiredIngredients` on `RecipeResponse` and `AdminRecipeListResponse` instead
  (BR-DISH-002).
- **Approving a recipe seeds this table.** One entry per recipe ingredient, `required: false`,
  skipping ingredients whose entry exists at all — including withdrawn ones (BR-DISH-003).
- **Deletion is permanent for the automatic path.** Seeding never reinstates a withdrawn entry.
- **Name visibility is unchanged.** An admin-created entry may reference a `PENDING` ingredient; it
  stays hidden from the member-facing list until the name itself is approved (BR-DISH-001).

## Related

- Feature spec: `docs/feats/dish-ingredient-suggestions.md`
- Business rules: `docs/brs/dish-ingredient-suggestions.md`
- Member read: `docs/apis/recipes/get-dishes-dishid-ingredients-suggestions.md`
- Sibling admin CRUD: `docs/apis/admin/dish-and-ingredient-crud.md`
- ADR: `docs/adrs/016-component-level-moderation.md`
