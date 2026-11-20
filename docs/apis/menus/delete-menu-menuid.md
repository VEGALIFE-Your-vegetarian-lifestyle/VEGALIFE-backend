# API Reference: DELETE /api/menus/{menuId}

## Overview

Deletes one of the authenticated member's weekly menus, in any status.
Deletion is **soft**: the menu and its recorded days stay in the database
and are marked removed, so the plan disappears from every read without
destroying its history.

## Endpoint

```text
DELETE /api/menus/{menuId}
```

## Authentication

Required: a valid JWT access token in the `Authorization` header
(`Bearer <token>`). The user ID is taken from that token; a menu belonging
to another user is indistinguishable from a non-existent one (404).

## Request

### Path Parameters

| Name | Type | Description |
|------|------|-------------|
| menuId | uuid | Identifier of the owned menu to delete. |

### Request Body

No request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Menu deleted successfully",
  "data": null
}
```

`data` is `null` — a deleted menu has no representation to return. Read the
plan back only for menus that still exist, via `GET /api/menus` or
`GET /api/menus/details`.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 404 | `menuId` is unknown, belongs to another user, or was already deleted | `Menu not found` |
| 401 | JWT is missing, invalid, or the account is inactive | `Unauthorized` |
| 500 | Unexpected server error | `Internal server error` |

There is no 409: every status is deletable, and deletion has no conflict
condition.

## Business Rules

- BR-MENU-001 — the menu is located by id **and** owner from the JWT; a
  foreign id is a 404, never a 403.
- BR-MENU-008 — deletion is soft, permitted in any status, and removes the
  menu from every read path and from the scheduled-overlap check.

### Delete is not cancel

`POST /api/menus/{menuId}/status` with `cancelled` and this endpoint are
different operations:

| | Cancel (`status` → `cancelled`) | Delete |
|---|---|---|
| Menu still listed | Yes, in terminal `cancelled` state | No |
| Menu still readable by id | Yes | No (404) |
| Meals preserved | Yes | Yes (soft delete keeps rows) |
| Week freed for rescheduling | No — the row stays `cancelled`, which the overlap rule does not constrain | Yes |
| Repeatable | No, a second cancel is a 409 | No, a second delete is a 404 |

Deleting the live (`scheduled`) week is allowed and simply frees it: another
menu may then be scheduled over the same dates (BR-MENU-003).

## Not an AI tool

This endpoint is deliberately **not** exposed to the AI chat. The tool surface
stays the six documented tools (BR-MCP-002); the assistant can read, create,
update, and schedule menus but cannot delete one.

## Example

### Request

```bash
curl -X DELETE /api/menus/7c1f2b90-4a3d-4e8a-9d5b-2f6e8c0a1d4e \
  -H "Authorization: Bearer <accessToken>"
```

### Success Response (200)

```json
{
  "success": true,
  "message": "Menu deleted successfully",
  "data": null
}
```

### Deleting it again (404)

```json
{
  "success": false,
  "message": "Menu not found",
  "errors": []
}
```

---

## Related

- Create endpoint: `docs/apis/menus/post-menus.md`
- Update endpoint: `docs/apis/menus/patch-menus.md`
- Status endpoint: `docs/apis/menus/post-menu-status.md`
- List endpoint: `docs/apis/menus/get-menus.md`
- Detail endpoint: `docs/apis/menus/get-menus-details.md`
- Business Rules: `docs/brs/menus.md`
- Shared error/response contract: `docs/apis/error-responses.md`
