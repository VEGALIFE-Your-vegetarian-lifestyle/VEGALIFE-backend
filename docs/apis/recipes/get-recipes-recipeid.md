# API Reference: GET /api/recipes/{recipeId}

## Overview
Read one of the authenticated member's own recipes by id, including the resolved dish, ingredient rows, and both review states. A recipe owned by someone else is indistinguishable from a missing one (404).

## Endpoint
```
GET /api/recipes/{recipeId}
```

## Authentication
JWT Bearer required (`@PreAuthorize("isAuthenticated()")` on `RecipeController`). The caller is also the ownership filter: only the owner's own recipe resolves.

## Request

### Path Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| recipeId | uuid | Yes | Id of the recipe to read — an `id` from `GET /api/recipes/me` |

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
    "moderationStatus": "PENDING",
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
| data.userId | uuid | Owner, always the authenticated caller |
| data.dishId | uuid | Resolved shared dish row; never null |
| data.dishName | string | Dish name after lowercasing (e.g. request `Tofu Stir-Fry` → `tofu stir-fry`) |
| data.dishModerationStatus | string | Review state of the linked dish name: `PENDING`, `APPROVED`, or `REJECTED` |
| data.moderationStatus | string | Review state of this recipe: `PENDING`, `APPROVED`, or `REJECTED` |
| data.rejectionReason | string or null | Why an admin rejected this recipe. `null` when no reason was recorded (never rejected, or rejected without one). |
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
| 401 | Missing, invalid, expired JWT, or the account is inactive | "Unauthorized" |
| 404 | Recipe does not exist, is soft-deleted, or belongs to another member | "Recipe not found" |
| 500 | Server error | "Internal server error" |

## Business Rules
- The lookup is `id = :recipeId AND deleted_at IS NULL AND user_id = :caller`. A recipe owned by another member is reported as 404, not 403: answering "exists but is not yours" would confirm the id (BR-RECP-001).
- Review state never hides the recipe from its own author. `moderationStatus` and `dishModerationStatus` are returned as-is, so a `PENDING` recipe or a recipe whose dish name is still awaiting approval is readable — with the state attached — instead of looking inexplicably withheld (BR-DISH-001).
- `rejectionReason` is readable here because every endpoint returning this shape is already scoped to the caller's own recipes, so there is no wider audience to leak it to.
- Nutrition is derived from the recipe's ingredients and stored, never authored (BR-RECP-004, ADR-022). `nutritionResolved` distinguishes "all ingredients curated" from "cannot be priced yet"; a `null` total is never a `0` (BR-RECP-006).
- Each line reports its own contribution (`amount × per-unit value`, rounded to 2 decimals) so a member can see which ingredient drives the total. Line values are rounded individually while the total is rounded once over the exact sum, so the visible lines need not add up exactly to the visible total (BR-RECP-007).
- Members cannot edit recipes (`PUT`/`PATCH` are deliberately absent, BR-RECP-001); corrections go through an admin, which is why this is a read-only endpoint.
- This is the owner's read surface only. Cross-user recipe lists live on `GET /api/recipes/me` (own), `GET /api/admin/recipes` (Admin), and the post surfaces that embed recipes.

## Example

### Request
```bash
curl -X GET "http://localhost:8080/api/recipes/550e8400-e29b-41d4-a716-446655440000" \
  -H "Authorization: Bearer <access-token>"
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
    "moderationStatus": "PENDING",
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
- List own recipes: `docs/apis/recipes/get-recipes-me.md`
- Create counterpart: `docs/apis/recipes/post-recipes.md`
- Admin counterpart: `docs/apis/admin/recipe-crud.md`
- Business rules: `docs/brs/recipes.md` (BR-RECP-001, BR-RECP-004, BR-RECP-006, BR-DISH-001)
- Feature spec: `docs/feats/ingredient-nutrition.md`
- ADR: [`docs/adrs/022-ingredient-level-nutrition.md`](../../adrs/022-ingredient-level-nutrition.md)
