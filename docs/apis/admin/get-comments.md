# API Reference: GET /api/admin/comments

## Overview
Return a paginated list of all comments across the platform (all users, both active and removed), optionally filtered by status, author, post, and creation date range. Admin only.

## Endpoint
```
GET /api/admin/comments
```

## Authentication
Required: Valid JWT access token in Authorization header (`Bearer <token>`), role `ADMIN`.

## Request

### Path Parameters
None

### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | integer | No | 0-based page index, default 0 |
| size | integer | No | Page size, default 20, max 100 |
| sort | string | No | Format `property,direction`. Default `createdAt,desc`. Property allowlisted to `createdAt` or `updatedAt` |
| status | string | No | One of: `active`, `removed`. Omit to return both |
| userId | uuid | No | Filter by comment author |
| postId | uuid | No | Filter by post the comment belongs to |
| createdFrom | string | No | ISO-8601 date or datetime lower bound (inclusive) on `createdAt` |
| createdTo | string | No | ISO-8601 date or datetime upper bound (inclusive) on `createdAt` |

### Request Body
No request body

## Responses

### Success Response (200 OK)
```json
{
  "success": true,
  "message": "Comments retrieved successfully",
  "data": {
    "content": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "postId": "660e8400-e29b-41d4-a716-446655440000",
        "parentId": null,
        "userId": "770e8400-e29b-41d4-a716-446655440000",
        "username": "jane",
        "content": "Great recipe!",
        "status": "active",
        "createdAt": "2026-09-28T10:00:00Z",
        "updatedAt": "2026-09-28T10:00:00Z"
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
| message | string | "Comments retrieved successfully" |
| data.content | array | Comment items for this page |
| data.content[].id | uuid | Comment id |
| data.content[].postId | uuid | Post the comment belongs to |
| data.content[].parentId | uuid \| null | Parent comment id for replies; null for top-level comments |
| data.content[].userId | uuid | Author's user id |
| data.content[].username | string | Author's username |
| data.content[].content | string | Comment text |
| data.content[].status | string | `active` (not deleted) or `removed` (soft-deleted) |
| data.content[].createdAt | string | ISO-8601 creation timestamp |
| data.content[].updatedAt | string | ISO-8601 last update timestamp |
| data.page | integer | 0-based page index returned |
| data.size | integer | Page size used |
| data.totalElements | integer | Total matching comments |
| data.totalPages | integer | Total page count |
| data.first | boolean | Whether this is the first page |
| data.last | boolean | Whether this is the last page |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Invalid `status`, non-allowlisted `sort` property, malformed UUID, or `createdFrom` after `createdTo` | Validation message (e.g. "Status must be one of: active, removed") |
| 401 | Missing/invalid JWT | "Unauthorized" |
| 403 | Authenticated non-admin | "Forbidden" |
| 500 | Server error | "Internal server error" |

## Business Rules
- Status is derived from `deleted_at`: `active` = `deleted_at IS NULL`, `removed` = `deleted_at IS NOT NULL` (issue #2 / FR-005). No `all` value — omit the parameter to get both.
- Only `ROLE_ADMIN` may call this endpoint (SecurityConfig `/api/admin/**`).
- `parentId` is returned as a plain field; thread context is not resolved (issue #2 non-goal).

## Example

### Request
```bash
curl -X GET "http://localhost:8080/api/admin/comments?page=0&size=20&status=active&postId=660e8400-e29b-41d4-a716-446655440000&sort=createdAt,desc" \
  -H "Authorization: Bearer <admin_access_token>"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Comments retrieved successfully",
  "data": {
    "content": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "postId": "660e8400-e29b-41d4-a716-446655440000",
        "parentId": null,
        "userId": "770e8400-e29b-41d4-a716-446655440000",
        "username": "jane",
        "content": "Great recipe!",
        "status": "active",
        "createdAt": "2026-09-28T10:00:00Z",
        "updatedAt": "2026-09-28T10:00:00Z"
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
- Feature Spec: `docs/feats/list-comments-admin-api.md`
- Driving issue: https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/2
- Business Rules: `docs/brs/auth.md` (JWT authentication)
