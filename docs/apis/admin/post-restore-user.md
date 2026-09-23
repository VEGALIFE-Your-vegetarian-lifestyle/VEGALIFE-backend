# API Reference: POST /api/admin/users/{userId}/restore

## Overview
Reverse a suspension: set a suspended user's status back to `activated` so they can log in and use the API again. The restoration is written to the audit log.

## Endpoint
```
POST /api/admin/users/{userId}/restore
```

## Authentication
JWT Bearer — role `ADMIN` only (`/api/admin/**`).

## Request

### Path Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| userId | uuid | Yes | Target user id (must currently be `suspended`) |

### Query Parameters
None

### Request Body
No request body

## Responses

### Success Response (200)
```json
{
  "success": true,
  "message": "User restored successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "jane@example.com",
    "username": "jane",
    "role": "USER",
    "status": "activated",
    "createdAt": "2026-09-21T10:00:00Z"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data.id | uuid | Restored user id |
| data.email | string | User email |
| data.username | string | Username |
| data.role | string | ADMIN or USER |
| data.status | string | Always `activated` on success |
| data.createdAt | string | ISO-8601 instant |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Invalid path uuid / other validation | "Validation failed" or handler default |
| 401 | Missing/invalid/expired JWT | "Unauthorized" or filter plain-text token error |
| 403 | Authenticated non-admin | "Forbidden" |
| 404 | User not found or soft-deleted | "User not found" |
| 409 | User is not currently suspended | "User is not suspended" |
| 500 | Server error | "Internal server error" |

## Business Rules
- BR-AUTH-008: Login requires an activated account — restore returns the account to that state.
- BR-AUTH-016: Non-activated accounts fail authentication on every request — applies until restore.
- Soft-deleted users (`deletedAt != null`) are treated as not found (404).
- Only `ROLE_ADMIN` may call this endpoint (`SecurityConfig` `/api/admin/**`).
- Only `suspended` accounts can be restored; `created`, `activated`, `deactivated` → 409.
- Restoration is audited via a log line (no audit table in this version; no schema change).

## Example

### Request
```bash
curl -X POST "http://localhost:8080/api/admin/users/550e8400-e29b-41d4-a716-446655440000/restore" \
  -H "Authorization: Bearer <admin_access_token>"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "User restored successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "jane@example.com",
    "username": "jane",
    "role": "USER",
    "status": "activated",
    "createdAt": "2026-09-21T10:00:00Z"
  }
}
```

### Error Response (409)
```json
{
  "success": false,
  "message": "User is not suspended",
  "data": null
}
```

## Related
- Feature Spec: `docs/feats/restore-user-account-admin-api.md`
- Driving issue: https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/26
- Companion API: `docs/apis/admin/post-suspend-user.md`, `docs/apis/admin/get-users.md`
- Business Rules: `docs/brs/auth.md`
