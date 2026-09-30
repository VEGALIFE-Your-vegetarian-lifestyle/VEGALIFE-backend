# API Reference: PATCH /api/admin/categories/{categoryId}

## Overview
Edit an existing content category's name and/or description. Admin only. Partial update: an omitted or `null` field keeps its current value.

## Endpoint
```
PATCH /api/admin/categories/{categoryId}
```

## Authentication
Required: Valid JWT access token in Authorization header (`Bearer <token>`), role `ADMIN`.

## Request

### Path Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| categoryId | uuid | Yes | Target category id |

### Request Body
```json
{
  "name": "High-Protein Vegan",
  "description": "Plant-based recipes with 20g+ protein per serving"
}
```

| Field | Type | Required | Description |
|-------|------|----------|--------------|
| name | string | No | New category name, trimmed, max 100 characters. Must be unique (case-insensitive) among other active categories. Omit or `null` to keep the current name. |
| description | string | No | New description. Blank values are stored as `null`. Omit or `null` to keep the current description — a description cannot be cleared via `null` (send an empty string `""` instead). |

At least one of `name`, `description` must be present.

## Responses

### Success Response (200)
```json
{
  "success": true,
  "message": "Category updated successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "High-Protein Vegan",
    "description": "Plant-based recipes with 20g+ protein per serving",
    "createdAt": "2026-09-30T10:00:00Z"
  }
}
```

| Field | Type | Description |
|-------|------|--------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data.id | uuid | Updated category id |
| data.name | string | Current category name |
| data.description | string \| null | Current category description |
| data.createdAt | string | ISO-8601 instant |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Neither field present, blank name, or name over 100 characters | "Validation failed" |
| 401 | Missing/invalid/expired JWT | "Unauthorized" or filter plain-text token error |
| 403 | Authenticated non-admin | "Forbidden" |
| 404 | Category not found or soft-deleted | "Category not found" |
| 409 | New name matches another existing active category (case-insensitive) | "Category name already exists" |
| 500 | Server error | "Internal server error" |

## Business Rules
- BR-ADMIN-003: Only `ROLE_ADMIN` may edit content categories.
- Category name uniqueness is case-insensitive and scoped to active (non-deleted) categories, excluding the category being edited.

## Example

### Request
```bash
curl -X PATCH "http://localhost:8080/api/admin/categories/550e8400-e29b-41d4-a716-446655440000" \
  -H "Authorization: Bearer <admin_access_token>" \
  -H "Content-Type: application/json" \
  -d '{"description": "Plant-based recipes with 20g+ protein per serving"}'
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Category updated successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Pure Vegan",
    "description": "Plant-based recipes with 20g+ protein per serving",
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
- Feature Spec: `docs/feats/edit-category-api.md`
- Companion API: `docs/apis/admin/post-categories.md` (creation)
