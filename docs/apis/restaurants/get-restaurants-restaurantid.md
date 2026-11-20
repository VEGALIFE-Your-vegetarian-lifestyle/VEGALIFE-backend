# API Reference: GET /api/restaurants/{restaurantId}

## Overview

Read one active (non-deleted) restaurant by id, with its curated `dishes` nested in the response (issue #195).

## Endpoint

```text
GET /api/restaurants/{restaurantId}
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`). Any authenticated user may call this endpoint.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| restaurantId | UUID | Yes | Id of the restaurant to read — the `id` returned by `GET /api/restaurants` / `GET /api/restaurants/nearby`. |

### Query Parameters

No query parameters.

### Request Body

No request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Restaurant retrieved successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
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
    "dishes": [
      {
        "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
        "name": "Banh Mi Chay",
        "description": "Vietnamese vegan baguette sandwich",
        "imageUrl": null,
        "cuisineType": "Vietnamese"
      },
      {
        "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "name": "Pho Chay",
        "description": "Vietnamese vegetarian noodle soup",
        "imageUrl": null,
        "cuisineType": "Vietnamese"
      }
    ]
  }
}
```

| Field | Type | Description |
|-------|------|--------------|
| data.id | uuid | Restaurant identifier. |
| data.name | string | Restaurant name. |
| data.address | string | Street address. |
| data.latitude, data.longitude | decimal | Coordinates. |
| data.phoneNumber | string or null | Contact phone number, if present. |
| data.openingHours | object or null | Full stored opening-hours document; see `docs/apis/restaurants/get-restaurants.md`. |
| data.websiteUrl | string or null | Website URL, if present. |
| data.rating | decimal or null | Rating out of 5, or `null` when unrated. |
| data.priceLevel | integer or null | Price level 1–4, or `null` when unset. |
| data.imageUrls | array of strings or null | Curated image gallery, or `null` when unset. |
| data.description | string or null | Short editorial blurb, or `null` when unset. |
| data.dishes | array | Curated dishes attached by an admin, ordered by `name` ascending. Always an array (empty when none attached) for this endpoint. |
| data.dishes[].id | uuid | Dish id. |
| data.dishes[].name | string | Dish name. |
| data.dishes[].description | string or null | Dish description. |
| data.dishes[].imageUrl | string or null | Representative image URL. |
| data.dishes[].cuisineType | string or null | Cuisine classification. |

`createdAt`, `updatedAt`, and `deletedAt` are never included. `data.dishes[]` never includes `moderationStatus` or any other admin/audit field — see `docs/apis/recipes/get-dishes-dishid.md` for the full (larger) dish shape used elsewhere.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `restaurantId` is not a valid UUID | `Validation failed` |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 404 | Restaurant does not exist, or is soft-deleted | `Restaurant not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Only an active (non-deleted) restaurant resolves; a missing id and a soft-deleted id are indistinguishable, both answering `404 "Restaurant not found"`.
- `dishes` is filtered to non-soft-deleted dishes at read time: if a dish attached to this restaurant is later soft-deleted through the admin dish flow, it stops appearing here on the next read, even though the `restaurant_dish` join row is left untouched (issue #195).
- Unlike `GET /api/restaurants` and `GET /api/restaurants/nearby` (which never populate `dishes`), this detail endpoint always includes the array — empty when no dishes are attached.
- Read-only: this endpoint never creates, attaches, or modifies a dish or restaurant.

## Example

```bash
curl -X GET "http://localhost:8080/api/restaurants/550e8400-e29b-41d4-a716-446655440000" \
  -H "Authorization: Bearer <access-token>"
```

### Error Response (404)

```json
{
  "success": false,
  "message": "Restaurant not found",
  "data": null
}
```

## Related

- List: `docs/apis/restaurants/get-restaurants.md`
- Nearby search: `docs/apis/restaurants/get-restaurants-nearby.md`
- Attach dishes on create/update: `docs/apis/admin/post-restaurants.md`, `docs/apis/admin/patch-restaurants-restaurantid.md`
- Data model: `docs/arch/data-dictionary.md` (Restaurant_Dish table, Restaurant M:N Dish relationship)
- Shared error/response contract: `docs/apis/error-responses.md`
