# API Reference: DELETE /api/admin/categories/{categoryId}

## Overview
Retire a content category. Admin only. This is a soft delete: `deleted_at` is set and the row is kept, so posts that already reference the category keep that reference. A retired category can no longer be assigned to new or edited posts.

## Endpoint
```
DELETE /api/admin/categories/{categoryId}
```

## Authentication
Required: Valid JWT access token in Authorization header (`Bearer <token>`), role `ADMIN`.

## Request

### Path Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| categoryId | uuid | Yes | Target category id |

### Request Body
No request body

## Responses

### Success Response (200)
```json
{
  "success": true,
  "message": "Category deleted successfully",
  "data": null
}
```

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | Missing/invalid/expired JWT | "Unauthorized" or filter plain-text token error |
| 403 | Authenticated non-admin | "Forbidden" |
| 404 | Category not found or already retired | "Category not found" |
| 500 | Server error | "Internal server error" |

## Business Rules
- BR-ADMIN-003: Only `ROLE_ADMIN` may retire content categories; a category is never hard-deleted, so existing posts that reference it are never broken.
- Retiring an already-retired category is not idempotent success — it returns 404, matching `DELETE /api/posts/{postId}`.
- A retired category no longer passes `PostService.resolveCategories`'s active-category check (BR-CONTENT-004), so it can't be assigned to new or edited posts, but posts that already have it keep it.

## Example

### Request
```bash
curl -X DELETE "http://localhost:8080/api/admin/categories/550e8400-e29b-41d4-a716-446655440000" \
  -H "Authorization: Bearer <admin_access_token>"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Category deleted successfully",
  "data": null
}
```

### Error Response (404)
```json
{
  "success": false,
  "message": "Category not found",
  "data": null
}
```

## Related
- Feature Spec: `docs/feats/delete-category-api.md`
- Companion API: `docs/apis/admin/post-categories.md` (creation), `docs/apis/admin/patch-categories-categoryid.md` (edit)
