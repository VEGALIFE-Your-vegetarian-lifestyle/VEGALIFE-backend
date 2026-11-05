# API Reference: GET /api/recipes/me

## Overview
List every non-deleted recipe owned by the authenticated member, newest first. Returns the caller's whole recipe list in one response — no pagination envelope, no filters.

## Endpoint
```
GET /api/recipes/me
```

## Authentication
JWT Bearer required (`@PreAuthorize("isAuthenticated()")` on `RecipeController`). The owner is taken from the token; the request cannot select another member's recipes.

## Request

### Path Parameters
No path parameters. `me` is a literal segment, so it is never read as a recipe id.

### Query Parameters
No query parameters

### Request Body
No request body

## Responses

### Success Response (200)
```json
{
  "success": true,
  "message": "Recipes retrieved successfully",
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "userId": "6c1f1a3e-1f0a-4f6e-9a4a-6d2d5e77aa11",
      "dishId": "9b2d8e11-2c33-4f5a-8f1d-0a1b2c3d4e5f",
      "dishName": "tofu stir-fry",
      "dishModerationStatus": "APPROVED",
      "moderationStatus": "APPROVED",
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
          "unit": "G"
        },
        {
          "ingredientId": "1a2b3c4d-0002-4000-8000-000000000002",
          "name": "soy sauce",
          "amount": 15,
          "unit": "ML"
        }
      ],
      "calories": 297.00,
      "proteinG": 35.60,
      "carbohydrateG": 6.40,
      "fatG": 17.46,
      "fiberG": 4.72,
      "nutritionResolved": true,
      "createdAt": "2026-10-01T10:00:00Z",
      "updatedAt": "2026-10-01T10:00:00Z"
    }
  ]
}
```

The response has no paging wrapper: `data` is the array itself, and an authenticated member with no recipes gets `"data": []`.

| Field | Type | Description |
|-------|------|--------------|
| success | boolean | Always true for success |
| message | string | `Recipes retrieved successfully` |
| data | array | The caller's recipes, newest `createdAt` first; empty array when there are none |
| data[].id | uuid | Recipe id — usable with `GET /api/recipes/{recipeId}` |
| data[].userId | uuid | Owner, always the authenticated caller |
| data[].dishId | uuid | Resolved shared dish row; never null |
| data[].dishName | string | Dish name after lowercasing (e.g. request `Tofu Stir-Fry` → `tofu stir-fry`) |
| data[].dishModerationStatus | string | Review state of the linked dish name: `PENDING`, `APPROVED`, or `REJECTED` |
| data[].moderationStatus | string | Review state of this recipe: `PENDING`, `APPROVED`, or `REJECTED` |
| data[].name | string | Recipe name as submitted |
| data[].description | string \| null | Optional description |
| data[].instructions | string | Cooking instructions |
| data[].prepTimeMinutes | integer \| null | Preparation time, if supplied |
| data[].cookTimeMinutes | integer \| null | Cooking time, if supplied |
| data[].servings | integer | Number of servings |
| data[].difficulty | string \| null | `EASY`, `MEDIUM`, `HARD`, or `null` |
| data[].ingredients | array | One entry per ingredient link, with the resolved `ingredientId` and the lowercased `name` |
| data[].ingredients[].ingredientId | uuid | Shared ingredient row |
| data[].ingredients[].name | string | Lowercased ingredient name |
| data[].ingredients[].amount | number | Quantity |
| data[].ingredients[].unit | string \| null | The **ingredient's** unit — `G`, `ML` or `PIECE` — derived, not stored per line. `null` when it has not been decided |
| data[].missingRequiredIngredients | array of string | Names of the dish's `required` ingredients this recipe omits (BR-DISH-002). Advisory only — the recipe is valid either way. Empty array when nothing is missing. Derived on read, never stored. |
| data[].calories / proteinG / carbohydrateG / fatG / fiberG | number \| null | Computed totals for the whole recipe, never per serving. `null` means the recipe cannot be priced yet, distinct from `0` (BR-RECP-006) |
| data[].nutritionResolved | boolean | `true` only when every ingredient is curated and the totals are real |
| data[].createdAt / data[].updatedAt | string | ISO-8601 instants |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | Missing, invalid, expired JWT, or the account is inactive | "Unauthorized" |
| 500 | Server error | "Internal server error" |

## Business Rules
- The owner is always the user identified by the authenticated JWT; the endpoint takes no `userId` parameter (BR-RECP-001).
- Soft-deleted recipes (`deleted_at IS NOT NULL`) are never included.
- Ordering is `created_at` descending — newest recipe first (repository method `findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc`).
- No filter is applied on review state: recipes whose dish name is still `PENDING`, or that are themselves unapproved, stay in the list with their state attached, so a withheld recipe is not a mystery to its author (BR-DISH-001).
- Not paginated: there is no `page`/`size` and no total count. A member's whole recipe list is returned in one response, which is why there is no envelope.
- Nutrition is derived from the recipe's ingredients and stored, never authored (BR-RECP-004, ADR-022). `nutritionResolved` distinguishes "all ingredients curated" from "cannot be priced yet"; a `null` total is never a `0`.
- Read-only: the endpoint never creates or modifies a recipe.

## Example

### Request
```bash
curl -X GET "http://localhost:8080/api/recipes/me" \
  -H "Authorization: Bearer <access-token>"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Recipes retrieved successfully",
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "userId": "6c1f1a3e-1f0a-4f6e-9a4a-6d2d5e77aa11",
      "dishId": "9b2d8e11-2c33-4f5a-8f1d-0a1b2c3d4e5f",
      "dishName": "tofu stir-fry",
      "dishModerationStatus": "PENDING",
      "moderationStatus": "PENDING",
      "name": "Spicy tofu stir-fry",
      "description": null,
      "instructions": "Press the tofu. Stir-fry with vegetables and sauce.",
      "prepTimeMinutes": null,
      "cookTimeMinutes": null,
      "servings": 2,
      "difficulty": null,
      "ingredients": [
        {
          "ingredientId": "1a2b3c4d-0001-4000-8000-000000000001",
          "name": "tofu",
          "amount": 200,
          "unit": "G"
        }
      ],
      "calories": null,
      "proteinG": null,
      "carbohydrateG": null,
      "fatG": null,
      "fiberG": null,
      "nutritionResolved": false,
      "createdAt": "2026-10-01T10:00:00Z",
      "updatedAt": "2026-10-01T10:00:00Z"
    }
  ]
}
```

## Related
- Create counterpart: `docs/apis/recipes/post-recipes.md`
- Read one: `docs/apis/recipes/get-recipes-recipeid.md`
- Admin list across users: `docs/apis/admin/get-recipes.md`
- Business rules: `docs/brs/recipes.md` (BR-RECP-001, BR-RECP-004, BR-RECP-006, BR-DISH-001)
- Feature spec: `docs/feats/ingredient-nutrition.md`
- ADR: [`docs/adrs/022-ingredient-level-nutrition.md`](../../adrs/022-ingredient-level-nutrition.md)
