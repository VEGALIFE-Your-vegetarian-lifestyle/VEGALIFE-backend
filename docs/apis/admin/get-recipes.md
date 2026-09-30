# API Reference: GET /api/admin/recipes

## Overview
Return a paginated list of every recipe across all users — including soft-deleted rows, each reported with a derived `status` — optionally filtered by author, category, and creation date range, with ingredients, instructions, and author identity on each item. Admin only.

## Endpoint
```
GET /api/admin/recipes
```

## Authentication
Required: Valid JWT access token in Authorization header (`Bearer <token>`), role `ADMIN`.

## Request

### Path Parameters
None.

### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | integer | No | 0-based page index, default 0 |
| size | integer | No | Page size, default 20, max 100 |
| sort | string | No | Format `property,direction`. Default `createdAt,desc`. Property must be one of: `createdAt`, `updatedAt`, `name` |
| userId | uuid | No | Only recipes authored by this user |
| categoryId | uuid | No | Only recipes linked (via `post_recipe` → `post_category`) to a post carrying this category |
| createdFrom | string | No | ISO-8601 datetime lower bound (inclusive) on `createdAt`, e.g. `2026-01-01T00:00:00Z` |
| createdTo | string | No | ISO-8601 datetime upper bound (inclusive) on `createdAt`, e.g. `2026-01-31T23:59:59Z` |

### Request Body
No request body

## Responses

### Success Response (200 OK)
```json
{
  "success": true,
  "message": "Recipes retrieved successfully",
  "data": {
    "content": [
      {
        "id": "3f1d2a44-9f5c-4a3b-8d9e-1c2b3a4e5f60",
        "name": "Vegan pho",
        "description": "A clear mushroom broth with rice noodles",
        "instructions": "1. Simmer mushrooms...\n2. Cook noodles...\n3. Assemble and serve.",
        "prepTimeMinutes": 20,
        "cookTimeMinutes": 40,
        "servings": 4,
        "difficulty": "MEDIUM",
        "status": "ACTIVE",
        "dishId": "9a8b7c6d-5e4f-4a3b-2c1d-0e9f8a7b6c5d",
        "dishName": "Pho",
        "categoryIds": ["1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed"],
        "ingredients": [
          {
            "ingredientId": "c7d8e9f0-1a2b-4c3d-8e5f-6a7b8c9d0e1f",
            "name": "shiitake mushroom",
            "amount": 200,
            "unit": "g"
          }
        ],
        "createdAt": "2026-09-28T08:00:00Z",
        "updatedAt": "2026-09-28T09:00:00Z",
        "userId": "550e8400-e29b-41d4-a716-446655440000",
        "username": "jane",
        "email": "jane@example.com"
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
| success | boolean | Always true for success |
| message | string | "Recipes retrieved successfully" |
| data.content | array | Recipe items for this page |
| data.content[].id | uuid | Recipe id |
| data.content[].name | string | Recipe name |
| data.content[].description | string | Recipe description, nullable |
| data.content[].instructions | string | Full cooking instructions text |
| data.content[].prepTimeMinutes | integer | Preparation time in minutes, nullable |
| data.content[].cookTimeMinutes | integer | Cooking time in minutes, nullable |
| data.content[].servings | integer | Number of servings |
| data.content[].difficulty | string | `EASY`, `MEDIUM`, `HARD`, or null |
| data.content[].status | string | Derived from `deletedAt`: `ACTIVE` or `DELETED` (no stored column, not filterable) |
| data.content[].dishId | uuid | Parent dish id |
| data.content[].dishName | string | Parent dish name, nullable |
| data.content[].categoryIds | uuid[] | Category ids via the recipe's linked posts; empty when not linked |
| data.content[].ingredients | array | Ingredients with amounts; empty when none |
| data.content[].ingredients[].ingredientId | uuid | Ingredient id |
| data.content[].ingredients[].name | string | Ingredient name |
| data.content[].ingredients[].amount | number | Amount in `unit` |
| data.content[].ingredients[].unit | string | Measurement unit (e.g. `g`, `ml`, `tbsp`) |
| data.content[].createdAt | string | ISO-8601 creation timestamp |
| data.content[].updatedAt | string | ISO-8601 last-update timestamp |
| data.content[].userId | uuid | Author user id |
| data.content[].username | string | Author username |
| data.content[].email | string | Author email |
| data.page | integer | 0-based page index returned |
| data.size | integer | Page size used |
| data.totalElements | integer | Total matching recipes |
| data.totalPages | integer | Total page count |
| data.first | boolean | Whether this is the first page |
| data.last | boolean | Whether this is the last page |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Unknown `sort` property, invalid date format, `createdFrom` after `createdTo`, or `size` > 100 | "Validation failed" or the parse error message |
| 401 | Missing/invalid JWT | "Unauthorized" |
| 403 | Authenticated non-admin | "Forbidden" |
| 500 | Server error | "Internal server error" |

## Business Rules
- Soft-deleted recipes are **included** and reported as `status = DELETED`; `status` is derived from `deletedAt`, not stored (issue #4 deviation, decided 2026-10-01).
- No `status` query filter exists on this endpoint.
- Only `ROLE_ADMIN` may call this endpoint (`SecurityConfig` `/api/admin/**`).
- `categoryId` resolves through `post_recipe` → `post_category`; a recipe not linked to any post matches only when `categoryId` is omitted.
- Nutrition columns on `ingredient` are never returned (issue #4 non-goal).

## Example

### Request
```bash
curl -X GET "http://localhost:8080/api/admin/recipes?page=0&size=20&userId=550e8400-e29b-41d4-a716-446655440000&sort=createdAt,desc" \
  -H "Authorization: Bearer <admin_access_token>"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Recipes retrieved successfully",
  "data": {
    "content": [
      {
        "id": "3f1d2a44-9f5c-4a3b-8d9e-1c2b3a4e5f60",
        "name": "Vegan pho",
        "description": "A clear mushroom broth with rice noodles",
        "instructions": "1. Simmer mushrooms...\n2. Cook noodles...\n3. Assemble and serve.",
        "prepTimeMinutes": 20,
        "cookTimeMinutes": 40,
        "servings": 4,
        "difficulty": "MEDIUM",
        "status": "ACTIVE",
        "dishId": "9a8b7c6d-5e4f-4a3b-2c1d-0e9f8a7b6c5d",
        "dishName": "Pho",
        "categoryIds": ["1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed"],
        "ingredients": [
          {
            "ingredientId": "c7d8e9f0-1a2b-4c3d-8e5f-6a7b8c9d0e1f",
            "name": "shiitake mushroom",
            "amount": 200,
            "unit": "g"
          }
        ],
        "createdAt": "2026-09-28T08:00:00Z",
        "updatedAt": "2026-09-28T09:00:00Z",
        "userId": "550e8400-e29b-41d4-a716-446655440000",
        "username": "jane",
        "email": "jane@example.com"
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

### Error Response (403)
```json
{
  "success": false,
  "message": "Forbidden",
  "data": null
}
```

## Related
- Feature Spec: `docs/feats/list-all-recipes-admin-api.md`
- Driving issue: https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/4
- Business Rules: `docs/brs/auth.md` (JWT authentication)
