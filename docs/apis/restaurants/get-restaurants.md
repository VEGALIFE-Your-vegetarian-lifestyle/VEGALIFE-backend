# API Reference: GET /api/restaurants

## Overview

List recommended restaurants with structured attribute filters — rating, price level, and open-now — for the recommendation feed (issue #9), plus an optional keyword filter on name, description, and attached dish names (issue #196). No embeddings or semantic search; distance/radius search is a separate endpoint (issue #10) and never gets a keyword filter.

## Endpoint

```text
GET /api/restaurants
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`). Any authenticated user may call this endpoint.

## Request

### Query Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | integer | No | 0-based page index, default `0`. `< 0` → 400. |
| size | integer | No | Page size, default `20`, range 1–100 (outside → 400). |
| minRating | decimal | No | Minimum rating, 0.0–5.0 inclusive. A restaurant with no stored rating never satisfies this filter. |
| maxPriceLevel | integer | No | Maximum price level, 1–4 inclusive. A restaurant with no stored price level never satisfies this filter. |
| openNow | boolean | No | `true` keeps only restaurants open at the current server time, evaluated from the stored `openingHours` document. A restaurant with no opening hours is excluded when this is `true`. Default `false` (no open-now filtering). |
| keyword | string | No | Case-insensitive substring match (`ILIKE`) against `name`, `description`, or the name of any attached, approved, non-deleted dish (issue #196). Null, empty, or whitespace-only is treated as "no keyword filter". |

Filters combine with AND logic; an unset filter never narrows the results. `keyword` narrows whatever `minRating`/`maxPriceLevel` already matched: a restaurant reachable only through a dish-name match still must independently satisfy `minRating` and `maxPriceLevel`.

### Request Body

No request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Restaurants retrieved successfully",
  "data": {
    "content": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Vegan Garden",
        "address": "123 Nguyen Hue, District 1",
        "latitude": 10.776889,
        "longitude": 106.700806,
        "phoneNumber": "+84281234567",
        "openingHours": {
          "monday": [{"open": "08:00", "close": "22:00"}],
          "tuesday": [{"open": "08:00", "close": "22:00"}]
        },
        "websiteUrl": "https://example.com/vegan-garden",
        "rating": 4.8,
        "priceLevel": 2,
        "imageUrls": ["https://example.com/photos/1.jpg"],
        "description": "Cozy all-vegan spot with a daily rotating menu."
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
| data.content[].id | uuid | Restaurant identifier. |
| data.content[].name | string | Restaurant name. |
| data.content[].address | string | Street address. |
| data.content[].latitude, longitude | decimal | Coordinates. |
| data.content[].phoneNumber | string or null | Contact phone number, if present. |
| data.content[].openingHours | object or null | Full stored opening-hours document (see "Opening hours format" below), or `null` when unset. |
| data.content[].websiteUrl | string or null | Website URL, if present. |
| data.content[].rating | decimal or null | Rating out of 5, or `null` when unrated. |
| data.content[].priceLevel | integer or null | Price level 1–4, or `null` when unset. |
| data.content[].imageUrls | array of strings or null | Curated image gallery, or `null` when unset (issue #159). |
| data.content[].description | string or null | Short editorial blurb, or `null` when unset (issue #159). |

`createdAt`, `updatedAt`, and `deletedAt` are never included in a result row.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `page`/`size` out of range, or `minRating`/`maxPriceLevel` out of range | `Validation failed` |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 500 | Unexpected server error | `Internal server error` |

## Opening hours format

`opening_hours` is stored as `jsonb`: an object keyed by lowercase English weekday name (`monday`..`sunday`), each holding an array of `{"open": "HH:mm", "close": "HH:mm"}` ranges in 24-hour server-local time. A day with no entry or an empty array means closed that day. A range whose `close` is not after its `open` (e.g. `22:00`–`02:00`) is treated as spanning midnight. Multiple ranges per day (split shifts) are supported.

## Business Rules

- Results include only active (non-deleted) restaurants, sorted by `rating` descending (unrated restaurants sort last), then `createdAt` ascending, then `id` ascending, so paging stays stable across requests.
- `openNow=true` is evaluated against the structured filters' matches at request time: a restaurant's `openingHours` is parsed and checked against the server's current day and time. A restaurant without opening hours, or whose hours are present but don't cover the current time, is excluded.
- `keyword` matches if `name` OR `description` OR an attached dish's `name` contains it (case-insensitive substring, not full-text/semantic search). A restaurant is never duplicated in the results just because it matches through more than one of those (e.g. its name and an attached dish both contain the keyword).
- No matches — whether from the filters, `keyword`, or `openNow` — returns an empty `content` array with `200 OK`, never a `404`.
- Creating, editing, or deleting restaurants is out of scope for this endpoint (issue #144, a separate Admin API); a single-restaurant detail endpoint, distance/radius search (issue #10), and semantic ranking are also out of scope (issue #9 non-goals). A curated `imageUrls`/`description` pair is supported (issue #159); reviews and other external provider data remain out of scope. Fuzzy/stemmed matching, autocomplete, and ranking by dish relevance are out of scope (issue #196 non-goals).

## Example

```bash
curl -X GET "http://localhost:8080/api/restaurants?minRating=4.0&maxPriceLevel=2&openNow=true&keyword=pho" \
  -H "Authorization: Bearer <access-token>"
```

## Related

- Admin restaurant management: issue #144 (not yet implemented)
- Distance/radius search: issue #10 (not yet implemented)
- Shared error/response contract: `docs/apis/error-responses.md`
