# API Reference: GET /api/categories

## Overview
List every active content category, sorted by name. Public — no authentication required.

## Endpoint
```
GET /api/categories
```

## Authentication
None. Available to any caller, logged in or anonymous.

## Request

### Query Parameters
None

### Request Body
No request body

## Responses

### Success Response (200)
```json
{
  "success": true,
  "message": "Categories retrieved successfully",
  "data": [
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
  ]
}
```

| Field | Type | Description |
|-------|------|--------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data | array | Active categories, sorted by `name` ascending. Empty array if none exist. |
| data[].id | uuid | Category id |
| data[].name | string | Category name |
| data[].description | string \| null | Category description |
| data[].createdAt | string | ISO-8601 instant |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 500 | Server error | "Internal server error" |

## Business Rules
- Retired (soft-deleted) categories are never included.
- No pagination: the category set is expected to stay small.

## Example

### Request
```bash
curl -X GET "http://localhost:8080/api/categories"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Categories retrieved successfully",
  "data": []
}
```

## Related
- Feature Spec: `docs/feats/list-categories-api.md`
- Companion API: `docs/apis/admin/post-categories.md` (creation, Admin), `docs/apis/admin/patch-categories-categoryid.md` (edit, Admin), `docs/apis/admin/delete-categories-categoryid.md` (retire, Admin)
- Consumer: `docs/apis/post/post-posts.md` (categoryIds)
