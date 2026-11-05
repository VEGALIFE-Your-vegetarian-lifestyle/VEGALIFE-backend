# API Reference: GET /api/ingredients

## Overview
List ingredients from the `ingredient` master list, paginated and optionally filtered by name — the suggestion source for the create-recipe form's ingredient autocomplete.

## Endpoint
```
GET /api/ingredients
```

## Authentication
JWT Bearer token required. A missing, expired, or invalid token returns `401 Unauthorized`. (Unlike `GET /api/categories`, this endpoint is not public.)

## Request

### Path Parameters
None.

### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | integer | No | 0-based page index, default 0. `< 0` → 400 |
| size | integer | No | Page size, default 20, range 1–100 (outside → 400) |
| sort | string | No | Format `property,direction`. Default `name,asc` |
| name | string | No | Case-insensitive substring filter on ingredient name |

### Request Body
No request body

## Responses

### Success Response (200)
```json
{
  "success": true,
  "message": "Ingredients retrieved successfully",
  "data": {
    "content": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "tofu",
        "unit": "G",
        "calories": 0.740000,
        "proteinG": 0.080000,
        "carbohydrateG": 0.019000,
        "fatG": 0.048000,
        "fiberG": 0.003000,
        "nutritionResolved": true,
        "createdAt": "2026-10-01T09:00:00Z"
      },
      {
        "id": "6f9619ff-8b86-d011-b42d-00c04fc964ff",
        "name": "tomato",
        "unit": null,
        "calories": null,
        "proteinG": null,
        "carbohydrateG": null,
        "fatG": null,
        "fiberG": null,
        "nutritionResolved": false,
        "createdAt": "2026-10-01T09:05:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

| Field | Type | Description |
|-------|------|--------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data.content | array | Ingredients on this page, sorted per `sort` (default `name` ascending) |
| data.content[].id | uuid | Ingredient id — the same id `POST /api/recipes` find-or-create resolves to |
| data.content[].name | string | Ingredient name as stored (recipe creation normalizes to lowercase) |
| data.content[].unit | string \| null | `G`, `ML` or `PIECE`; `null` while the row's unit is undecided |
| data.content[].calories | number \| null | kcal per **one** `unit` |
| data.content[].proteinG | number \| null | grams of protein per one `unit` |
| data.content[].carbohydrateG | number \| null | grams of carbohydrate per one `unit` |
| data.content[].fatG | number \| null | grams of fat per one `unit` |
| data.content[].fiberG | number \| null | grams of fibre per one `unit` |
| data.content[].nutritionResolved | boolean | `true` only when `unit` and all five values are present |
| data.content[].createdAt | string | ISO-8601 instant |
| data.page | integer | Current page index |
| data.size | integer | Page size |
| data.totalElements | long | Total matching ingredients across all pages |
| data.totalPages | integer | Total number of pages |
| data.first | boolean | Whether this is the first page |
| data.last | boolean | Whether this is the last page |

**`unit` is required to interpret a recipe amount.** `POST /api/recipes` ignores a
unit supplied for an ingredient that already exists and reads the `amount` in the
stored row's unit (see [POST /api/recipes](../recipes/post-recipes.md)). Without
this field a client cannot know what its amount will mean: `{"name":"tomato",
"amount":2,"unit":"quả"}` against a row curated as `G` silently creates 2 g of
tomato, not two tomatoes. Read `unit` here and render the amount in it.

The five nutrition values describe **one** unit, exactly as stored — they are not a
label for a finished dish and not a whole-recipe total. Multiply by the amount being
submitted to preview a line, or read the derived totals from the recipe responses
instead.

`null` means "not curated yet" and is never `0`: water is legitimately 0 kcal. All
five are `null` together whenever `nutritionResolved` is `false` — the API never
reports a partial set. These fields are always present in the JSON; they are not
omitted when null.

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `page < 0` or `size` outside 1–100 | "Validation failed" |
| 400 | Malformed `sort` (more than one comma part) | "Sort must be in the form property,asc\|desc" |
| 400 | Blank `sort` property (e.g. `sort=,asc`) | "Sort property must not be blank" |
| 401 | Missing, expired, or invalid JWT | "Unauthorized" |
| 500 | Server error, including `sort` naming an unknown property (same behaviour as `GET /api/categories`) | "Internal server error" |

## Business Rules
- The endpoint is read-only: it never creates, updates, or deletes an `ingredient` row.
- `name` matches as a case-insensitive substring (`name=tof` matches "Tofu", "tofu", "Sweet Tofu").
- The `ingredient` table has no `deleted_at` column (see `docs/arch/data-dictionary.md`, Table 11), so there is no soft-delete filter — every row is listable.
- Ordering is stable across pages: names are unique modulo case (`UNIQUE INDEX idx_ingredient_name_unique ON ingredient (lower(name))`, migration `V22`), so `name,asc` is a strict total order and no row can repeat or be skipped between pages.
- An empty table returns `200` with `content: []` and `totalElements: 0` — never `404`.

## Example

### Request
```bash
curl -X GET "http://localhost:8080/api/ingredients?name=tof&page=0&size=20" \
  -H "Authorization: Bearer <access-token>"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Ingredients retrieved successfully",
  "data": {
    "content": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "tofu",
        "unit": "G",
        "calories": 0.740000,
        "proteinG": 0.080000,
        "carbohydrateG": 0.019000,
        "fatG": 0.048000,
        "fiberG": 0.003000,
        "nutritionResolved": true,
        "createdAt": "2026-10-01T09:00:00Z"
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

## Related
- Feature Spec: `docs/feats/list-ingredients-api.md`
- Exposure rationale: `docs/feats/nutrition-read-surfaces.md`, ADR-022 decisions 10–11
- Consumer: `docs/apis/recipes/post-recipes.md` (ingredient names → find-or-create)
- Paging contract: `docs/apis/post/get-categories.md` (`GET /api/categories`, same `PageResponse` shape)
