# API Reference: GET /api/recipes/me

## Overview
Return a paginated list of every non-deleted recipe owned by the authenticated member, newest first, with optional filters by review state, dish, difficulty, and total time.

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

| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | integer | No | Zero-based page index. Defaults to `0`; must be `>= 0`. |
| size | integer | No | Page size. Defaults to `20`; between `1` and `100`. |
| moderationStatus | string | No | `PENDING`, `APPROVED`, or `REJECTED`. Absent means every state. Any other value is `400 Validation failed`. |
| dishId | UUID | No | Only recipes using that dish. |
| difficulty | string | No | `EASY`, `MEDIUM`, or `HARD`. Any other value is `400 Validation failed`. |
| maxTotalTimeMinutes | integer | No | Only recipes whose `prepTimeMinutes + cookTimeMinutes` is at most this value. A recipe missing either time does not match. |

Order is fixed: `created_at DESC`. There is no `keyword` parameter; search the public catalogue with `GET /api/recipes`. Filters combine with AND and only narrow the caller's own list.

### Request Body
No request body

## Responses

### Success Response (200)
```json
{
  "success": true,
  "message": "Recipes retrieved successfully",
  "data": {
    "content": [
      {
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
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

| Field | Type | Description |
|-------|------|--------------|
| success | boolean | Always true for success |
| message | string | `Recipes retrieved successfully` |
| data.content | array | The caller's recipes on this page, newest `createdAt` first; empty array when there are none |
| data.content[].id | uuid | Recipe id — usable with `GET /api/recipes/{recipeId}` |
| data.content[].userId | uuid | Owner, always the authenticated caller |
| data.content[].dishId | uuid | Resolved shared dish row; never null |
| data.content[].dishName | string | Dish name after lowercasing (e.g. request `Tofu Stir-Fry` → `tofu stir-fry`) |
| data.content[].dishModerationStatus | string | Review state of the linked dish name: `PENDING`, `APPROVED`, or `REJECTED` |
| data.content[].moderationStatus | string | Review state of this recipe: `PENDING`, `APPROVED`, or `REJECTED` |
| data.content[].rejectionReason | string or null | Why an admin rejected this recipe. Always populated for the author when a reason exists; `null` otherwise. |
| data.content[].name | string | Recipe name as submitted |
| data.content[].description | string \| null | Optional description |
| data.content[].instructions | string | Cooking instructions |
| data.content[].prepTimeMinutes | integer \| null | Preparation time, if supplied |
| data.content[].cookTimeMinutes | integer \| null | Cooking time, if supplied |
| data.content[].servings | integer | Number of servings |
| data.content[].difficulty | string \| null | `EASY`, `MEDIUM`, `HARD`, or `null` |
| data.content[].ingredients | array | One entry per ingredient link, with the resolved `ingredientId` and the lowercased `name` |
| data.content[].ingredients[].ingredientId | uuid | Shared ingredient row |
| data.content[].ingredients[].name | string | Lowercased ingredient name |
| data.content[].ingredients[].amount | number | Quantity |
| data.content[].ingredients[].unit | string \| null | The **ingredient's** unit — `G`, `ML` or `PIECE` — derived, not stored per line. `null` when it has not been decided |
| data.content[].ingredients[].calories / proteinG / carbohydrateG / fatG / fiberG | number \| null | This line's own contribution: `amount × the ingredient's per-unit value`, rounded to 2 decimals. All five are `null` together when the ingredient is uncurated |
| data.content[].ingredients[].nutritionResolved | boolean | `true` when this line's five values are real rather than unknown |
| data.content[].missingRequiredIngredients | array of string | Names of the dish's `required` ingredients this recipe omits (BR-DISH-002). Advisory only — the recipe is valid either way. Empty array when nothing is missing. Derived on read, never stored. |
| data.content[].calories / proteinG / carbohydrateG / fatG / fiberG | number \| null | Computed totals for the whole recipe, never per serving. `null` means the recipe cannot be priced yet, distinct from `0` (BR-RECP-006) |
| data.content[].nutritionResolved | boolean | `true` only when every ingredient is curated and the totals are real |
| data.content[].createdAt / data.content[].updatedAt | string | ISO-8601 instants |
| data.page | integer | Zero-based page index returned |
| data.size | integer | Page size used |
| data.totalElements | integer | Total matching recipes |
| data.totalPages | integer | Total page count |
| data.first | boolean | Whether this is the first page |
| data.last | boolean | Whether this is the last page |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `page`/`size` out of range, an invalid `moderationStatus` or `difficulty`, or an unparseable `dishId` | "Validation failed" |
| 401 | Missing, invalid, expired JWT, or the account is inactive | "Unauthorized" |
| 500 | Server error | "Internal server error" |

## Business Rules
- The owner is always the user identified by the authenticated JWT; the endpoint takes no `userId` parameter (BR-RECP-001).
- Soft-deleted recipes (`deleted_at IS NOT NULL`) are never included.
- Ordering is `created_at DESC` — newest recipe first.
- No moderation filter is applied unless `moderationStatus` is supplied: recipes whose dish name is still `PENDING`, or that are themselves unapproved, stay in the list with their state attached, so a withheld recipe is not a mystery to its author (BR-DISH-001).
- `moderationStatus`, `dishId`, `difficulty`, and `maxTotalTimeMinutes` only narrow the caller's own list; they never widen visibility.
- Pagination is bounded: page `0`, size `20` by default, `size` 1–100. This response is a `PageResponse`, like `GET /api/posts/me`; it changed from a bare array in the list-filters release.
- Nutrition is derived from the recipe's ingredients and stored, never authored (BR-RECP-004, ADR-022). `nutritionResolved` distinguishes "all ingredients curated" from "cannot be priced yet"; a `null` total is never a `0`.
- Each line reports its own contribution (`amount × per-unit value`, rounded to 2 decimals) so a member can see which ingredient drives the total. Line values are rounded individually while the total is rounded once over the exact sum, so the visible lines need not add up exactly to the visible total (BR-RECP-007).
- Read-only: the endpoint never creates or modifies a recipe.

## Example

### Request
```bash
curl -X GET "http://localhost:8080/api/recipes/me?page=0&size=20&moderationStatus=REJECTED" \
  -H "Authorization: Bearer <access-token>"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Recipes retrieved successfully",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "first": true,
    "last": true
  }
}
```

## Related
- Create counterpart: `docs/apis/recipes/post-recipes.md`
- Read one: `docs/apis/recipes/get-recipes-recipeid.md`
- Public catalogue: `docs/apis/recipes/get-recipes.md`
- Admin list across users: `docs/apis/admin/get-recipes.md`
- Business rules: `docs/brs/recipes.md` (BR-RECP-001, BR-RECP-004, BR-RECP-006, BR-DISH-001)
- Feature specs: `docs/feats/ingredient-nutrition.md`, `docs/feats/post-recipe-list-filters.md`
- ADR: [`docs/adrs/022-ingredient-level-nutrition.md`](../../adrs/022-ingredient-level-nutrition.md)
