# API Reference: GET /api/admin/users

## Overview
Return a paginated list of non-deleted user accounts, optionally filtered by status, role, and creation date range. Admin only.

## Endpoint
```
GET /api/admin/users
```

## Authentication
Required: Valid JWT access token in Authorization header (`Bearer <token>`), role `ADMIN`.

## Request

### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | integer | No | 0-based page index, default 0 |
| size | integer | No | Page size, default 20, max 100 |
| sort | string | No | Format `property,direction`. Default `createdAt,desc` |
| status | string | No | One of: `created`, `activated`, `deactivated`, `suspended` |
| role | string | No | One of: `ADMIN`, `USER` |
| createdFrom | string | No | ISO-8601 date or datetime lower bound (inclusive) on `createdAt` |
| createdTo | string | No | ISO-8601 date or datetime upper bound (inclusive) on `createdAt` |

### Request Body
No request body

## Responses

### Success Response (200 OK)
```json
{
  "success": true,
  "message": "Users retrieved successfully",
  "data": {
    "content": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "email": "jane@example.com",
        "username": "jane",
        "role": "USER",
        "status": "activated",
        "createdAt": "2026-09-21T10:00:00Z"
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
| message | string | "Users retrieved successfully" |
| data.content | array | User items for this page |
| data.content[].id | uuid | User id |
| data.content[].email | string | Email address |
| data.content[].username | string | Username |
| data.content[].role | string | ADMIN or USER |
| data.content[].status | string | created, activated, deactivated, or suspended |
| data.content[].createdAt | string | ISO-8601 account creation timestamp |
| data.page | integer | 0-based page index returned |
| data.size | integer | Page size used |
| data.totalElements | integer | Total matching users |
| data.totalPages | integer | Total page count |
| data.first | boolean | Whether this is the first page |
| data.last | boolean | Whether this is the last page |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | Missing/invalid JWT | "Unauthorized" |
| 403 | Authenticated non-admin | "Forbidden" |
| 500 | Server error | "Internal server error" |

## Business Rules
- Soft-deleted users (`deletedAt != null`) are excluded (issue #24 / FR-005).
- Only `ROLE_ADMIN` may call this endpoint (SecurityConfig `/api/admin/**`).

## Example

### Request
```bash
curl -X GET "http://localhost:8080/api/admin/users?page=0&size=20&status=activated&role=USER&sort=createdAt,desc" \
  -H "Authorization: Bearer <admin_access_token>"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Users retrieved successfully",
  "data": {
    "content": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "email": "jane@example.com",
        "username": "jane",
        "role": "USER",
        "status": "activated",
        "createdAt": "2026-09-21T10:00:00Z"
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
- Feature Spec: `docs/feats/list-user-accounts-admin-api.md`
- Driving issue: https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/24
- Business Rules: `docs/brs/auth.md` (JWT authentication)
