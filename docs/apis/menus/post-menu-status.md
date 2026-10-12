# API Reference: POST /api/menus/{menuId}/status

## Overview

Change the status of one of the authenticated member's weekly menus —
schedule it, cancel it, or mark it completed. Transitions follow a fixed
legal-transition table; scheduling additionally enforces the one-scheduled-
week-per-user overlap rule.

## Endpoint

```text
POST /api/menus/{menuId}/status
```

## Authentication

Required: a valid JWT access token in the `Authorization` header
(`Bearer <token>`). The user ID is taken from that token; a menu belonging
to another user is indistinguishable from a non-existent one (404).

## Request

### Path Parameters

| Name | Type | Description |
|------|------|-------------|
| menuId | uuid | Identifier of the owned menu whose status changes. |

### Request Body

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| status | string | Yes | Target status: `scheduled`, `cancelled`, or `completed`. `drafted` is never accepted (menus are born drafted). |

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Menu status updated successfully",
  "data": {
    "id": "7c1f2b90-4a3d-4e8a-9d5b-2f6e8c0a1d4e",
    "startDate": "2026-10-05",
    "endDate": "2026-10-11",
    "status": "scheduled",
    "notes": "Prep Sunday",
    "dietaryPreferences": "high-protein",
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

The `data` payload is the menu-detail shape (`MenuDetailResponse`), the same as
`POST /api/menus`; meals identify each dish by `dishId` and `dishName` only.
Read the plan back with dishes embedded via `GET /api/menus/details`, which
returns a richer and deliberately different meal shape.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `status` missing, malformed, or outside the vocabulary (including `drafted`) | Field-level or vocabulary message |
| 404 | `menuId` is unknown or belongs to another user | `Menu not found` |
| 409 | The transition is not in the legal table (BR-MENU-007) | e.g. `Cannot transition a drafted menu to completed` |
| 409 | Scheduling would overlap another scheduled menu of the same user | e.g. `Another scheduled menu already overlaps this week` |
| 401 | JWT is missing, invalid, or the account is inactive | `Unauthorized` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- BR-MENU-001 — the menu is located by id **and** owner from the JWT; a
  foreign id is a 404, never a 403.
- BR-MENU-003 — scheduling enforces one non-overlapping scheduled week
  per user (service pre-check; V30 exclusion constraint is the DB backstop).
- BR-MENU-007 — legal transitions: `drafted → scheduled|cancelled`,
  `scheduled → cancelled|completed`; `cancelled` and `completed` are
  terminal; a no-op (target equals current status) is a 409.

## Example

### Request

```bash
curl -X POST /api/menus/7c1f2b90-4a3d-4e8a-9d5b-2f6e8c0a1d4e/status \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{ "status": "scheduled" }'
```

### Success Response (200)

See the success response above.

---

## Related

- Create endpoint: `docs/apis/menus/post-menus.md`
- Update endpoint: `docs/apis/menus/patch-menus.md`
- List endpoint: `docs/apis/menus/get-menus.md`
- Detail endpoint: `docs/apis/menus/get-menus-details.md`
- Business Rules: `docs/brs/menus.md`
- Shared error/response contract: `docs/apis/error-responses.md`
