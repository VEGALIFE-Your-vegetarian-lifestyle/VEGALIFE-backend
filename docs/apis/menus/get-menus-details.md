# API Reference: GET /api/menus/details

## Overview

Return the authenticated member's menus intersecting a date range, each with
its full day-by-day meal detail and embedded dish information, in a single
call. Accepts a `menuId` to read exactly one menu's meals.

This endpoint replaces `GET /api/menus/{menuId}`, which no longer serves
`GET` (the path still backs `PATCH /api/menus/{menuId}` and
`POST /api/menus/{menuId}/status`, so a `GET` there returns `405 Method Not
Allowed`), and is the backing endpoint for the MCP `get_meal_plan` tool.

## Endpoint

```text
GET /api/menus/details
```

## Authentication

Required: a valid JWT access token in the `Authorization` header
(`Bearer <token>`). The user ID is taken from that token, so callers cannot
request another user's menus.

## Request

### Path Parameters

No path parameters.

### Query Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| from | date (`YYYY-MM-DD`) | No | Inclusive start of the requested range. Optional on its own. |
| to | date (`YYYY-MM-DD`) | No | Inclusive end of the requested range. Optional on its own; must be ≥ `from`. |
| menuId | uuid | No | Read exactly one owned menu's detail. When supplied, `from` and `to` are ignored. |

No request body.

#### Window resolution

- `menuId` supplied → that one menu, regardless of `from`/`to`.
- Neither `from` nor `to` supplied → the current ISO week (Monday–Sunday),
  computed in server (UTC) time.
- Either or both supplied → the intersection window, each bound applied only
  when present.

A menu is included when its `[startDate, endDate]` window intersects the
requested window, inclusive of touching endpoints. The range must not exceed
366 days.

There is no `status` filter on this endpoint: every matching menu is returned
and carries its own `status` field, so a caller can distinguish a `drafted`
plan from a `scheduled` one without a second query. Use
`GET /api/menus?status=` when a status-filtered, paginated list is wanted.

### Request Body

No request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Meal plan retrieved successfully",
  "data": [
    {
      "id": "3f0a9d21-5f6e-4c11-9a8b-1d2e3f4a5b6c",
      "startDate": "2026-10-05",
      "endDate": "2026-10-11",
      "status": "scheduled",
      "notes": "High-protein week",
      "createdAt": "2026-10-01T09:00:00Z",
      "updatedAt": "2026-10-01T09:00:00Z",
      "days": [
        {
          "date": "2026-10-05",
          "meals": [
            {
              "mealType": "BREAKFAST",
              "servings": 2,
              "dish": {
                "id": "9b1e7c44-2d55-4f8a-9c31-77aa0c6b1e10",
                "name": "Tofu scramble",
                "description": "Crumbed firm tofu with turmeric and black salt",
                "imageUrl": "https://cdn.example.com/dishes/tofu-scramble.jpg",
                "cuisineType": "Asian"
              }
            }
          ]
        }
      ]
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | `true` when the request succeeds. |
| message | string | `Meal plan retrieved successfully`. |
| data | array | Matching menus, ordered by `startDate` ascending. Empty when nothing matches. |
| data[].id | uuid | Menu identifier. |
| data[].startDate | date | First day of the menu (a Monday). |
| data[].endDate | date | Last day of the menu (`startDate + 6`). |
| data[].status | string | `drafted`, `scheduled`, `cancelled`, or `completed`. |
| data[].notes | string or null | Optional free-text note. |
| data[].createdAt | string | ISO-8601 creation timestamp. |
| data[].updatedAt | string | ISO-8601 last-update timestamp. |
| data[].days | array | Days that carry at least one meal. A menu with no `menu_detail` rows yields an empty array. |
| data[].days[].date | date | The day this entry covers. |
| data[].days[].meals | array | Meals for that day, ordered by `mealType`. |
| data[].days[].meals[].mealType | string | `BREAKFAST`, `LUNCH`, or `DINNER`. |
| data[].days[].meals[].servings | integer | Number of servings; ≥ 1. |
| data[].days[].meals[].dish | object | The dish this meal references, embedded in full. |
| data[].days[].meals[].dish.id | uuid | Dish identifier. |
| data[].days[].meals[].dish.name | string | Dish name. |
| data[].days[].meals[].dish.description | string or null | Free-text description. |
| data[].days[].meals[].dish.imageUrl | string or null | Image URL. |
| data[].days[].meals[].dish.cuisineType | string or null | Free-text cuisine type. |

`data` is a plain array, not a page object — this endpoint is not paginated.
`GET /api/menus` remains the paginated, summary-only read.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Malformed `from`/`to`, `from` > `to`, range > 366 days | Clear message, e.g. `from must be on or before to` |
| 400 | `menuId` present but not a valid UUID | `Validation failed` |
| 401 | JWT is missing, invalid, or the account is inactive | `Unauthorized` |
| 404 | `menuId` does not exist or belongs to another user | `Menu not found` |
| 500 | Unexpected server error | `Internal server error` |

Note the 404 rather than 403 for a foreign `menuId`: a menu belonging to
another user is indistinguishable from one that does not exist (BR-MENU-001).

## Business Rules

- BR-MENU-001 — results are scoped to the authenticated user; no user ID is
  accepted as a parameter, and a foreign `menuId` is a 404.
- BR-MENU-002 — a menu is included when its window intersects the requested
  window (touching endpoints included).
- BR-MENU-004 — the default week is resolved in server (UTC) time.

## Example

### Request

```bash
curl "/api/menus/details?from=2026-09-01&to=2026-10-15" \
  -H "Authorization: Bearer <accessToken>"
```

### Request — one menu's meals

```bash
curl "/api/menus/details?menuId=3f0a9d21-5f6e-4c11-9a8b-1d2e3f4a5b6c" \
  -H "Authorization: Bearer <accessToken>"
```

### Success Response (200)

```json
{
  "success": true,
  "message": "Meal plan retrieved successfully",
  "data": [
    {
      "id": "3f0a9d21-5f6e-4c11-9a8b-1d2e3f4a5b6c",
      "startDate": "2026-10-05",
      "endDate": "2026-10-11",
      "status": "scheduled",
      "notes": "High-protein week",
      "createdAt": "2026-10-01T09:00:00Z",
      "updatedAt": "2026-10-01T09:00:00Z",
      "days": [
        {
          "date": "2026-10-05",
          "meals": [
            {
              "mealType": "BREAKFAST",
              "servings": 2,
              "dish": {
                "id": "9b1e7c44-2d55-4f8a-9c31-77aa0c6b1e10",
                "name": "Tofu scramble",
                "description": "Crumbed firm tofu with turmeric and black salt",
                "imageUrl": "https://cdn.example.com/dishes/tofu-scramble.jpg",
                "cuisineType": "Asian"
              }
            },
            {
              "mealType": "LUNCH",
              "servings": 1,
              "dish": {
                "id": "c4d2f1a8-6b03-4e77-8a15-2f9d5b0c3a44",
                "name": "Chickpea salad",
                "description": "Chickpeas, cucumber, lemon and dill",
                "imageUrl": null,
                "cuisineType": "Mediterranean"
              }
            }
          ]
        }
      ]
    }
  ]
}
```

### Success Response (200) — nothing matches

```json
{
  "success": true,
  "message": "Meal plan retrieved successfully",
  "data": []
}
```

---

## Related

- Feature Spec: `docs/feats/meal-plan-detail-query.md`
- Menu list: `docs/apis/menus/get-menus.md`
- MCP tool contract: `docs/apis/mcp/post-mcp.md`
- Business Rules: `docs/brs/menus.md`
- Shared error/response contract: `docs/apis/error-responses.md`
