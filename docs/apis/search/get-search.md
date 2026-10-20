# API Reference: GET /api/search

## Overview

Keyword search across posts, videos, and recipes in one query (issue #40). Public endpoint, no authentication required.

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
| type | string | No | One of `post`, `video`, `recipe`. Omitted searches all three. Any other value → 400. |
| categoryId | UUID | No | Narrows `post`/`video` results to that category. Never excludes recipe results — a recipe has no category of its own. |
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
| data.content[].type | string | `post`, `video`, or `recipe`. A `video` is a post carrying at least one `video/*` media; there is no separate video entity. |
| data.content[].id | uuid | The post's or recipe's own id. |
| data.content[].title | string | Post title or recipe name. |
| data.content[].snippet | string | ~150-character excerpt around the first case-insensitive match, with the matched text wrapped in `<mark>`. The same trust rules as every other text field returned by this API apply — the client is responsible for safely rendering it. |
| data.content[].categoryIds | array of UUID | Assigned categories; always empty for a recipe. |
| data.content[].createdAt | string | ISO-8601 creation timestamp. |

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `q` missing/blank, `type` not one of `post`/`video`/`recipe`, or `page`/`size` out of range | `Validation failed` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Only published, non-deleted posts are searchable — drafts, unpublished, hidden, and moderated-out posts never surface (same visibility floor as the public feed, BR-POST-010). Recipes are searchable whenever not soft-deleted; a recipe has no publish state of its own.
- Matching is a case-insensitive substring match (`ILIKE`), not semantic/full-text search: post/video match on `title` or `content`; recipe matches on `name`, `description`, or `instructions`.
- "Relevance" is a two-tier rank: a title/name match outranks a body-only match; within the same tier, newest first. There is no term-frequency or semantic scoring.
- `type=post` and `type=video` both query the same underlying post rows; `type=video` keeps only posts with at least one `video/*` media, `type=post` keeps the rest.
- A `categoryId` filter narrows posts/videos only; it is silently ignored for recipes, which are never excluded by it.
- Search suggestions/autocomplete, personalized ranking, and search analytics are out of scope (issue #40 non-goals).

## Example

```bash
curl -X GET "http://localhost:8080/api/search?q=tofu&type=recipe&page=0&size=10"
```

## Related

- Public feed: `docs/apis/post/get-posts-feed.md`
- Create a recipe: `docs/apis/recipes/post-recipes.md`
- Shared error/response contract: `docs/apis/error-responses.md`
