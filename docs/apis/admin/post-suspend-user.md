# API Reference: POST /api/admin/users/{userId}/suspend

## Overview
Mark a user account as suspended and revoke all of that user's outstanding refresh tokens. Access tokens issued before suspension are rejected on the next authenticated request via account-state validation.

## Endpoint
```
POST /api/admin/users/{userId}/suspend
```

## Authentication
JWT Bearer — role `ADMIN` only (`/api/admin/**`).

## Request

### Path Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| userId | uuid | Yes | Target user id |

### Query Parameters
None

### Request Body
No request body

## Responses

### Success Response (200)
```json
{
  "success": true,
  "message": "User suspended successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "jane@example.com",
    "username": "jane",
    "role": "USER",
    "status": "suspended",
    "createdAt": "2026-09-21T10:00:00Z"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data.id | uuid | Suspended user id |
| data.email | string | User email |
| data.username | string | Username |
| data.role | string | ADMIN or USER |
| data.status | string | Always `suspended` on success |
| data.createdAt | string | ISO-8601 instant |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Invalid path uuid / other validation | "Validation failed" or handler default |
| 401 | Missing/invalid/expired JWT | "Unauthorized" or filter plain-text token error |
| 403 | Authenticated non-admin | "Forbidden" |
| 404 | User not found or soft-deleted | "User not found" |
| 409 | User already suspended | "User is already suspended" |
| 500 | Server error | "Internal server error" |

## Business Rules
- BR-AUTH-008: Login requires activated account (suspended cannot log in).
- BR-AUTH-016: Suspended/deactivated/missing accounts fail authentication on every request.
- Soft-deleted users (`deletedAt != null`) are treated as not found (404).
- Only `ROLE_ADMIN` may call this endpoint (`SecurityConfig` `/api/admin/**`).
- Suspension does not store a reason in this version (no schema change).

## Example

### Request
```bash
curl -X POST "http://localhost:8080/api/admin/users/550e8400-e29b-41d4-a716-446655440000/suspend" \
  -H "Authorization: Bearer <admin_access_token>"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "User suspended successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "jane@example.com",
    "username": "jane",
    "role": "USER",
    "status": "suspended",
    "createdAt": "2026-09-21T10:00:00Z"
  }
}
```

### Error Response (409)
```json
{
  "success": false,
  "message": "User is already suspended",
  "data": null
}
```

## Related
- Feature Spec: `docs/feats/suspend-user-account-admin-api.md`
- Driving issue: https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/25
- Companion API: `docs/apis/admin/get-users.md`
- Business Rules: `docs/brs/auth.md`
