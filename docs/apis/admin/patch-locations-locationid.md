# API Reference: PATCH /api/admin/locations/{locationId}

## Overview

Partially update a non-deleted location: only submitted fields change, `updatedAt` refreshes. Admin only (issue #144).

## Endpoint

```text
PATCH /api/admin/locations/{locationId}
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`), role `ADMIN`.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| locationId | UUID | Yes | ID of the location to edit. |

### Request Body

All fields are optional; omitted or null fields are left unchanged. At least one field must be supplied. Sending a blank string for a text field clears it (e.g. `"phoneNumber": ""` removes the stored phone number).

```json
{
  "name": "Vegan Garden (Relocated)",
  "address": "456 Le Loi, District 1"
}
```

| Field | Type | Description |
|-------|------|-------------|
| name | string | Not blank if supplied, at most 255 characters. |
| address | string | Not blank if supplied, at most 255 characters. |
| latitude | decimal | -90 to 90. |
| longitude | decimal | -180 to 180. |
| phoneNumber | string | At most 20 characters. |
| placeType | string | One of `restaurant`, `grocery`, `cafe`, `market`. |
| openingHours | object | Replaces the whole document; must be a JSON object if supplied. |
| websiteUrl | string | — |
| rating | decimal | 0.0 to 5.0. |
| priceLevel | integer | 1 to 4. |

## Responses

### Success Response (200 OK)

Returns the updated location using the same shape as `POST /api/admin/locations` (see `docs/apis/admin/post-locations.md`), including `createdAt` and `updatedAt`.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | No field supplied, invalid `placeType`, `priceLevel` outside 1–4, `rating` outside 0.0–5.0, out-of-range coordinates, or a blank required text field | `Validation failed` |
| 401 | JWT is missing, invalid, or expired | `Unauthorized` |
| 403 | Authenticated but not an Administrator | `Forbidden` |
| 404 | Location does not exist or is already soft-deleted | `Location not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Only an Administrator may edit a location (BR-ADMIN-003).
- Every successful edit writes a `moderation_log` row: `action = UPDATE_LOCATION`, `targetType = LOCATION` — BR-ADMIN-002.

## Example

```bash
curl -X PATCH http://localhost:8080/api/admin/locations/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer <admin-access-token>" \
  -H "Content-Type: application/json" \
  -d '{"priceLevel": 3}'
```

## Related

- Create a location: `docs/apis/admin/post-locations.md`
- Retire a location: `docs/apis/admin/delete-locations-locationid.md`
- Public reads: `docs/apis/locations/get-locations.md`, `docs/apis/locations/get-locations-nearby.md`
