# API Reference: GET /api/search

> **Deprecated.** Use the typed list filters instead: `GET /api/posts` (`keyword`,
> `categoryId`, `createdFrom`, `createdTo`) returns `PostListResponse` pages, and
> `GET /api/recipes` (`keyword`, `dishId`, `difficulty`, `maxTotalTimeMinutes`)
> returns `RecipeResponse` pages. This endpoint is kept for compatibility only; its
> behavior is unchanged.

## Overview

Keyword search across posts, videos, recipes, and restaurants in one query (issues #40, #196). Public endpoint, no authentication required.

Deprecated in favour of the typed list filters on `GET /api/posts` and
`GET /api/recipes`; see `docs/feats/post-recipe-list-filters.md`.

## Endpoint

```text
GET /api/search
```

## Authentication

None. Any caller, including guests, may search.

## Request

### Query Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| q | string | Yes | Keyword to search for. Missing or blank → 400. |
| type | string | No | One of `post`, `video`, `recipe`, `restaurant`. Omitted searches all four. Any other value → 400. |
| categoryId | UUID | No | Narrows `post`/`video` results to that category. Never excludes recipe or restaurant results — neither has a category of its own. |
| dateFrom | ISO-8601 datetime | No | Only rows created at or after this instant. |
| dateTo | ISO-8601 datetime | No | Only rows created at or before this instant. |
| page | integer | No | 0-based page index, default `0`. `< 0` → 400. |
| size | integer | No | Page size, default `20`, range 1–100 (outside → 400). |

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Search completed successfully",
  "data": {
    "content": [
      {
        "type": "post",
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "title": "Vegan tofu bowl",
        "snippet": "A simple plant-based lunch with <mark>tofu</mark> inside.",
        "categoryIds": ["7c9e6679-7425-40de-944b-e07fc1f90ae7"],
        "createdAt": "2026-10-14T10:00:00Z"
      },
      {
        "type": "recipe",
        "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "title": "Tofu stir fry",
        "snippet": "Fried <mark>tofu</mark> with vegetables.",
        "categoryIds": [],
        "createdAt": "2026-10-13T09:00:00Z"
      },
      {
        "type": "restaurant",
        "id": "9b2e6679-7425-40de-944b-e07fc1f90b11",
        "title": "Garden Cafe",
        "snippet": "Matches via dish: <mark>Pho</mark> Chay",
        "categoryIds": [],
        "createdAt": "2026-10-12T08:00:00Z"
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
|-------|------|-------------|
| data.content[].type | string | `post`, `video`, `recipe`, or `restaurant`. A `video` is a post carrying at least one `video/*` media; there is no separate video entity. |
| data.content[].id | uuid | The post's, recipe's, or restaurant's own id. |
| data.content[].title | string | Post title, recipe name, or restaurant name. |
| data.content[].snippet | string | ~150-character excerpt around the first case-insensitive match, with the matched text wrapped in `<mark>`. For a restaurant matched only through an attached dish's name (not its own name or description), the snippet instead names the matched dish, e.g. `Matches via dish: <mark>Pho</mark> Chay`, so a human can tell why the restaurant matched. The same trust rules as every other text field returned by this API apply — the client is responsible for safely rendering it. |
| data.content[].categoryIds | array of UUID | Assigned categories; always empty for a recipe or restaurant. |
| data.content[].createdAt | string | ISO-8601 creation timestamp. |

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `q` missing/blank, `type` not one of `post`/`video`/`recipe`/`restaurant`, or `page`/`size` out of range | `Validation failed` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Only published, non-deleted posts are searchable — drafts, posts awaiting a decision (`processing`), and rejected (`unpublished`) posts never surface (same visibility floor as the public feed, BR-POST-010). Recipes and restaurants are searchable whenever not soft-deleted; neither has a publish state of its own.
- Matching is a case-insensitive substring match (`ILIKE`), not semantic/full-text search: post/video match on `title` or `content`; recipe matches on `name`, `description`, or `instructions`; restaurant matches on `name`, `description`, or the name of an attached, approved, non-deleted dish (issue #196). A dish is never itself returned as a search result — only the restaurant it's attached to.
- "Relevance" is a two-tier rank: a title/name match outranks a body-only, instructions-only, description-only, or dish-only match; within the same tier, newest first. There is no term-frequency or semantic scoring. A restaurant matched through more than one of {name, description, a dish} appears exactly once, at its best (lowest) rank.
- `type=post` and `type=video` both query the same underlying post rows; `type=video` keeps only posts with at least one `video/*` media, `type=post` keeps the rest.
- A `categoryId` filter narrows posts/videos only; it is silently ignored for recipes and restaurants, neither of which is ever excluded by it.
- Search suggestions/autocomplete, personalized ranking, and search analytics are out of scope (issue #40 non-goals); so are fuzzy/stemmed matching and ranking by distance, reviews, or popularity for restaurant results (issue #196 non-goals).

## Example

```bash
curl -X GET "http://localhost:8080/api/search?q=tofu&type=restaurant&page=0&size=10"
```

## Related

- Public feed: `docs/apis/post/get-posts.md`
- Create a recipe: `docs/apis/recipes/post-recipes.md`
- Shared error/response contract: `docs/apis/error-responses.md`
