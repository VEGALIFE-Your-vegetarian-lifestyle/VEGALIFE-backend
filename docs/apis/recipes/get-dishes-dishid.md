# API Reference: GET /api/dishes/{dishId}

## Overview
Read one approved, non-retired canonical dish by id — the same shared `dish` row that recipe creation resolves a `dishName` to. Requires authentication.

## Endpoint
```
GET /api/dishes/{dishId}
```

## Authentication
JWT Bearer required. The route is not in `SecurityConfig`'s `permitAll` list, so the catch-all rule applies; `DishController` additionally carries `@PreAuthorize("isAuthenticated()")`. Requests without a valid token get 401.

## Request

### Path Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| dishId | uuid | Yes | Id of the dish to read — the `id` returned by `GET /api/dishes` |

### Query Parameters
No query parameters

### Request Body
No request body

## Responses

### Success Response (200)
```json
{
  "success": true,
  "message": "Dish retrieved successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "pho chay",
    "description": "Vietnamese vegetarian noodle soup",
    "imageUrl": null,
    "cuisineType": "Vietnamese",
    "createdAt": "2026-10-01T10:00:00Z"
  }
}
```

| Field | Type | Description |
|-------|------|--------------|
| success | boolean | Always true for success |
| message | string | `Dish retrieved successfully` |
| data.id | uuid | Dish id — the same id recipe creation resolves `dishName` to |
| data.name | string | Canonical dish name |
| data.description | string \| null | Dish description |
| data.imageUrl | string \| null | Representative image URL |
| data.cuisineType | string \| null | Cuisine classification (e.g. Vietnamese, Mediterranean) |
| data.createdAt | string | ISO-8601 instant |

`DishResponse` is the same shape as each element of `GET /api/dishes`, minus the paging envelope, so one client mapper covers both. The admin counterpart (`/api/admin/dishes/{dishId}`) returns `AdminDishResponse`, which adds `moderationStatus` and `updatedAt`.

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `dishId` is not a valid UUID | "Validation failed" |
| 401 | Missing, invalid, expired JWT, or the account is inactive | "Unauthorized" |
| 404 | Dish does not exist, is retired (`deleted_at IS NOT NULL`), or its name is `PENDING` / `REJECTED` | "Dish not found" |
| 500 | Server error | "Internal server error" |

## Business Rules
- Only dishes with `moderation_status = APPROVED` and `deleted_at IS NULL` resolve. Pending and rejected names are reported as 404 rather than returned with a flag: the endpoint is member-facing, and "exists but you may not see it" would still confirm the name to whoever guessed the id (BR-DISH-001).
- A missing id, a retired id, and a not-yet-approved id are indistinguishable in the response — all three are `404 "Dish not found"`.
- Read-only: the endpoint never creates, approves, or modifies a `dish` row.
- Member reads never expose `moderationStatus` or `updatedAt`; use the admin endpoints for those.

## Example

### Request
```bash
curl -X GET "http://localhost:8080/api/dishes/550e8400-e29b-41d4-a716-446655440000" \
  -H "Authorization: Bearer <access-token>"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Dish retrieved successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "pho chay",
    "description": "Vietnamese vegetarian noodle soup",
    "imageUrl": null,
    "cuisineType": "Vietnamese",
    "createdAt": "2026-10-01T10:00:00Z"
  }
}
```

### Error Response (404)
```json
{
  "success": false,
  "message": "Dish not found",
  "data": null
}
```

## Related
- List counterpart: `docs/apis/recipes/get-dishes.md`
- Admin counterpart: `docs/apis/admin/dish-and-ingredient-crud.md`
- Name resolution on write: `docs/apis/recipes/post-recipes.md`
- Business rule: `docs/brs/dish-ingredient-name-approval.md` (BR-DISH-001)
- Feature spec: `docs/feats/list-dishes-api.md`
- Data model: `docs/arch/data-dictionary.md` Table 14 (Dish)
