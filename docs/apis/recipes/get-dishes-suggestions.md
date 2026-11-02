# API Reference: GET /api/dishes/suggestions

## Overview
Suggests dishes that can actually be planned for the caller, each with up to
`recipesPerDish` recommended recipes and their nutrition. Unlike `GET
/api/dishes`, which lists the catalogue, this is a suggestion surface: a dish
appears only when at least one of its approved recipes passes the caller's
stored-allergy check *and* the supplied recipe filters. Requires
authentication.

Allergies always come from the caller's own profile. There is no allergen
parameter — by design, so the filter cannot be forgotten or forged.

## Endpoint
```
GET /api/dishes/suggestions
```

## Authentication
JWT Bearer required. Requests without a valid token get 401.

## Request

### Path Parameters
No path parameters

### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| name | string | No | Case-insensitive substring filter on dish name |
| cuisineType | string | No | Cuisine type, exact and case-insensitive (`Vietnamese`, not `viet`) |
| difficulty | string | No | Recipe difficulty: `EASY`, `MEDIUM`, `HARD`. Case-insensitive |
| maxPrepTime | integer | No | Only recipes whose prep time is at most this many minutes |
| maxCookTime | integer | No | Only recipes whose cook time is at most this many minutes |
| maxTotalTime | integer | No | Only recipes whose prep + cook time is at most this many minutes |
| minCalories | decimal | No | Only recipes with at least this many calories (whole recipe) |
| maxCalories | decimal | No | Only recipes with at most this many calories (whole recipe) |
| minProtein | decimal | No | Only recipes with at least this many grams of protein (whole recipe) |
| page | integer | No | 0-based page index, default 0 |
| size | integer | No | Dishes per page, default 20, min 1, max 50 |
| recipesPerDish | integer | No | Recipes returned per dish, default 3, min 1, max 10 |

### Request Body
No request body

Behaviour of the recipe filters:

- They narrow **recipes**, not dishes. The same predicate decides whether a
  dish qualifies (it needs at least one surviving recipe) and which recipes are
  recommended, so a dish is never shown for a recipe that was filtered out of
  its own list.
- Nutrition filters are strict. A recipe whose value is unknown does **not**
  pass a bound: unknown is not the same as zero.
- `maxTotalTime` counts a missing prep or cook time as 0, so a recipe with no
  times recorded can still satisfy it. `maxPrepTime`/`maxCookTime` require the
  value to be present.
- There is no `sort` parameter. Ordering is fixed (`dish name` ascending), so a
  request cannot name a nonexistent property.

## Responses

### Success Response (200)
```json
{
  "success": true,
  "message": "Dish suggestions retrieved successfully",
  "data": {
    "content": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "pho chay",
        "description": "Vietnamese vegetarian noodle soup",
        "imageUrl": null,
        "cuisineType": "Vietnamese",
        "recommendedRecipes": [
          {
            "id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
            "name": "Pho chay with tofu",
            "description": "Brothy noodle soup",
            "prepTimeMinutes": 20,
            "cookTimeMinutes": 40,
            "servings": 2,
            "difficulty": "EASY",
            "calories": 420.00,
            "proteinG": 21.50,
            "carbohydrateG": 55.00,
            "fatG": 9.00,
            "fiberG": 6.00
          }
        ]
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
|-------|------|--------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data.content | array | Suggested dishes on this page, ordered by name |
| data.content[].id | uuid | Dish id — usable directly in `POST /api/menus` |
| data.content[].name | string | Canonical dish name |
| data.content[].description | string \| null | Dish description |
| data.content[].imageUrl | string \| null | Representative image URL |
| data.content[].cuisineType | string \| null | Cuisine classification |
| data.content[].recommendedRecipes | array | At least one, at most `recipesPerDish`, best first |
| data.content[].recommendedRecipes[].id | uuid | Recipe id |
| data.content[].recommendedRecipes[].name | string | Recipe name |
| data.content[].recommendedRecipes[].description | string \| null | Recipe description |
| data.content[].recommendedRecipes[].prepTimeMinutes | integer \| null | Prep time |
| data.content[].recommendedRecipes[].cookTimeMinutes | integer \| null | Cook time |
| data.content[].recommendedRecipes[].servings | integer \| null | Servings the totals below describe |
| data.content[].recommendedRecipes[].difficulty | string \| null | `EASY`, `MEDIUM` or `HARD` |
| data.content[].recommendedRecipes[].calories | decimal \| null | Author-entered total for the whole recipe |
| data.content[].recommendedRecipes[].proteinG | decimal \| null | Total grams of protein, whole recipe |
| data.content[].recommendedRecipes[].carbohydrateG | decimal \| null | Total grams of carbohydrate, whole recipe |
| data.content[].recommendedRecipes[].fatG | decimal \| null | Total grams of fat, whole recipe |
| data.content[].recommendedRecipes[].fiberG | decimal \| null | Total grams of fibre, whole recipe |
| data.page | integer | Current page index |
| data.size | integer | Page size |
| data.totalElements | long | Eligible dishes across all pages, after filtering |
| data.totalPages | integer | Total number of pages |
| data.first | boolean | Whether this is the first page |
| data.last | boolean | Whether this is the last page |

Nutrition fields are omitted from the JSON when `null`. `null` means the author
did not provide the value — it is distinct from `0`.

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | A numeric parameter is negative, or `size`/`recipesPerDish` is below 1 | "Validation failed" |
| 401 | Missing or invalid JWT | "Unauthorized" |
| 500 | Server error | "Internal server error" |

## Business Rules
- A dish is eligible only when at least one approved, non-deleted recipe
  survives both the allergy check and the recipe filters ("block on all").
  Dishes with no surviving recipe are omitted, and `totalElements` counts only
  what the caller actually receives.
- Allergy matching is **exact** on the ingredient name, case-insensitive and
  trimmed. A stored allergy `peanut` does not match an ingredient
  `peanut butter`. See BR-MCP-008 in `docs/brs/mcp-tools.md`.
- Only recipes with `moderation_status = APPROVED` and
  `deleted_at IS NULL` are ever recommended (ADR-016), so a pending or rejected
  recipe never appears here, even for its own author.
- Recommendations are ranked `score DESC NULLS LAST, createdAt DESC, id ASC`
  and capped at `recipesPerDish`. `score` is a relevance score of the recipe
  text, not a nutrition or popularity ranking.
- Retired (soft-deleted) dishes never appear.
- Read-only: the endpoint never writes.

## Example

### Request
```bash
curl -X GET "http://localhost:8080/api/dishes/suggestions?cuisineType=Vietnamese&maxTotalTime=45&recipesPerDish=2" \
  -H "Authorization: Bearer <access-token>"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Dish suggestions retrieved successfully",
  "data": {
    "content": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "pho chay",
        "description": "Vietnamese vegetarian noodle soup",
        "imageUrl": null,
        "cuisineType": "Vietnamese",
        "recommendedRecipes": []
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
- Feature Spec: `docs/feats/dish-suggestions.md`
- Business Rules: `docs/brs/mcp-tools.md` — BR-MCP-002, BR-MCP-008
- Companion API: `docs/apis/recipes/get-dishes.md` (plain catalogue listing)
- AI tool: `suggest_dishes` on `service/ai/MenuTools`
- Data model: `docs/arch/data-dictionary.md` (Dish, Recipe, RecipeIngredient)
