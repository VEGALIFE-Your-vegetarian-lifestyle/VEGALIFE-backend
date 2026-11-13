# API Reference: GET /api/recipes

## Overview

Return a paginated catalogue of approved recipes, newest first. Public: guests and members browse the same approved content (BR-RECP-008).

## Endpoint

```text
GET /api/recipes
```

## Authentication

Optional. No JWT is needed; the route is `permitAll`. A supplied but invalid or expired token is still validated and returns `401`.

Every caller gets the same result: `APPROVED`, non-deleted recipes only.

## Request

### Path Parameters

No path parameters.

### Query Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | integer | No | Zero-based page index. Defaults to `0`; must be `>= 0`. |
| size | integer | No | Page size. Defaults to `20`; between `1` and `100`. |

Order is fixed: `created_at DESC`. There is no sort, dish, category, or keyword parameter.

### Request Body

No request body.

## Responses

### Success Response (200 OK)

Standard pagination envelope (`ApiResponse<PageResponse<RecipeResponse>>`), message `Recipes retrieved successfully`:

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
        "instructions": "Press the tofu. Stir-fry with vegetables and sauce.",
        "servings": 2,
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

Each `data.content[]` item is the same `RecipeResponse` shape returned by `GET /api/recipes/me`; see that endpoint for the full field table.

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | `true` when the request succeeds. |
| message | string | `Recipes retrieved successfully`. |
| data.content | array | Approved recipes on this page; empty when none are approved. |
| data.page | integer | Zero-based page index returned. |
| data.size | integer | Page size used. |
| data.totalElements | integer | Total approved, non-deleted recipes. |
| data.totalPages | integer | Total page count. |
| data.first | boolean | Whether this is the first page. |
| data.last | boolean | Whether this is the last page. |

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `page` is negative or `size` is outside `1`–`100` | `Validation failed` |
| 401 | A token is supplied but is invalid, expired, or the account is inactive | `Unauthorized` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- BR-RECP-008: only `moderationStatus = APPROVED`, non-deleted recipes are listed. A `PENDING` or `REJECTED` recipe is visible only to its author through `GET /api/recipes/me` and `GET /api/recipes/{recipeId}`.
- Soft-deleted recipes (`deleted_at IS NOT NULL`) are never returned.
- `rejectionReason` is always `null` here: a non-author list must not reveal an administrative message.
- Ordering is `created_at` descending — newest recipe first.
- BR-RECP-006 / BR-RECP-007 apply: nutrition totals and per-line values are derived, and a recipe reports `nutritionResolved = false` when any ingredient is unresolved.
- Pagination is bounded: page `0`, size `20` by default, `size` 1–100.
- No schema migration is needed; visibility is already stored on the recipe row.

## Example

### Request

```bash
curl "http://localhost:8080/api/recipes?page=0&size=10"
```

### Success Response (200)

```json
{
  "success": true,
  "message": "Recipes retrieved successfully",
  "data": {
    "content": [],
    "page": 0,
    "size": 10,
    "totalElements": 0,
    "totalPages": 0,
    "first": true,
    "last": true
  }
}
```

## Related

- Read one recipe: `docs/apis/recipes/get-recipes-recipeid.md`
- List own recipes: `docs/apis/recipes/get-recipes-me.md`
- Create counterpart: `docs/apis/recipes/post-recipes.md`
- Admin list across users: `docs/apis/admin/get-recipes.md`
- Business rules: `docs/brs/recipes.md` (BR-RECP-008)
- Feature spec: `docs/feats/public-recipe-browsing.md`
