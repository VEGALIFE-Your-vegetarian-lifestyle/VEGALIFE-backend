# API Reference: POST /api/admin/restaurants

## Overview

Create a new restaurant for the vegan directory that issues #9, #10, and #11 read from. Admin only (issue #144).

## Endpoint

```text
POST /api/admin/restaurants
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
  "openingHours": {
    "monday": [{"open": "08:00", "close": "22:00"}]
  },
  "websiteUrl": "https://example.com/vegan-garden",
  "rating": 4.8,
  "priceLevel": 2,
  "imageUrls": ["https://example.com/photos/1.jpg"],
  "description": "Cozy all-vegan spot with a daily rotating menu.",
  "dishIds": ["7c9e6679-7425-40de-944b-e07fc1f90ae7"]
}
```

| Field | Type | Required | Description |
|-------|------|----------|--------------|
| name | string | Yes | Restaurant name, at most 255 characters. |
| address | string | Yes | Street address, at most 255 characters. |
| latitude | decimal | Yes | -90 to 90. |
| longitude | decimal | Yes | -180 to 180. |
| phoneNumber | string | No | At most 20 characters. |
| openingHours | object | No | Keyed by lowercase weekday name; see `docs/apis/restaurants/get-restaurants.md` for the exact shape. Must be a JSON object if supplied. |
| websiteUrl | string | No | Must be an absolute `http://` or `https://` URL if supplied. |
| rating | decimal | No | 0.0 to 5.0. |
| priceLevel | integer | No | 1 to 4. |
| imageUrls | array of strings | No | Each entry must be an absolute `http://` or `https://` URL. Omitted → stored as `null` (issue #159). |
| description | string | No | Short editorial blurb. Omitted → stored as `null` (issue #159). |
| dishIds | array of UUIDs | No | Dishes to attach. Duplicates are collapsed. Every id must resolve to an approved, non-deleted dish or the whole request fails with 404 and nothing is persisted. Omitted → no dishes attached (issue #195). |

## Responses

### Success Response (201 Created)

```json
{
  "success": true,
  "message": "Restaurant created successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Vegan Garden",
    "address": "123 Nguyen Hue, District 1",
    "latitude": 10.776889,
    "longitude": 106.700806,
    "phoneNumber": "+84281234567",
    "openingHours": {"monday": [{"open": "08:00", "close": "22:00"}]},
    "websiteUrl": "https://example.com/vegan-garden",
    "rating": 4.8,
    "priceLevel": 2,
    "imageUrls": ["https://example.com/photos/1.jpg"],
    "description": "Cozy all-vegan spot with a daily rotating menu.",
    "dishes": [
      {
        "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
        "name": "Banh Mi Chay",
        "description": "Vietnamese vegan baguette sandwich",
        "imageUrl": null,
        "cuisineType": "Vietnamese"
      }
    ],
    "createdAt": "2026-10-14T10:00:00Z",
    "updatedAt": "2026-10-14T10:00:00Z"
  }
}
```

Unlike the public `GET /api/restaurants` / `GET /api/restaurants/nearby` (issues #9, #10), this admin response includes `createdAt` and `updatedAt`. `dishes` is always an array (empty when no dishes were attached), ordered by `name`, in the same 5-field shape as `GET /api/restaurants/{restaurantId}` (issue #195).

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Missing/blank required field, `priceLevel` outside 1–4, `rating` outside 0.0–5.0, out-of-range coordinates, a non-absolute `websiteUrl`, or an `imageUrls` entry that is not an absolute http/https URL | `Validation failed` |
| 401 | JWT is missing, invalid, or expired | `Unauthorized` |
| 403 | Authenticated but not an Administrator | `Forbidden` |
| 404 | A `dishIds` entry does not resolve to an approved, non-deleted dish | `One or more dishes are not found or are not approved.` |
| 500 | Unexpected server error | `Internal server error` |

Nothing is persisted on a 400 or a 404.

## Business Rules

- Only an Administrator may create a restaurant (BR-ADMIN-003).
- Every successful create writes a `moderation_log` row: `actorId` (the admin), `action = CREATE_RESTAURANT`, `targetType = RESTAURANT`, `targetId` (the new restaurant's id) — BR-ADMIN-002.
- Importing data from external providers and user-submitted restaurants are out of scope (issue #144 non-goals).
- Attaching dishes validates every `dishIds` entry before anything is written: an unknown, soft-deleted, or not-yet-approved dish id fails the whole request with 404, and nothing is persisted (issue #195). Duplicate ids in the request are collapsed before counting.

## Example

```bash
curl -X POST http://localhost:8080/api/admin/restaurants \
  -H "Authorization: Bearer <admin-access-token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Vegan Garden","address":"123 Nguyen Hue, District 1","latitude":10.776889,"longitude":106.700806}'
```

## Related

- Edit a restaurant: `docs/apis/admin/patch-restaurants-restaurantid.md`
- Retire a restaurant: `docs/apis/admin/delete-restaurants-restaurantid.md`
- Public reads: `docs/apis/restaurants/get-restaurants.md`, `docs/apis/restaurants/get-restaurants-nearby.md`, `docs/apis/restaurants/get-restaurants-restaurantid.md`
- Shared error/response contract: `docs/apis/error-responses.md`
