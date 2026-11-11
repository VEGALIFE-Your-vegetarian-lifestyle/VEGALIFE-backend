# API Reference: GET /api/saved/recipes

## Overview

Lists the caller's saved recipes, newest save first, including only recipes that are still
`APPROVED` (BR-SAVED-002).

## Endpoint

```text
GET /api/saved/recipes
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`). The list always
belongs to the caller; no member id is accepted.

## Request

### Query Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | integer | No | Zero-based page index. Defaults to `0`. Must be `>= 0`. |
| size | integer | No | Page size. Defaults to `20`. Must be between `1` and `100`. |

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Saved recipes retrieved successfully",
  "data": {
    "content": [
      {
        "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
        "userId": "6f1c2b1a-9a1e-4f5a-8f3e-2b4c9d8e7f10",
        "dishId": "3b1f5c8e-2a4d-4c6b-9f7e-1d2c3b4a5e60",
        "dishName": "phở chay",
        "dishModerationStatus": "APPROVED",
        "moderationStatus": "APPROVED",
        "rejectionReason": null,
        "name": "Phở chay",
        "description": "A vegan take on the classic broth.",
        "instructions": "Simmer the broth for two hours...",
        "prepTimeMinutes": 20,
        "cookTimeMinutes": 120,
        "servings": 4,
        "difficulty": "MEDIUM",
        "ingredients": [],
        "missingRequiredIngredients": [],
        "calories": 420.00,
        "proteinG": 18.50,
        "carbohydrateG": 62.00,
        "fatG": 9.25,
        "fiberG": 6.00,
        "nutritionResolved": true,
        "createdAt": "2026-10-20T11:00:00Z",
        "updatedAt": "2026-10-21T08:15:00Z"
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
|-------|------|-------------|
| content | array of `RecipeResponse` | Saved recipes in save order, newest first. Same shape as `GET /api/recipes/me`. |
| totalElements | long | Number of saved recipes that are still approved — not the number ever saved. |

`rejectionReason` is always null here: only `APPROVED` recipes are returned, and approval clears it.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `page` is negative or `size` is outside 1–100 | `Validation failed` |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- The list is owner-scoped and private (`BR-SAVED-001`).
- A saved recipe that is no longer `APPROVED`, or is soft-deleted, is omitted without its save being
  deleted, so it returns if the recipe is approved again (`BR-SAVED-002`).
- Paging is bounded: default 20, maximum 100.
- A recipe authored by the caller appears here exactly like anyone else's; the saved list is not an
  authorship view.

## Example

```bash
curl "http://localhost:8080/api/saved/recipes?page=0&size=20" \
  -H "Authorization: Bearer <access-token>"
```

## Related

- Save a recipe: `docs/apis/saved/post-saved-recipes-recipeid.md`
- Unsave a recipe: `docs/apis/saved/delete-saved-recipes-recipeid.md`
- List saved posts: `docs/apis/saved/get-saved-posts.md`
- Business rules: `docs/brs/saved-items.md`
