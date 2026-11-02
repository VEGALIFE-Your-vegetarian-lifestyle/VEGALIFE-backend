# API Reference: GET /api/dishes/{dishId}/ingredients/suggestions

## Overview

Returns the curated ingredient list for one dish, so the create-recipe form can prefill or prompt
the ingredients that normally belong to that dish. Requires authentication.

## Endpoint

```
GET /api/dishes/{dishId}/ingredients/suggestions
```

## Authentication

JWT Bearer access token, any authenticated role (`@PreAuthorize("isAuthenticated()")`). Missing or
invalid token → `401`. No `ADMIN` role is needed — this is member-facing reading.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| dishId | UUID | Yes | Id of the dish, as returned by `GET /api/dishes` |

### Query Parameters

None.

### Request Body

No request body.

## Responses

### Success Response (200)

```json
{
  "success": true,
  "message": "Dish ingredient suggestions retrieved successfully",
  "data": [
    {
      "ingredientId": "3f9a1c22-7d4e-4a80-9b31-2c6d5e8f0a11",
      "name": "spaghetti",
      "required": true
    },
    {
      "ingredientId": "8b2c4d66-1e3f-4a55-8c07-5d9e2b1a7f34",
      "name": "tomato",
      "required": false
    }
  ]
}
```

`data` is a plain array, not a paged response: a dish's ingredient set is bounded by curation
effort, and a form-prefill call should not need a paging loop.

| Field | Type | Description |
|-------|------|-------------|
| ingredientId | UUID | The shared `ingredient` row id, usable in a later `POST /api/recipes` name lookup |
| name | string | Normalized (trimmed, lowercased) ingredient name |
| required | boolean | `true` when an administrator marked this ingredient as normally mandatory for the dish |

Ordering is `required DESC, name ASC` — mandatory entries first, then alphabetical.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | Missing or invalid JWT | "Authentication required" |
| 404 | Dish missing, soft-deleted, or not `APPROVED` | "Dish not found" |
| 500 | Server error | "Internal server error" |

## Business Rules

- Only an `APPROVED`, non-soft-deleted dish resolves. `PENDING` and `REJECTED` dishes answer
  `404`, not a flagged entity: the endpoint is member-facing, and "exists but you may not see it"
  would still confirm the name (BR-DISH-001).
- Only entries whose `ingredient.moderationStatus = APPROVED` are returned. A `PENDING` or
  `REJECTED` ingredient is omitted rather than returned with a state field — same reasoning, and
  it prevents the endpoint being used to enumerate unreviewed names (BR-DISH-002).
- Soft-deleted `dish_ingredient` rows (`removed_at IS NOT NULL`) are excluded. An admin withdrawing
  an entry removes it from this list permanently, including from the automatic re-add path
  (BR-DISH-003).
- `required` is advisory. This endpoint reports the flag; it does not cause recipe creation to fail
  when the ingredient is absent.
- Read-only: the endpoint performs no writes and never creates a `dish_ingredient` row.

## Example

### Request

```bash
curl -X GET "https://api.vegalife.app/api/dishes/1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d/ingredients/suggestions" \
  -H "Authorization: Bearer <access_token>"
```

### Success Response (200)

```json
{
  "success": true,
  "message": "Dish ingredient suggestions retrieved successfully",
  "data": [
    { "ingredientId": "3f9a1c22-7d4e-4a80-9b31-2c6d5e8f0a11", "name": "spaghetti", "required": true },
    { "ingredientId": "de7f2a19-4c3b-4f88-a1d2-6b9c0e5f7a23", "name": "olive oil", "required": false },
    { "ingredientId": "8b2c4d66-1e3f-4a55-8c07-5d9e2b1a7f34", "name": "tomato", "required": false }
  ]
}
```

### Not Found (404)

```json
{
  "success": false,
  "message": "Dish not found",
  "data": null
}
```

---

## Related

- Feature Spec: `docs/feats/dish-ingredient-suggestions.md`
- ADR: `docs/adrs/016-component-level-moderation.md`
- Business Rules: `docs/brs/dish-ingredient-suggestions.md`
- Sibling read: `docs/apis/recipes/get-dishes-dishid.md`
- Consumer: `docs/apis/recipes/post-recipes.md` (`POST /api/recipes` — its `ingredients` names match
  these `name` values)
- Admin side: `docs/apis/admin/dish-ingredient-crud.md`
