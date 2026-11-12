# API Reference: DELETE /api/admin/restaurants/{restaurantId}

## Overview

Soft-delete a restaurant: sets `deletedAt`, the row is kept. Admin only (issue #144). Hard delete and restore are out of scope.

## Endpoint

```text
DELETE /api/admin/restaurants/{restaurantId}
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`), role `ADMIN`.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| restaurantId | UUID | Yes | ID of the restaurant to retire. |

### Request Body

No request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Restaurant deleted successfully",
  "data": null
}
```

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | JWT is missing, invalid, or expired | `Unauthorized` |
| 403 | Authenticated but not an Administrator | `Forbidden` |
| 404 | Restaurant does not exist or is already soft-deleted | `Restaurant not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Only an Administrator may delete a restaurant (BR-ADMIN-003).
- Deletion is soft (`deletedAt`); the row is kept, not hard-deleted, and cannot be restored through this API (issue #144 non-goals).
- Every successful delete writes a `moderation_log` row: `action = DELETE_RESTAURANT`, `targetType = RESTAURANT` — BR-ADMIN-002.

## Example

```bash
curl -X DELETE http://localhost:8080/api/admin/restaurants/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer <admin-access-token>"
```

## Related

- Create a restaurant: `docs/apis/admin/post-restaurants.md`
- Edit a restaurant: `docs/apis/admin/patch-restaurants-restaurantid.md`
