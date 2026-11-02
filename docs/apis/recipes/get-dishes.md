# API Reference: GET /api/dishes

## Overview
List active canonical dishes, paginated and optionally filtered by name — the suggestion source for the create-recipe form's `dishName` autocomplete. Requires authentication.

## Endpoint
```
GET /api/dishes
```

## Authentication
JWT Bearer required. Requests without a valid token get 401.

## Request

### Path Parameters
No path parameters

### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | integer | No | 0-based page index, default 0 |
| size | integer | No | Page size, default 20, min 1, max 100 |
| sort | string | No | Format `property,direction`. Default `name,asc` |
| name | string | No | Case-insensitive substring filter on dish name |

### Request Body
No request body

## Responses

### Success Response (200)
```json
{
  "success": true,
  "message": "Dishes retrieved successfully",
  "data": {
    "content": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "pho chay",
        "description": "Vietnamese vegetarian noodle soup",
        "imageUrl": null,
        "cuisineType": "Vietnamese",
        "createdAt": "2026-10-01T10:00:00Z"
      },
      {
        "id": "6f9619ff-8b86-d011-b42d-00c04fc964ff",
        "name": "tofu stir-fry",
        "description": null,
        "imageUrl": null,
        "cuisineType": null,
        "createdAt": "2026-10-01T09:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

| Field | Type | Description |
|-------|------|--------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data.content | array | Active dishes on this page, sorted per `sort` (default `name` ascending) |
| data.content[].id | uuid | Dish id — the same id recipe creation resolves `dishName` to |
| data.content[].name | string | Canonical dish name |
| data.content[].description | string \| null | Dish description |
| data.content[].imageUrl | string \| null | Representative image URL |
| data.content[].cuisineType | string \| null | Cuisine classification (e.g. Vietnamese, Mediterranean) |
| data.content[].createdAt | string | ISO-8601 instant |
| data.page | integer | Current page index |
| data.size | integer | Page size |
| data.totalElements | long | Total matching dishes across all pages |
| data.totalPages | integer | Total number of pages |
| data.first | boolean | Whether this is the first page |
| data.last | boolean | Whether this is the last page |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `page`/`size` out of range, or malformed `sort` | "Validation failed" / sort error message |
| 401 | Missing or invalid JWT | "Unauthorized" |
| 500 | Server error | "Internal server error" |

## Business Rules
- Retired (soft-deleted) dishes (`deleted_at IS NOT NULL`) are never included, in results or in `totalElements`.
- `name` matches as a case-insensitive substring (e.g. `name=pho` matches "Pho Chay").
- Active dish names are unique case-insensitively (`idx_dish_active_name_unique`), so each distinct dish appears exactly once.
- Read-only: the endpoint never creates or modifies a `dish` row.

## Example

### Request
```bash
curl -X GET "http://localhost:8080/api/dishes?name=pho&page=0&size=20" \
  -H "Authorization: Bearer <access-token>"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Dishes retrieved successfully",
  "data": {
    "content": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "pho chay",
        "description": "Vietnamese vegetarian noodle soup",
        "imageUrl": null,
        "cuisineType": "Vietnamese",
        "createdAt": "2026-10-01T10:00:00Z"
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

## Related
- Feature Spec: `docs/feats/list-dishes-api.md`
- Companion API: `docs/apis/recipes/post-recipes.md` (`dishName` find-or-create)
- Pattern source: `docs/apis/post/get-categories.md`
- Data model: `docs/arch/data-dictionary.md` Table 14 (Dish)
