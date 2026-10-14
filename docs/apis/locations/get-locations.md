# API Reference: GET /api/locations

## Overview

List recommend locations (restaurants, groceries, cafes, markets) with structured attribute filters — rating, place type, price level, and open-now — for the recommendation feed (issue #9). No embeddings or semantic search; distance/radius search is a separate endpoint (issue #10).

## Endpoint

```text
GET /api/locations
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`). Any authenticated user may call this endpoint.

## Request

### Query Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | integer | No | 0-based page index, default `0`. `< 0` → 400. |
| size | integer | No | Page size, default `20`, range 1–100 (outside → 400). |
| minRating | decimal | No | Minimum rating, 0.0–5.0 inclusive. A location with no stored rating never satisfies this filter. |
| placeType | string | No | One of `restaurant`, `grocery`, `cafe`, `market`. Any other value → 400. |
| maxPriceLevel | integer | No | Maximum price level, 1–4 inclusive. A location with no stored price level never satisfies this filter. |
| openNow | boolean | No | `true` keeps only locations open at the current server time, evaluated from the stored `openingHours` document. A location with no opening hours is excluded when this is `true`. Default `false` (no open-now filtering). |

Filters combine with AND logic; an unset filter never narrows the results.

### Request Body

No request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Locations retrieved successfully",
  "data": {
    "content": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Vegan Garden",
        "address": "123 Nguyen Hue, District 1",
        "latitude": 10.776889,
        "longitude": 106.700806,
        "phoneNumber": "+84281234567",
        "placeType": "restaurant",
        "openingHours": {
          "monday": [{"open": "08:00", "close": "22:00"}],
          "tuesday": [{"open": "08:00", "close": "22:00"}]
        },
        "websiteUrl": "https://example.com/vegan-garden",
        "rating": 4.8,
        "priceLevel": 2
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
| data.content[].id | uuid | Location identifier. |
| data.content[].name | string | Location name. |
| data.content[].address | string | Street address. |
| data.content[].latitude, longitude | decimal | Coordinates. |
| data.content[].phoneNumber | string or null | Contact phone number, if present. |
| data.content[].placeType | string | One of `restaurant`, `grocery`, `cafe`, `market`. |
| data.content[].openingHours | object or null | Full stored opening-hours document (see "Opening hours format" below), or `null` when unset. |
| data.content[].websiteUrl | string or null | Website URL, if present. |
| data.content[].rating | decimal or null | Rating out of 5, or `null` when unrated. |
| data.content[].priceLevel | integer or null | Price level 1–4, or `null` when unset. |

`createdAt`, `updatedAt`, and `deletedAt` are never included in a result row.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `page`/`size` out of range, `minRating`/`maxPriceLevel` out of range, or `placeType` not one of the four values | `Validation failed` |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 500 | Unexpected server error | `Internal server error` |

## Opening hours format

`opening_hours` is stored as `jsonb`: an object keyed by lowercase English weekday name (`monday`..`sunday`), each holding an array of `{"open": "HH:mm", "close": "HH:mm"}` ranges in 24-hour server-local time. A day with no entry or an empty array means closed that day. A range whose `close` is not after its `open` (e.g. `22:00`–`02:00`) is treated as spanning midnight. Multiple ranges per day (split shifts) are supported.

## Business Rules

- Results include only active (non-deleted) locations, sorted by `rating` descending (unrated locations sort last), then `createdAt` ascending, then `id` ascending, so paging stays stable across requests.
- `openNow=true` is evaluated against the structured filters' matches at request time: a location's `openingHours` is parsed and checked against the server's current day and time. A location without opening hours, or whose hours are present but don't cover the current time, is excluded.
- No matches — whether from the filters or from `openNow` — returns an empty `content` array with `200 OK`, never a `404`.
- Creating, editing, or deleting locations is out of scope for this endpoint (issue #144, a separate Admin API); a single-location detail endpoint, distance/radius search (issue #10), semantic ranking, and external provider data (photos, reviews) are also out of scope (issue #9 non-goals).

## Example

```bash
curl -X GET "http://localhost:8080/api/locations?minRating=4.0&placeType=cafe&maxPriceLevel=2&openNow=true" \
  -H "Authorization: Bearer <access-token>"
```

## Related

- Admin location management: issue #144 (not yet implemented)
- Distance/radius search: issue #10 (not yet implemented)
- Shared error/response contract: `docs/apis/error-responses.md`
