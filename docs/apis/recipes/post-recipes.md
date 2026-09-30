# API Reference: POST /api/recipes

## Overview

Create a recipe for the currently authenticated user: the recipe's dish and ingredient names are normalized to lowercase and resolved against shared `dish` / `ingredient` rows (created on first use).

## Endpoint

```text
POST /api/recipes
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`). The recipe owner is taken from the token; the request cannot choose a user ID.

## Request

### Path Parameters

No path parameters.

### Query Parameters

No query parameters.

### Request Body

```json
{
  "name": "Spicy tofu stir-fry",
  "dishName": "Tofu Stir-Fry",
  "description": "A quick weeknight dinner.",
  "instructions": "Press the tofu. Stir-fry with vegetables and sauce.",
  "prepTimeMinutes": 10,
  "cookTimeMinutes": 15,
  "servings": 2,
  "difficulty": "EASY",
  "ingredients": [
    { "name": "Tofu", "amount": 200, "unit": "g" },
    { "name": "Soy Sauce", "amount": 2, "unit": "tbsp" }
  ]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| name | string | Yes | Recipe name. Must not be blank; max 255 characters. Stored as submitted (not lowercased). |
| dishName | string | Yes | Dish this recipe belongs to. Trimmed and lowercased before resolving/creating the shared `dish` row; max 255 characters. |
| instructions | string | Yes | Cooking instructions. Must not be blank. |
| servings | integer | Yes | Number of servings; must be at least 1. |
| ingredients | array | Yes | Ingredient entries; at least one and at most 50. Entry names must be unique within the request (case-insensitive). |
| ingredients[].name | string | Yes (per entry) | Ingredient name. Trimmed and lowercased before resolving/creating the shared `ingredient` row; max 100 characters. |
| ingredients[].amount | number | Yes (per entry) | Quantity of the ingredient; must be greater than 0 (up to 3 decimal places). |
| ingredients[].unit | string | Yes (per entry) | Unit of measure (e.g. `g`, `tbsp`); must not be blank; max 30 characters. |
| description | string or null | No | Optional recipe description. |
| prepTimeMinutes | integer or null | No | Preparation time in minutes; must be at least 0 if supplied. |
| cookTimeMinutes | integer or null | No | Cooking time in minutes; must be at least 0 if supplied. |
| difficulty | string or null | No | `EASY`, `MEDIUM`, or `HARD`. |

The client must not send `userId`, `dishId`, `id`, or timestamps. The server sets these values; any such fields are ignored.

## Responses

### Success Response (201 Created)

```json
{
  "success": true,
  "message": "Recipe created successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "userId": "6c1f1a3e-1f0a-4f6e-9a4a-6d2d5e77aa11",
    "dishId": "9b2d8e11-2c33-4f5a-8f1d-0a1b2c3d4e5f",
    "dishName": "tofu stir-fry",
    "name": "Spicy tofu stir-fry",
    "description": "A quick weeknight dinner.",
    "instructions": "Press the tofu. Stir-fry with vegetables and sauce.",
    "prepTimeMinutes": 10,
    "cookTimeMinutes": 15,
    "servings": 2,
    "difficulty": "EASY",
    "ingredients": [
      { "ingredientId": "1a2b3c4d-0001-4000-8000-000000000001", "name": "tofu", "amount": 200, "unit": "g" },
      { "ingredientId": "1a2b3c4d-0002-4000-8000-000000000002", "name": "soy sauce", "amount": 2, "unit": "tbsp" }
    ],
    "createdAt": "2026-10-01T10:00:00Z",
    "updatedAt": "2026-10-01T10:00:00Z"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | `true` when the request succeeds. |
| message | string | `Recipe created successfully`. |
| data.id | uuid | Identifier generated for the new recipe; referenceable by the future post↔recipe linking endpoint. |
| data.userId | uuid | Owner, taken from the authenticated JWT. |
| data.dishId | uuid | Resolved shared dish row; never null. |
| data.dishName | string | Dish name after lowercasing (e.g. request `Tofu Stir-Fry` → `tofu stir-fry`). |
| data.name | string | Recipe name as submitted. |
| data.description | string or null | Optional description, if supplied. |
| data.instructions | string | Cooking instructions. |
| data.prepTimeMinutes | integer or null | Preparation time, if supplied. |
| data.cookTimeMinutes | integer or null | Cooking time, if supplied. |
| data.servings | integer | Number of servings. |
| data.difficulty | string or null | `EASY`, `MEDIUM`, `HARD`, or `null`. |
| data.ingredients | array | One entry per ingredient link, with the resolved `ingredientId` and the lowercased `name`. |
| data.createdAt / data.updatedAt | string | Timestamps in ISO-8601 format. |

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Missing/blank required field, `servings < 1`, `amount <= 0`, unknown `difficulty`, overlong `name`/`dishName`/ingredient `name`/`unit`, empty or case-duplicate ingredient list, or more than 50 ingredients | `Validation failed` or the specific rule message |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- The owner is always the user identified by the authenticated JWT (BR-RECP-001).
- `dishName` and every ingredient `name` are trimmed and lowercased before lookup; the response echoes the normalized names (BR-RECP-002).
- Names are unique case-insensitively at the database level: unique index on `ingredient (lower(name))`, partial unique index on `dish (lower(name)) WHERE deleted_at IS NULL` (migration `V22`). A soft-deleted dish's name is free to reuse; a concurrent race is resolved by reusing the winning row (BR-RECP-002).
- `name`, `instructions`, `servings`, and `dishName` are required (BR-RECP-003).
- New recipes are persisted with `user_id` from the token and `dish_id` resolved; soft-delete timestamps start as `null`.

## Example

```bash
curl -X POST http://localhost:8080/api/recipes \
  -H "Authorization: Bearer <access-token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Spicy tofu stir-fry","dishName":"Tofu Stir-Fry","instructions":"Press the tofu. Stir-fry with vegetables and sauce.","servings":2,"ingredients":[{"name":"Tofu","amount":200,"unit":"g"}]}'
```

## Related

- Feature spec: `docs/feats/create-recipe-api.md`
- Business rules: `docs/brs/recipes.md`
- Database schema: `src/main/resources/db/migration/V6__create_ingredient_recipe_tables.sql`, `V5__create_dish_table.sql`
- Uniqueness index: `src/main/resources/db/migration/V22__add_ingredient_dish_name_unique.sql`
- Driving issue: https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/84
