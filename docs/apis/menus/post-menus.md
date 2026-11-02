# API Reference: POST /api/menus

## Overview

Create a weekly menu for the authenticated member, always in status `drafted`,
with its seven days of meals in a single request. Scheduling is a separate
transition (`POST /api/menus/{menuId}/status`); this endpoint never creates a
`scheduled` menu.

## Endpoint

```text
POST /api/menus
```

## Authentication

Required: a valid JWT access token in the `Authorization` header
(`Bearer <token>`). The user ID is taken from that token, so callers cannot
create menus for another user.

## Request

### Path Parameters

No path parameters.

### Request Body

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| startDate | date (`YYYY-MM-DD`) | Yes | First day of the week. `endDate` is derived as `startDate + 6`; it is never accepted from the client. |
| notes | string | No | Free-text note (the week's summary: theme and honored restrictions). |
| days | array of day objects | Yes | Exactly the 7 dates `startDate .. startDate + 6`, each appearing once. |

Day object:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| date | date (`YYYY-MM-DD`) | Yes | Must be one of the 7 dates of the week. |
| meals | array of meal objects | Yes | 1–3 meals. |

Meal object:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| mealType | string | Yes | `BREAKFAST`, `LUNCH`, or `DINNER`. At most one meal per type per date. |
| dishId | uuid | Yes | An existing, non-soft-deleted dish. |
| servings | integer | Yes | Portions for this meal; must be ≥ 1. Persisted on `menu_detail.servings`. |

### Structural rules (BR-MENU-005)

- Exactly the 7 dates of the week, each once; 1–3 meals per date; at most one
  meal per `meal_type` per date.
- `servings ≥ 1`.
- Every `dish_id` exists and is not soft-deleted.
- No dish appears more than 3 times in the menu; at least 5 distinct dishes
  are referenced.
- Identity is never a body field (`user_id` is not accepted).

Any violation returns 400 and persists nothing.

## Responses

### Success Response (201 Created)

```json
{
  "success": true,
  "message": "Menu created successfully",
  "data": {
    "id": "7c1f2b90-4a3d-4e8a-9d5b-2f6e8c0a1d4e",
    "startDate": "2026-10-05",
    "endDate": "2026-10-11",
    "status": "drafted",
    "notes": "High-protein week",
    "createdAt": "2026-10-12T09:00:00Z",
    "updatedAt": "2026-10-12T09:00:00Z",
    "days": [
      {
        "date": "2026-10-05",
        "meals": [
          {
            "mealType": "BREAKFAST",
            "dishId": "9b4d1e77-1f2a-4c5d-8e6f-3a7b2c9d0e11",
            "dishName": "Tofu Scramble",
            "servings": 2
          },
          {
            "mealType": "LUNCH",
            "dishId": "2e8c5a10-9b3d-4f7a-8c1e-6d4b0a9f3e22",
            "dishName": "Chickpea Buddha Bowl",
            "servings": 1
          }
        ]
      }
    ]
  }
}
```

The `data` payload is the menu-detail shape (`MenuDetailResponse`), whose meals
identify each dish by `dishId` and `dishName` only. To read a plan back with
the dishes embedded in full, use `GET /api/menus/details`, which returns a
richer and deliberately different meal shape. Store the returned id and re-read
the menu later with either.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Missing/malformed body fields, `servings` < 1 | Bean-validation or parse message |
| 400 | A structural rule of BR-MENU-005 is violated (wrong dates, duplicate `meal_type`, unknown dish, too few distinct dishes, dish repeated too often) | Clear message naming the rule, e.g. `days must contain exactly the 7 dates from start_date to start_date + 6` |
| 401 | JWT is missing, invalid, or the account is inactive | `Unauthorized` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- BR-MENU-001 — the owner comes from the JWT; no user ID is accepted as a
  parameter.
- BR-MENU-003 — new menus are always `drafted`, so the scheduled-overlap
  constraint is never triggered by creation.
- BR-MENU-005 — the full creation contract (draft status, derived
  `end_date`, 7-day shape, servings, dish existence, variety thresholds).

## Example

### Request

```bash
curl -X POST /api/menus \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "startDate": "2026-10-05",
    "notes": "High-protein week",
    "days": [
      {
        "date": "2026-10-05",
        "meals": [
          { "mealType": "BREAKFAST", "dishId": "9b4d1e77-1f2a-4c5d-8e6f-3a7b2c9d0e11", "servings": 2 },
          { "mealType": "LUNCH", "dishId": "2e8c5a10-9b3d-4f7a-8c1e-6d4b0a9f3e22", "servings": 1 },
          { "mealType": "DINNER", "dishId": "5a3f7c21-7d8e-4b1a-9c4f-8e2d6b0a5c33", "servings": 2 }
        ]
      },
      { "date": "2026-10-06", "meals": [ /* …6 more days… */ ] }
    ]
  }'
```

### Success Response (201)

See the success response above.

---

## Related

- List endpoint: `docs/apis/menus/get-menus.md`
- Detail endpoint: `docs/apis/menus/get-menus-details.md`
- Business Rules: `docs/brs/menus.md`
- ADR: `docs/adrs/010-menu-scheduled-overlap-gist-constraint.md`
- Shared error/response contract: `docs/apis/error-responses.md`
