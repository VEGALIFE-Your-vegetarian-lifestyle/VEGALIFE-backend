# API Reference: DELETE /api/saved/recipes/{recipeId}

## Overview

Removes a recipe from the caller's saved list. A recipe that is not saved is a successful no-op
(BR-SAVED-001).

## Endpoint

```text
DELETE /api/saved/recipes/{recipeId}
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`).

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| recipeId | UUID | Yes | ID of the recipe to unsave. |

### Request Body

No request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Recipe removed from saved items",
  "data": null
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | Human-readable confirmation |
| data | null | The action returns no payload |

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Unsaving is idempotent and never fails because the row is already gone (`BR-SAVED-001`).
- Unsave is not scoped to the recipe's review state: a member can remove a save for a recipe that has
  since been demoted from `APPROVED`, even though it no longer appears in the list.
- The recipe itself is untouched; only the caller's `saved_item` row is deleted.

## Example

```bash
curl -X DELETE http://localhost:8080/api/saved/recipes/7c9e6679-7425-40de-944b-e07fc1f90ae7 \
  -H "Authorization: Bearer <access-token>"
```

## Related

- Save a recipe: `docs/apis/saved/post-saved-recipes-recipeid.md`
- List saved recipes: `docs/apis/saved/get-saved-recipes.md`
- Business rules: `docs/brs/saved-items.md`
