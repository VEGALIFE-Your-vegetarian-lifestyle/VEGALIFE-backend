# API Reference: PATCH /api/menus/{menuId}

## Overview

Update one of the authenticated member's weekly menus: replace its meals
and/or its dietary preferences and notes. Only `drafted` and `scheduled`
menus can be edited; the menu's week (start and end dates) never changes.

## Endpoint

```text
PATCH /api/menus/{menuId}
```

## Authentication

Required: a valid JWT access token in the `Authorization` header
(`Bearer <token>`). The user ID is taken from that token; a menu belonging
to another user is indistinguishable from a non-existent one (404).

## Request

### Path Parameters

| Name | Type | Description |
|------|------|-------------|
| menuId | uuid | Identifier of the owned menu to update. |

### Request Body

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| days | array of day objects | Yes | The complete new week: exactly the 7 dates of the menu (`startDate .. endDate`), each once. Meals are replaced wholesale — the existing set is discarded. Same day/meal shape and rules as `POST /api/menus`. |
| dietaryPreferences | string | No | New free-text dietary preference. Omitted (or `null`) keeps the current value. |
| notes | string | No | New free-text note. Omitted (or `null`) keeps the current value. |

`startDate` and `endDate` are never accepted: a menu keeps its original
week.

### Re-validation (BR-MENU-005)

Every update re-runs the full week validation against the resulting menu:
7 dates, 1–3 meals per date, at most one meal per `meal_type` per date,
`servings >= 1`, every dish exists and is not soft-deleted, no dish more
than 3 times, at least 5 distinct dishes. There is no partial update of
meals — an update that would produce an invalid week is rejected whole.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Menu updated successfully",
  "data": {
    "id": "7c1f2b90-4a3d-4e8a-9d5b-2f6e8c0a1d4e",
    "startDate": "2026-10-05",
    "endDate": "2026-10-11",
    "status": "scheduled",
    "notes": "Updated prep notes",
    "dietaryPreferences": "soy-free",
    "createdAt": "2026-10-12T09:00:00Z",
    "updatedAt": "2026-10-12T10:00:00Z",
    "days": [
      {
        "date": "2026-10-05",
        "meals": [
          {
            "mealType": "BREAKFAST",
            "dishId": "9b4d1e77-1f2a-4c5d-8e6f-3a7b2c9d0e11",
            "dishName": "Tofu Scramble",
            "servings": 3
          }
        ]
      }
    ]
  }
}
```

The `data` payload is the same full-detail shape as
`GET /api/menus/{menuId}` and `POST /api/menus`.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Bean-validation failure (missing `days`) or malformed body | Field-level message |
| 400 | The resulting week violates BR-MENU-005 | Clear message naming the rule, e.g. `a menu must reference at least 5 distinct dishes` |
| 404 | `menuId` is unknown or belongs to another user | `Menu not found` |
| 409 | The menu is `cancelled` or `completed` | `Only drafted or scheduled menus can be updated` |
| 401 | JWT is missing, invalid, or the account is inactive | `Unauthorized` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- BR-MENU-001 — the menu is located by id **and** owner from the JWT; a
  foreign id is a 404, never a 403.
- BR-MENU-005 — the resulting week is fully re-validated on every update.
- BR-MENU-006 — only `drafted` and `scheduled` menus are editable; the
  week never changes, so the scheduled-overlap state cannot change.

## Example

### Request

```bash
curl -X PATCH /api/menus/7c1f2b90-4a3d-4e8a-9d5b-2f6e8c0a1d4e \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "dietaryPreferences": "soy-free",
    "days": [
      {
        "date": "2026-10-05",
        "meals": [
          { "mealType": "BREAKFAST", "dishId": "9b4d1e77-1f2a-4c5d-8e6f-3a7b2c9d0e11", "servings": 3 },
          { "mealType": "LUNCH", "dishId": "2e8c5a10-9b3d-4f7a-8c1e-6d4b0a9f3e22", "servings": 1 },
          { "mealType": "DINNER", "dishId": "5a3f7c21-7d8e-4b1a-9c4f-8e2d6b0a5c33", "servings": 2 }
        ]
      },
      { "date": "2026-10-06", "meals": [ /* …6 more days… */ ] }
    ]
  }'
```

### Success Response (200)

See the success response above.

---

## Related

- Create endpoint: `docs/apis/menus/post-menus.md`
- List endpoint: `docs/apis/menus/get-menus.md`
- Detail endpoint: `docs/apis/menus/get-menus-menuid.md`
- Status endpoint: `docs/apis/menus/post-menu-status.md`
- Business Rules: `docs/brs/menus.md`
- Shared error/response contract: `docs/apis/error-responses.md`
