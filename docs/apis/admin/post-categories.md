# API Reference: POST /api/admin/categories

## Overview
Create a new content category. Admin only.

## Endpoint
```
POST /api/admin/categories
```

## Authentication
Required: Valid JWT access token in Authorization header (`Bearer <token>`), role `ADMIN`.

## Request

### Request Body
```json
{
  "name": "Pure Vegan",
  "description": "Strictly plant-based recipes and content"
}
```

| Field | Type | Required | Description |
|-------|------|----------|--------------|
| name | string | Yes | Category name, trimmed, max 100 characters. Must be unique (case-insensitive) among active categories. |
| description | string | No | Free-text description. Blank values are stored as `null`. |

## Responses

### Success Response (201)
```json
{
  "success": true,
  "message": "Category created successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Pure Vegan",
    "description": "Strictly plant-based recipes and content",
    "createdAt": "2026-09-30T10:00:00Z"
  }
}
```

| Field | Type | Description |
|-------|------|--------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data.id | uuid | Created category id |
| data.name | string | Category name |
| data.description | string \| null | Category description |
| data.createdAt | string | ISO-8601 instant |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Missing/blank name, or name over 100 characters | "Validation failed" |
| 401 | Missing/invalid/expired JWT | "Unauthorized" or filter plain-text token error |
| 403 | Authenticated non-admin | "Forbidden" |
| 409 | Name matches an existing active category (case-insensitive) | "Category name already exists" |
| 500 | Server error | "Internal server error" |

## Business Rules
- BR-ADMIN-003: Only `ROLE_ADMIN` may create content categories.
- Category name uniqueness is case-insensitive and scoped to active (non-deleted) categories.

## Example

### Request
```bash
curl -X POST "http://localhost:8080/api/admin/categories" \
  -H "Authorization: Bearer <admin_access_token>" \
  -H "Content-Type: application/json" \
  -d '{"name": "Pure Vegan", "description": "Strictly plant-based recipes and content"}'
```

### Success Response (201)
```json
{
  "success": true,
  "message": "Category created successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Pure Vegan",
    "description": "Strictly plant-based recipes and content",
    "createdAt": "2026-09-30T10:00:00Z"
  }
}
```

### Error Response (409)
```json
{
  "success": false,
  "message": "Category name already exists",
  "data": null
}
```

## Related
- Feature Spec: `docs/feats/create-category-api.md`
- Data dictionary: `docs/arch/data-dictionary.md` (Table 3: Category)
- Consumer: `docs/apis/post/post-posts.md` (categoryIds)
