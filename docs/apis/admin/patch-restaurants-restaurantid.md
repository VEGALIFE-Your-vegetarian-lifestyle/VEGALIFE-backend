# API Reference: PATCH /api/admin/restaurants/{restaurantId}

## Overview

Partially update a non-deleted restaurant: only submitted fields change, `updatedAt` refreshes. Admin only (issue #144).

## Endpoint

```text
PATCH /api/admin/restaurants/{restaurantId}
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`), role `ADMIN`.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| restaurantId | UUID | Yes | ID of the restaurant to edit. |

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
| openingHours | object | Replaces the whole document; must be a JSON object if supplied. |
| websiteUrl | string | Must be an absolute `http://` or `https://` URL if supplied; blank clears it. |
| rating | decimal | 0.0 to 5.0. |
| priceLevel | integer | 1 to 4. |
| imageUrls | array of strings | Each entry must be an absolute `http://` or `https://` URL. `[]` clears the gallery; omitted/null leaves it unchanged (issue #159). |
| description | string | Blank clears it to `null`; omitted/null leaves it unchanged (issue #159). |
| dishIds | array of UUIDs | Replace semantics (issue #195): omitted/`null` leaves attached dishes unchanged; `[]` removes all attached dishes; a non-empty array replaces the whole set. Duplicates are collapsed. Every id must resolve to an approved, non-deleted dish or the whole request fails with 404 and nothing changes. |

## Responses

### Success Response (200 OK)

Returns the updated restaurant using the same shape as `POST /api/admin/restaurants` (see `docs/apis/admin/post-restaurants.md`), including `createdAt`, `updatedAt`, and the nested `dishes` array.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | No field supplied, `priceLevel` outside 1–4, `rating` outside 0.0–5.0, out-of-range coordinates, a blank required text field, a non-absolute `websiteUrl`, or an `imageUrls` entry that is not an absolute http/https URL | `Validation failed` |
| 401 | JWT is missing, invalid, or expired | `Unauthorized` |
| 403 | Authenticated but not an Administrator | `Forbidden` |
| 404 | Restaurant does not exist or is already soft-deleted | `Restaurant not found` |
| 404 | A `dishIds` entry does not resolve to an approved, non-deleted dish | `One or more dishes are not found or are not approved.` |
| 500 | Unexpected server error | `Internal server error` |

Nothing changes on a 400 or a 404 — including the restaurant's existing attached dishes when `dishIds` itself is the failing field.

## Business Rules

- Only an Administrator may edit a restaurant (BR-ADMIN-003).
- Every successful edit writes a `moderation_log` row: `action = UPDATE_RESTAURANT`, `targetType = RESTAURANT` — BR-ADMIN-002.
- `dishIds` follows the same omitted/null-vs-empty-vs-non-empty convention as `imageUrls` (issue #159), extended to replace-the-whole-set semantics: omitted/null never touches attached dishes, `[]` detaches all of them, a non-empty array replaces the set after validating every id (issue #195).

## Example

```bash
curl -X PATCH http://localhost:8080/api/admin/restaurants/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer <admin-access-token>" \
  -H "Content-Type: application/json" \
  -d '{"priceLevel": 3}'
```

## Related

- Create a restaurant: `docs/apis/admin/post-restaurants.md`
- Retire a restaurant: `docs/apis/admin/delete-restaurants-restaurantid.md`
- Public reads: `docs/apis/restaurants/get-restaurants.md`, `docs/apis/restaurants/get-restaurants-nearby.md`, `docs/apis/restaurants/get-restaurants-restaurantid.md`
