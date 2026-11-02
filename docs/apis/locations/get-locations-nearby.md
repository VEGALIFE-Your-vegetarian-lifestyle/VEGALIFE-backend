# API Reference: GET /api/locations/nearby

## Overview

List every active location within a radius of a given point, nearest first, with no attribute filters and no pagination (issue #10). A sibling of `GET /api/locations` (issue #9): same row shape, plus a computed `distance`.

## Endpoint

```text
GET /api/locations/nearby
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`). Any authenticated user may call this endpoint.

## Request

### Query Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| lat | decimal | Yes | Search centre latitude, -90 to 90. Missing or out of range → 400. |
| lng | decimal | Yes | Search centre longitude, -180 to 180. Missing or out of range → 400. |
| radius | decimal | Yes | Search radius in metres. Must be greater than 0; missing or non-positive → 400. |

There are no `page`/`size` parameters — every matching location is returned in one response.

### Request Body

No request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Nearby locations retrieved successfully",
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Vegan Garden",
      "address": "123 Nguyen Hue, District 1",
      "latitude": 10.777529,
      "longitude": 106.695528,
      "phoneNumber": "+84281234567",
      "placeType": "restaurant",
      "openingHours": {
        "monday": [{"open": "08:00", "close": "22:00"}]
      },
      "websiteUrl": "https://example.com/vegan-garden",
      "rating": 4.8,
      "priceLevel": 2,
      "imageUrls": ["https://example.com/photos/1.jpg"],
      "description": "Cozy all-vegan spot with a daily rotating menu.",
      "distance": 620.4
    }
  ]
}
```

`data` is a plain array, not a page envelope. Every field matches `GET /api/locations`' `LocationResponse` shape (see `docs/apis/locations/get-locations.md`), plus:

| Field | Type | Description |
|-------|------|-------------|
| data[].distance | decimal | Great-circle distance from (`lat`, `lng`) to this location, in metres. Never stored — computed per request. |

`createdAt`, `updatedAt`, and `deletedAt` are never included, same as `GET /api/locations`.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `lat`/`lng`/`radius` missing, `lat`/`lng` out of range, or `radius` not positive | `Validation failed` |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Results include only active (non-deleted) locations within `radius` metres of (`lat`, `lng`), sorted by distance ascending.
- The query is a bounding-box pre-filter over the existing `idx_location_coords` index (no new geospatial dependency — no PostGIS, no Redis GEO); the exact radius cut and the returned `distance` are both computed afterward with the haversine formula.
- No matches returns an empty array with `200 OK`, never a `404`.
- Attribute filters (rating, type, price, open-now — issue #9), pagination, and a "vegan-friendly score" are out of scope for this endpoint (issue #10 non-goals). A curated `imageUrls`/`description` pair is supported (issue #159); reviews and menu highlights remain out of scope.
- The search centre is always the request's `lat`/`lng`; the user's own coordinates are never read or stored.

## Example

```bash
curl -X GET "http://localhost:8080/api/locations/nearby?lat=10.772465&lng=106.698055&radius=2000" \
  -H "Authorization: Bearer <access-token>"
```

## Related

- Attribute-filtered list: `docs/apis/locations/get-locations.md`
- Admin location management: issue #144 (not yet implemented)
- Shared error/response contract: `docs/apis/error-responses.md`
