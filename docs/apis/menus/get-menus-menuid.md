# API Reference: GET /api/menus/{menuId}

## Overview

Return one weekly menu owned by the authenticated member, including its days
and the meals planned for each day.

## Endpoint

```text
GET /api/menus/{menuId}
```

## Authentication

Required: a valid JWT access token in the `Authorization` header
(`Bearer <token>`). The menu must belong to the caller.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| menuId | uuid | Yes | Identifier of the menu to return. |

### Query Parameters

None.

### Request Body

No request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Menu retrieved successfully",
  "data": {
    "id": "3f0a9d21-5f6e-4c11-9a8b-1d2e3f4a5b6c",
    "startDate": "2026-10-05",
    "endDate": "2026-10-11",
    "status": "scheduled",
    "notes": "High-protein week",
    "dietaryPreferences": "high-protein, gluten-free",
    "createdAt": "2026-10-01T09:00:00Z",
    "updatedAt": "2026-10-01T09:00:00Z",
    "days": [
      {
        "date": "2026-10-05",
        "meals": [
          {
            "mealType": "BREAKFAST",
            "dishId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
            "dishName": "Tofu scramble",
            "servings": 2
          }
        ]
      }
    ]
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | `true` when the request succeeds. |
| message | string | `Menu retrieved successfully`. |
| data.id | uuid | Menu identifier. |
| data.startDate | date | First day of the menu. |
| data.endDate | date | Last day of the menu (`startDate + 6`). |
| data.status | string | `drafted`, `scheduled`, `cancelled`, or `completed`. |
| data.notes | string or null | Optional free-text note. |
| data.dietaryPreferences | string or null | Dietary preferences recorded for the plan. |
| data.createdAt | string | ISO-8601 creation timestamp. |
| data.updatedAt | string | ISO-8601 last-update timestamp. |
| data.days | array | The menu's days, ordered by date ascending. Empty when the plan has no dishes yet. |
| data.days[].date | date | The calendar day. |
| data.days[].meals | array | Meals planned on that day. An empty array is returned when nothing is planned for the day. |
| data.days[].meals[].mealType | string | `BREAKFAST`, `LUNCH`, or `DINNER`. |
| data.days[].meals[].dishId | uuid | The planned dish identifier. |
| data.days[].meals[].dishName | string | The planned dish name. |
| data.days[].meals[].servings | integer | Number of servings (≥ 1). |

Only days that have at least one meal are included, so `days` is never padded
to seven entries.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `menuId` is not a valid UUID | `Invalid value for parameter 'menuId'` |
| 401 | JWT is missing, invalid, or the account is inactive | `Unauthorized` |
| 404 | The menu does not exist or belongs to another user | `Menu not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- BR-MENU-001 — a foreign menu id returns 404, never 403, so existence does
  not leak.

## Example

### Request

```bash
curl /api/menus/3f0a9d21-5f6e-4c11-9a8b-1d2e3f4a5b6c \
  -H "Authorization: Bearer <accessToken>"
```

### Success Response (200)

```json
{
  "success": true,
  "message": "Menu retrieved successfully",
  "data": {
    "id": "3f0a9d21-5f6e-4c11-9a8b-1d2e3f4a5b6c",
    "startDate": "2026-10-05",
    "endDate": "2026-10-11",
    "status": "scheduled",
    "notes": "High-protein week",
    "dietaryPreferences": "high-protein, gluten-free",
    "createdAt": "2026-10-01T09:00:00Z",
    "updatedAt": "2026-10-01T09:00:00Z",
    "days": [
      {
        "date": "2026-10-05",
        "meals": [
          {
            "mealType": "BREAKFAST",
            "dishId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
            "dishName": "Tofu scramble",
            "servings": 2
          }
        ]
      }
    ]
  }
}
```

---

## Related

- Feature Spec: `docs/feats/query-menus-by-range.md`
- List endpoint: `docs/apis/menus/get-menus.md`
- Business Rules: `docs/brs/menus.md`
- Shared error/response contract: `docs/apis/error-responses.md`
