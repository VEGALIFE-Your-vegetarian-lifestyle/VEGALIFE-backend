# API Reference: GET /api/search

## Overview

Search public posts, videos, and recipes together, with relevance ranking, filters, pagination, and highlighted matches.

## Endpoint

```text
GET /api/search
```

## Authentication

Public. No JWT is required. Search visibility rules apply to every caller.

## Request

### Path Parameters

None.

### Query Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| q | string | Yes | Search text, 1–100 characters after trimming. |
| type | string | No | One result type: `POST`, `VIDEO`, or `RECIPE`. Omit to search all types. |
| from | ISO-8601 datetime | No | Inclusive lower date bound. Uses post `publishedAt`, video `createdAt`, and recipe `createdAt`. |
| to | ISO-8601 datetime | No | Inclusive upper date bound. Uses the same date field as `from`. Must be greater than or equal to `from`. |
| categoryId | UUID | No | Restrict posts to this category; videos and recipes must be attached to a published post carrying it. |
| page | integer | No | Zero-based page number; default `0`. |
| size | integer | No | Page size from `1` to `100`; default `20`. |

Example:

```text
GET /api/search?q=đậu%20hũ&type=POST&from=2026-01-01T00:00:00Z&categoryId=85fb5914-f304-45c9-b44e-480e90ec67fa&page=0&size=20
```

### Request Body

No request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Search results retrieved successfully",
  "data": {
    "content": [
      {
        "id": "98b73399-d47a-4ad3-a092-d5f5edc2c7a6",
        "type": "POST",
        "title": "Cách làm đậu hũ sốt cà",
        "summary": "Món ăn chay dễ làm...",
        "thumbnailUrl": null,
        "mediaUrl": null,
        "relatedPostId": null,
        "ownerId": "aca135af-1941-47fb-a42f-4a7ece560f6e",
        "date": "2026-09-15T18:57:35.997901Z",
        "score": 0.80,
        "highlightedTitle": "Cách làm <mark>đậu hũ</mark> sốt cà",
        "highlightedSnippet": "Món ăn với <mark>đậu hũ</mark> và cà chua..."
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
|-------|------|-------------|
| id | UUID | Post, media/video, or recipe ID. |
| type | string | `POST`, `VIDEO`, or `RECIPE`. |
| title | string | Post title, associated post title for a video when available, or recipe name. |
| summary | string/null | Short searchable description; never full post content or recipe instructions. |
| thumbnailUrl | string/null | Post featured image, video thumbnail, or dish image. |
| mediaUrl | string/null | Uploaded video URL for `VIDEO` results; null for posts and recipes. |
| relatedPostId | UUID/null | Published post associated with a video; null for posts and recipes. |
| ownerId | UUID/null | Post author, video uploader, or recipe author. |
| date | ISO-8601 datetime/null | Post publication time or media/recipe creation time. |
| score | number | Weighted substring relevance score from `0.45` to `1.00`; higher ranks first. |
| highlightedTitle | string | Title/name with matched terms wrapped in `<mark>`; source HTML is escaped. |
| highlightedSnippet | string/null | Matching excerpt with matched terms wrapped in `<mark>`; source HTML is escaped. |

Results from all types are ranked together by weighted, case-insensitive substring match: exact title/name (`1.00`), title/name prefix (`0.90`), title/name substring (`0.80`), descriptive field (`0.65`), then body/instructions (`0.45`). Ties are ordered by `date` descending, then ID ascending. Matching does not tokenize, stem, or use PostgreSQL full-text search. Highlighted snippets are limited to 200 characters; raw `title` stays unmarked while `highlightedTitle` and `highlightedSnippet` contain escaped source text with `<mark>` tags.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Missing/blank/oversized `q`, unknown `type`, invalid date range, or invalid pagination | Standard validation failure body |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Search is public and returns only non-deleted published posts, successful videos attached to non-deleted published posts, and non-deleted recipes. See [`BR-SEARCH-002`](../../brs/search.md).
- Category filtering for video and recipe records is evaluated through their linked published posts.
- Matched text is escaped before `<mark>` tags are added; clients should still render these fields using their safe HTML handling.
- See [Feature Spec: Search Posts, Videos, and Recipes](../../feats/search-api.md) and issue #40.

## Related

- Shared error response contract: [`error-responses.md`](../error-responses.md)
