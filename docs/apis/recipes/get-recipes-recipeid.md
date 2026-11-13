# API Reference: GET /api/recipes/{recipeId}

## Overview
Read one recipe by id, including the resolved dish, ingredient rows, and both review states. An `APPROVED`, non-deleted recipe is public — anyone, including a guest, can read it. A `PENDING` or `REJECTED` recipe is readable only by its author; for everyone else it is indistinguishable from a missing one (404).

## Endpoint
```
GET /api/recipes/{recipeId}
```

## Authentication
Optional. The route is `permitAll`. A supplied but invalid or expired token is still validated and returns `401`. When a valid token is present, the caller's identity is used only to grant the author access to their own non-approved recipe.

## Request

### Path Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| recipeId | uuid | Yes | Id of the recipe to read — an `id` from `GET /api/recipes` or `GET /api/recipes/me` |

### Query Parameters
No query parameters

### Request Body
No request body

## Responses

### Success Response (200)
```json
{
  "success": true,
  "message": "Recipe retrieved successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "userId": "6c1f1a3e-1f0a-4f6e-9a4a-6d2d5e77aa11",
    "dishId": "9b2d8e11-2c33-4f5a-8f1d-0a1b2c3d4e5f",
    "dishName": "tofu stir-fry",
    "dishModerationStatus": "APPROVED",
    "moderationStatus": "APPROVED",
    "rejectionReason": null,
    "name": "Spicy tofu stir-fry",
    "description": "A quick weeknight dinner.",
    "instructions": "Press the tofu. Stir-fry with vegetables and sauce.",
    "prepTimeMinutes": 10,
    "cookTimeMinutes": 15,
    "servings": 2,
    "difficulty": "EASY",
    "ingredients": [
      {
        "ingredientId": "1a2b3c4d-0001-4000-8000-000000000001",
        "name": "tofu",
        "amount": 200,
        "unit": "G",
        "calories": 288.00,
        "proteinG": 34.38,
        "carbohydrateG": 5.56,
        "fatG": 17.44,
        "fiberG": 4.60,
        "nutritionResolved": true
      }
    ],
    "calories": 288.00,
    "proteinG": 34.38,
    "carbohydrateG": 5.56,
    "fatG": 17.44,
    "fiberG": 4.60,
    "nutritionResolved": true,
    "createdAt": "2026-10-01T10:00:00Z",
    "updatedAt": "2026-10-01T10:00:00Z"
  }
}
```

| Field | Type | Description |
|-------|------|--------------|
| success | boolean | Always true for success |
| message | string | `Recipe retrieved successfully` |
| data | object | The recipe — same shape as one element of `GET /api/recipes/me` |
| data.id | uuid | Recipe id |
| data.userId | uuid | Owner of the recipe; not necessarily the caller |
| data.dishId | uuid | Resolved shared dish row; never null |
| data.dishName | string | Dish name after lowercasing (e.g. request `Tofu Stir-Fry` → `tofu stir-fry`) |
| data.dishModerationStatus | string | Review state of the linked dish name: `PENDING`, `APPROVED`, or `REJECTED` |
| data.moderationStatus | string | Review state of this recipe: `PENDING`, `APPROVED`, or `REJECTED` |
| data.rejectionReason | string or null | Why an admin rejected this recipe. Returned only to the author; always `null` for a non-author or guest read. |
| data.name | string | Recipe name as submitted |
| data.description | string \| null | Optional description |
| data.instructions | string | Cooking instructions |
| data.prepTimeMinutes | integer \| null | Preparation time, if supplied |
| data.cookTimeMinutes | integer \| null | Cooking time, if supplied |
| data.servings | integer | Number of servings |
| data.difficulty | string \| null | `EASY`, `MEDIUM`, `HARD`, or `null` |
| data.ingredients[] | array | One entry per ingredient link, with the resolved `ingredientId` and the lowercased `name` |
| data.ingredients[].ingredientId | uuid | Shared ingredient row |
| data.ingredients[].name | string | Lowercased ingredient name |
| data.ingredients[].amount | number | Quantity |
| data.ingredients[].unit | string \| null | The **ingredient's** unit — `G`, `ML` or `PIECE` — derived, not stored per line. `null` when it has not been decided |
| data.ingredients[].calories / proteinG / carbohydrateG / fatG / fiberG | number \| null | This line's own contribution: `amount × the ingredient's per-unit value`, rounded to 2 decimals. All five are `null` together when the ingredient is uncurated |
| data.ingredients[].nutritionResolved | boolean | `true` when this line's five values are real rather than unknown |
| data.missingRequiredIngredients | array of string | Names of the dish's `required` ingredients this recipe omits (BR-DISH-002). Advisory only — the recipe is valid either way. Empty array when nothing is missing. Derived on read, never stored. |
| data.calories / proteinG / carbohydrateG / fatG / fiberG | number \| null | Computed totals for the whole recipe, never per serving. `null` means the recipe cannot be priced yet, distinct from `0` (BR-RECP-006) |
| data.nutritionResolved | boolean | `true` only when every ingredient is curated and the totals are real |
| data.createdAt / data.updatedAt | string | ISO-8601 instants |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `recipeId` is not a valid UUID | "Validation failed" |
| 401 | A token is supplied but is invalid, expired, or the account is inactive | "Unauthorized" |
| 404 | Recipe does not exist, is soft-deleted, or is not `APPROVED` and the caller is not its author | "Recipe not found" |
| 500 | Server error | "Internal server error" |

## Business Rules
- BR-RECP-008: visibility is decided by moderation state plus authorship, not by who is asking. A recipe with `moderationStatus = APPROVED` and `deleted_at IS NULL` resolves for every caller, including a guest. A `PENDING` or `REJECTED` recipe resolves only when the caller is its author.
- An ineligible read is reported as 404, not 403: answering "exists but is not approved" would confirm the id to a non-author.
- Review state never hides the recipe from its own author. `moderationStatus` and `dishModerationStatus` are returned as-is, so an author can still read a `PENDING` recipe or a recipe whose dish name is still awaiting approval — with the state attached — instead of looking inexplicably withheld (BR-DISH-001).
- `rejectionReason` is author-only. It is returned when the caller authored the recipe and cleared to `null` for every other caller, because this payload is now also a public read.
- Nutrition is derived from the recipe's ingredients and stored, never authored (BR-RECP-004, ADR-022). `nutritionResolved` distinguishes "all ingredients curated" from "cannot be priced yet"; a `null` total is never a `0` (BR-RECP-006).
- Each line reports its own contribution (`amount × per-unit value`, rounded to 2 decimals) so a member can see which ingredient drives the total. Line values are rounded individually while the total is rounded once over the exact sum, so the visible lines need not add up exactly to the visible total (BR-RECP-007).
- Members cannot edit recipes (`PUT`/`PATCH` are deliberately absent, BR-RECP-001); corrections go through an admin, which is why this is a read-only endpoint.
- The public catalogue lives at `GET /api/recipes`; the author's own list lives at `GET /api/recipes/me`; the admin surface is `GET /api/admin/recipes`.

## Example

### Request
```bash
curl -X GET "http://localhost:8080/api/recipes/550e8400-e29b-41d4-a716-446655440000"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Recipe retrieved successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "userId": "6c1f1a3e-1f0a-4f6e-9a4a-6d2d5e77aa11",
    "dishId": "9b2d8e11-2c33-4f5a-8f1d-0a1b2c3d4e5f",
    "dishName": "tofu stir-fry",
    "dishModerationStatus": "APPROVED",
    "moderationStatus": "APPROVED",
    "name": "Spicy tofu stir-fry",
    "description": null,
    "instructions": "Press the tofu. Stir-fry with vegetables and sauce.",
    "prepTimeMinutes": null,
    "cookTimeMinutes": null,
    "servings": 2,
    "difficulty": null,
    "ingredients": [],
    "calories": null,
    "proteinG": null,
    "carbohydrateG": null,
    "fatG": null,
    "fiberG": null,
    "nutritionResolved": false,
    "createdAt": "2026-10-01T10:00:00Z",
    "updatedAt": "2026-10-01T10:00:00Z"
  }
}
```

### Error Response (404)
```json
{
  "success": false,
  "message": "Recipe not found",
  "data": null
}
```

## Related
- List approved recipes (public): `docs/apis/recipes/get-recipes.md`
- List own recipes: `docs/apis/recipes/get-recipes-me.md`
- Create counterpart: `docs/apis/recipes/post-recipes.md`
- Delete counterpart: `docs/apis/recipes/delete-recipes-recipeid.md`
- Admin counterpart: `docs/apis/admin/recipe-crud.md`
- Business rules: `docs/brs/recipes.md` (BR-RECP-001, BR-RECP-004, BR-RECP-006, BR-RECP-008, BR-RECP-010, BR-DISH-001)
- Feature spec: `docs/feats/public-recipe-browsing.md`, `docs/feats/ingredient-nutrition.md`
- ADR: [`docs/adrs/022-ingredient-level-nutrition.md`](../../adrs/022-ingredient-level-nutrition.md)
