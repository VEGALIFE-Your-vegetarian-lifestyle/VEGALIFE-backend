# API Reference: DELETE /api/recipes/{recipeId}

## Overview

Soft-delete the authenticated member's own recipe. Only the recipe's author may
delete it (BR-RECP-010). Administrators do not get an override on this route —
they use `DELETE /api/admin/recipes/{recipeId}` instead.

## Endpoint

```text
DELETE /api/recipes/{recipeId}
```

## Authentication

Required: a valid JWT access token in the `Authorization` header
(`Bearer <token>`). The recipe's owner is taken from that token; the request
cannot choose or infer another member's recipe.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| recipeId | UUID | Yes | ID of the recipe to delete. |

No query parameters. No request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Recipe deleted successfully",
  "data": null
}
```

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 404 | Recipe does not exist, is already soft-deleted, or belongs to another member | `Recipe not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Deletion is soft: `deleted_at` is set and the row is kept. The recipe stops
  appearing in `GET /api/recipes`, `GET /api/recipes/me`, and saved-recipe
  lists, and `GET /api/recipes/{recipeId}` returns `404` for everyone —
  including its author (BR-RECP-010).
- Owner only. A non-owner gets the same `404 Recipe not found` as a missing id,
  so the route cannot confirm that another member's recipe exists. An
  Administrator on this route is treated as an ordinary caller; the admin
  route is the supported override.
- Deleting a recipe that a published post references is allowed. The post keeps
  resolving the attachment; the recipe simply stops being listed or readable
  on its own. No post is edited.
- A second delete on the same id returns `404` because the row is already
  soft-deleted.
- There is no member-facing restore path. Restoration is an administrative
  operation and is not offered by this endpoint.
- Dish and ingredient rows are not deleted. Only the recipe row is affected.
- No new migration: uses the existing `recipe.deleted_at` column (`V6`).

## Example

```bash
curl -X DELETE http://localhost:8080/api/recipes/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer <access-token>"
```

## Related

- Create counterpart: `docs/apis/recipes/post-recipes.md`
- Read one: `docs/apis/recipes/get-recipes-recipeid.md`
- Own list: `docs/apis/recipes/get-recipes-me.md`
- Admin delete: `docs/apis/admin/recipe-crud.md`
- Business rules: `docs/brs/recipes.md` (BR-RECP-001, BR-RECP-010)
- Shared error/response contract: `docs/apis/error-responses.md`
