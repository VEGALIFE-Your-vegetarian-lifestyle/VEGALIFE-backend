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
| maxProtein | decimal | No | Only recipes with at most this many grams of protein (whole recipe) |
| minCarbohydrate | decimal | No | Only recipes with at least this many grams of carbohydrate (whole recipe) |
| maxCarbohydrate | decimal | No | Only recipes with at most this many grams of carbohydrate (whole recipe) |
| minFat | decimal | No | Only recipes with at least this many grams of fat (whole recipe) |
| maxFat | decimal | No | Only recipes with at most this many grams of fat (whole recipe) |
| minFiber | decimal | No | Only recipes with at least this many grams of fibre (whole recipe) |
| maxFiber | decimal | No | Only recipes with at most this many grams of fibre (whole recipe) |
| page | integer | No | 0-based page index, default 0 |
| size | integer | No | Dishes per page, default 20, min 1, max 50 |
| recipesPerDish | integer | No | Recipes returned per dish, default 3, min 1, max 10 |

All five macros carry both bounds. A caller building a meal plan asks for "high
protein, low fat, at least 8 g of fibre" as readily as it asks for a calorie
window; splitting that across two code paths would let one of them drift.

### Request Body
No request body

Behaviour of the recipe filters:

- They narrow **recipes**, not dishes. The same predicate decides whether a
  dish qualifies (it needs at least one surviving recipe) and which recipes are
  recommended, so a dish is never shown for a recipe that was filtered out of
  its own list.
- Nutrition filters are strict. A recipe whose value is unknown does **not**
  pass any bound — not a lower one and not an upper one. Unknown is not the same
  as zero: treating a missing value as `0` would let "at most 5 g of fat" return
  recipes with unknown fat.
- Since nutrition is derived from ingredients
  ([ADR-022](../../adrs/022-ingredient-level-nutrition.md)), a `null` here means
  "the catalogue cannot price this recipe yet", not "the author left it blank".
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
            "calories": 297.00,
            "proteinG": 35.60,
            "carbohydrateG": 6.40,
            "fatG": 17.46,
            "fiberG": 4.72,
            "nutritionResolved": true
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
| data.content[].recommendedRecipes[].calories | decimal \| null | Computed total for the whole recipe (BR-RECP-004) |
| data.content[].recommendedRecipes[].proteinG | decimal \| null | Total grams of protein, whole recipe |
| data.content[].recommendedRecipes[].carbohydrateG | decimal \| null | Total grams of carbohydrate, whole recipe |
| data.content[].recommendedRecipes[].fatG | decimal \| null | Total grams of fat, whole recipe |
| data.content[].recommendedRecipes[].fiberG | decimal \| null | Total grams of fibre, whole recipe |
| data.content[].recommendedRecipes[].nutritionResolved | boolean | `true` only when every ingredient in the recipe is curated and the totals are real |
| data.page | integer | Current page index |
| data.size | integer | Page size |
| data.totalElements | long | Eligible dishes across all pages, after filtering |
| data.totalPages | integer | Total number of pages |
| data.first | boolean | Whether this is the first page |
| data.last | boolean | Whether this is the last page |

Nutrition fields are omitted from the JSON when `null`. `null` means the recipe
cannot be priced yet — at least one of its ingredients has no unit or no
nutrition — and it is distinct from `0`, which is a real value (water is
legitimately 0 kcal). Use `nutritionResolved` to tell the two apart without
inspecting the individual fields.

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
- A recipe's nutrition is derived from its ingredients and stored, never
  authored (BR-RECP-004). It is `null` unless every ingredient is curated
  (BR-RECP-006), and a `null` fails every nutrition bound rather than passing as
  zero.
- Recommendations are ranked `score DESC NULLS LAST, createdAt DESC, id ASC`
  and capped at `recipesPerDish`. `score` is a relevance score of the recipe
  text, not a nutrition or popularity ranking.
- Retired (soft-deleted) dishes never appear.
- Read-only: the endpoint never writes.

## Example

### Request
```bash
curl -X GET "http://localhost:8080/api/dishes/suggestions?cuisineType=Vietnamese&maxTotalTime=45&minProtein=20&maxFat=15&recipesPerDish=2" \
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
- Nutrition feature spec: `docs/feats/ingredient-nutrition.md`
- ADR: [`docs/adrs/022-ingredient-level-nutrition.md`](../../adrs/022-ingredient-level-nutrition.md)
- Business Rules: `docs/brs/mcp-tools.md` — BR-MCP-002, BR-MCP-008;
  `docs/brs/recipes.md` — BR-RECP-004, BR-RECP-006
- Companion API: `docs/apis/recipes/get-dishes.md` (plain catalogue listing)
- AI tool: `suggest_dishes` on `service/ai/MenuTools`
- Data model: `docs/arch/data-dictionary.md` (Dish, Recipe, RecipeIngredient)
