# API Reference: GET /api/menus

## Overview

Return a paginated list of the authenticated member's weekly menus, optionally
filtered by a time window (a week, a month, or an explicit date range) and by
status.

## Endpoint

```text
GET /api/menus
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
| period | string | No | Time filter selector: `week`, `month`, or `custom`. Omit for no time filter (all own menus). |
| date | date (`YYYY-MM-DD`) | No | Only with `period=week`. Any day in the target ISO week (Monday–Sunday). Defaults to today. |
| month | string (`YYYY-MM`) | No | Only with `period=month`. The target calendar month. Defaults to the current month. |
| from | date (`YYYY-MM-DD`) | Yes when `period=custom` | Inclusive start of the requested range. |
| to | date (`YYYY-MM-DD`) | Yes when `period=custom` | Inclusive end of the requested range; must be ≥ `from`. |
| status | string | No | Filter by menu status: `drafted`, `scheduled`, `cancelled`, or `completed`. Combines with the time filter (AND). |
| page | integer | No | Zero-based page index. Defaults to `0`; must be zero or greater. |
| size | integer | No | Menus per page. Defaults to `20`; must be between `1` and `100`. |
| sort | string | No | Sort as `field,direction`. Defaults to `startDate,desc`. |

A menu is included when its `[startDate, endDate]` window intersects the
requested window (any overlap, inclusive of touching endpoints). Weeks are
resolved Monday–Sunday; both week and month windows are computed in server
(UTC) time.

`from` and `to` are only valid with `period=custom`; supplying them with any
other `period` (or none) is rejected with 400.

### Request Body

No request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Menus retrieved successfully",
  "data": {
    "content": [
      {
        "id": "3f0a9d21-5f6e-4c11-9a8b-1d2e3f4a5b6c",
        "startDate": "2026-10-05",
        "endDate": "2026-10-11",
        "status": "scheduled",
        "notes": "High-protein week",
        "createdAt": "2026-10-01T09:00:00Z",
        "updatedAt": "2026-10-01T09:00:00Z"
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
| success | boolean | `true` when the request succeeds. |
| message | string | `Menus retrieved successfully`. |
| data.content | array | Menus on this page. Empty when nothing matches. |
| data.content[].id | uuid | Menu identifier. |
| data.content[].startDate | date | First day of the menu (a Monday). |
| data.content[].endDate | date | Last day of the menu (`startDate + 6`). |
| data.content[].status | string | `drafted`, `scheduled`, `cancelled`, or `completed`. |
| data.content[].notes | string or null | Optional free-text note. |
| data.content[].createdAt | string | ISO-8601 creation timestamp. |
| data.content[].updatedAt | string | ISO-8601 last-update timestamp. |
| data.page | integer | Zero-based page index returned. |
| data.size | integer | Page size used. |
| data.totalElements | integer | Total matching menus. |
| data.totalPages | integer | Total page count. |
| data.first | boolean | Whether this is the first page. |
| data.last | boolean | Whether this is the last page. |

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `period=custom` without `from`/`to`, malformed `date`/`month`, unknown `period`, `from` > `to`, range > 366 days, or `from`/`to` without `period=custom` | Clear message, e.g. `from and to are required when period=custom` |
| 400 | Invalid `status`, `sort`, `page`, or `size` | `Validation failed` |
| 401 | JWT is missing, invalid, or the account is inactive | `Unauthorized` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- BR-MENU-001 — results are scoped to the authenticated user; no user ID is
  accepted as a parameter.
- BR-MENU-002 — a menu is included when its window intersects the requested
  window (touching endpoints included).
- BR-MENU-004 — week/month windows are resolved in server (UTC) time.

## Example

### Request

```bash
curl "/api/menus?period=custom&from=2026-09-01&to=2026-10-15&status=scheduled" \
  -H "Authorization: Bearer <accessToken>"
```

### Success Response (200)

```json
{
  "success": true,
  "message": "Menus retrieved successfully",
  "data": {
    "content": [
      {
        "id": "3f0a9d21-5f6e-4c11-9a8b-1d2e3f4a5b6c",
        "startDate": "2026-10-05",
        "endDate": "2026-10-11",
        "status": "scheduled",
        "notes": "High-protein week",
        "createdAt": "2026-10-01T09:00:00Z",
        "updatedAt": "2026-10-01T09:00:00Z"
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

---

## Related

- Feature Spec: `docs/feats/query-menus-by-range.md`
- Detail endpoint: `docs/apis/menus/get-menus-menuid.md`
- Business Rules: `docs/brs/menus.md`
- ADR: `docs/adrs/010-menu-scheduled-overlap-gist-constraint.md`
- Shared error/response contract: `docs/apis/error-responses.md`
