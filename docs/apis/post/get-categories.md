# API Reference: GET /api/categories

## Overview
List active content categories, paginated and optionally filtered by name. Public — no authentication required.

## Endpoint
```
GET /api/categories
```

## Authentication
None. Available to any caller, logged in or anonymous.

## Request

### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | integer | No | 0-based page index, default 0 |
| size | integer | No | Page size, default 20, max 100 |
| sort | string | No | Format `property,direction`. Default `name,asc` |
| name | string | No | Case-insensitive substring filter on category name |

### Request Body
No request body

## Responses

### Success Response (200)
```json
{
  "success": true,
  "message": "Categories retrieved successfully",
  "data": {
    "content": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Dessert",
        "description": null,
        "createdAt": "2026-09-30T10:00:00Z"
      },
      {
        "id": "6f9619ff-8b86-d011-b42d-00c04fc964ff",
        "name": "Vegan",
        "description": "Strictly plant-based recipes and content",
        "createdAt": "2026-09-30T09:00:00Z"
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
| data.content | array | Active categories on this page, sorted per `sort` (default `name` ascending) |
| data.content[].id | uuid | Category id |
| data.content[].name | string | Category name |
| data.content[].description | string \| null | Category description |
| data.content[].createdAt | string | ISO-8601 instant |
| data.page | integer | Current page index |
| data.size | integer | Page size |
| data.totalElements | long | Total matching categories across all pages |
| data.totalPages | integer | Total number of pages |
| data.first | boolean | Whether this is the first page |
| data.last | boolean | Whether this is the last page |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `page`/`size` out of range, or malformed `sort` | "Validation failed" |
| 500 | Server error | "Internal server error" |

## Business Rules
- Retired (soft-deleted) categories are never included.
- `name` matches as a case-insensitive substring (e.g. `name=vegan` matches "Pure Vegan" and "High-Protein Vegan").

## Example

### Request
```bash
curl -X GET "http://localhost:8080/api/categories?name=vegan&page=0&size=20"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Categories retrieved successfully",
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
- Feature Spec: `docs/feats/list-categories-api.md`
- Companion API: `docs/apis/admin/post-categories.md` (creation, Admin), `docs/apis/admin/patch-categories-categoryid.md` (edit, Admin), `docs/apis/admin/delete-categories-categoryid.md` (retire, Admin)
- Consumer: `docs/apis/post/post-posts.md` (categoryIds)
