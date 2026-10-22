# API Reference: POST /api/admin/locations

## Overview

Create a new location (restaurant, grocery, cafe, or market) for the vegan directory that issues #9, #10, and #11 read from. Admin only (issue #144).

## Endpoint

```text
POST /api/admin/locations
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`), role `ADMIN`.

## Request

### Request Body

```json
{
  "name": "Vegan Garden",
  "address": "123 Nguyen Hue, District 1",
  "latitude": 10.776889,
  "longitude": 106.700806,
  "phoneNumber": "+84281234567",
  "placeType": "restaurant",
  "openingHours": {
    "monday": [{"open": "08:00", "close": "22:00"}]
  },
  "websiteUrl": "https://example.com/vegan-garden",
  "rating": 4.8,
  "priceLevel": 2,
  "imageUrls": ["https://example.com/photos/1.jpg"],
  "description": "Cozy all-vegan spot with a daily rotating menu."
}
```

| Field | Type | Required | Description |
|-------|------|----------|--------------|
| name | string | Yes | Location name, at most 255 characters. |
| address | string | Yes | Street address, at most 255 characters. |
| latitude | decimal | Yes | -90 to 90. |
| longitude | decimal | Yes | -180 to 180. |
| phoneNumber | string | No | At most 20 characters. |
| placeType | string | Yes | One of `restaurant`, `grocery`, `cafe`, `market`. |
| openingHours | object | No | Keyed by lowercase weekday name; see `docs/apis/locations/get-locations.md` for the exact shape. Must be a JSON object if supplied. |
| websiteUrl | string | No | Must be an absolute `http://` or `https://` URL if supplied. |
| rating | decimal | No | 0.0 to 5.0. |
| priceLevel | integer | No | 1 to 4. |
| imageUrls | array of strings | No | Each entry must be an absolute `http://` or `https://` URL. Omitted → stored as `null` (issue #159). |
| description | string | No | Short editorial blurb. Omitted → stored as `null` (issue #159). |

## Responses

### Success Response (201 Created)

```json
{
  "success": true,
  "message": "Location created successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Vegan Garden",
    "address": "123 Nguyen Hue, District 1",
    "latitude": 10.776889,
    "longitude": 106.700806,
    "phoneNumber": "+84281234567",
    "placeType": "restaurant",
    "openingHours": {"monday": [{"open": "08:00", "close": "22:00"}]},
    "websiteUrl": "https://example.com/vegan-garden",
    "rating": 4.8,
    "priceLevel": 2,
    "imageUrls": ["https://example.com/photos/1.jpg"],
    "description": "Cozy all-vegan spot with a daily rotating menu.",
    "createdAt": "2026-10-14T10:00:00Z",
    "updatedAt": "2026-10-14T10:00:00Z"
  }
}
```

Unlike the public `GET /api/locations` / `GET /api/locations/nearby` (issues #9, #10), this admin response includes `createdAt` and `updatedAt`.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Missing/blank required field, invalid `placeType`, `priceLevel` outside 1–4, `rating` outside 0.0–5.0, out-of-range coordinates, a non-absolute `websiteUrl`, or an `imageUrls` entry that is not an absolute http/https URL | `Validation failed` |
| 401 | JWT is missing, invalid, or expired | `Unauthorized` |
| 403 | Authenticated but not an Administrator | `Forbidden` |
| 500 | Unexpected server error | `Internal server error` |

Nothing is persisted on a 400.

## Business Rules

- Only an Administrator may create a location (BR-ADMIN-003).
- Every successful create writes a `moderation_log` row: `actorId` (the admin), `action = CREATE_LOCATION`, `targetType = LOCATION`, `targetId` (the new location's id) — BR-ADMIN-002.
- Importing data from external providers and user-submitted locations are out of scope (issue #144 non-goals).

## Example

```bash
curl -X POST http://localhost:8080/api/admin/locations \
  -H "Authorization: Bearer <admin-access-token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Vegan Garden","address":"123 Nguyen Hue, District 1","latitude":10.776889,"longitude":106.700806,"placeType":"restaurant"}'
```

## Related

- Edit a location: `docs/apis/admin/patch-locations-locationid.md`
- Retire a location: `docs/apis/admin/delete-locations-locationid.md`
- Public reads: `docs/apis/locations/get-locations.md`, `docs/apis/locations/get-locations-nearby.md`
- Shared error/response contract: `docs/apis/error-responses.md`
