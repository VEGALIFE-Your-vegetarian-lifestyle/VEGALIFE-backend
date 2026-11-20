# API Reference: POST /api/saved/recipes/{recipeId}

## Overview

Saves an approved recipe to the caller's private saved list. Repeat calls succeed and keep the
original save time (BR-SAVED-001).

## Endpoint

```text
POST /api/saved/recipes/{recipeId}
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`).

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| recipeId | UUID | Yes | ID of the recipe to save. It must be `APPROVED` and not soft-deleted. |

### Request Body

No request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Recipe saved successfully",
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
| 404 | The recipe does not exist, is soft-deleted, or is not `APPROVED` | `Recipe not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- A saved item is private to the member who saved it, and saving is idempotent (`BR-SAVED-001`).
- Only an approved, non-deleted recipe can be saved (`BR-SAVED-002`). A `PENDING` or `REJECTED`
  recipe is reported as not found, so the saved list is never a way to read an unreviewed body.
- The author may save their own recipe; the rule is about the recipe's review state, not ownership.

## Example

```bash
curl -X POST http://localhost:8080/api/saved/recipes/7c9e6679-7425-40de-944b-e07fc1f90ae7 \
  -H "Authorization: Bearer <access-token>"
```

```json
{
  "success": true,
  "message": "Recipe saved successfully",
  "data": null
}
```

## Related

- List saved recipes: `docs/apis/saved/get-saved-recipes.md`
- Unsave a recipe: `docs/apis/saved/delete-saved-recipes-recipeid.md`
- Business rules: `docs/brs/saved-items.md`
