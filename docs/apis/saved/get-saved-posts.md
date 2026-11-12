# API Reference: GET /api/saved/posts

## Overview

Lists the caller's saved posts, newest save first, including only posts that are still published
(BR-SAVED-002).

## Endpoint

```text
GET /api/saved/posts
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`). The list always
belongs to the caller; no member id is accepted.

## Request

### Query Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | integer | No | Zero-based page index. Defaults to `0`. Must be `>= 0`. |
| size | integer | No | Page size. Defaults to `20`. Must be between `1` and `100`. |

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Saved posts retrieved successfully",
  "data": {
    "content": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "userId": "6f1c2b1a-9a1e-4f5a-8f3e-2b4c9d8e7f10",
        "displayName": "greenchef",
        "avatarUrl": "https://cdn.example.com/avatar/greenchef.png",
        "title": "My first vegan week",
        "content": "Switching was easier than I expected...",
        "rawContent": {},
        "featuredImageUrl": null,
        "categoryIds": [],
        "mediaIds": [],
        "recipeIds": [],
        "status": "published",
        "flag": "PASSED",
        "rejectionReason": null,
        "viewCount": 12,
        "publishedAt": "2026-11-01T09:30:00Z",
        "createdAt": "2026-11-01T09:00:00Z"
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
| content | array of `PostListResponse` | Saved posts in save order, newest first. The shape is the same one the feed returns. |
| totalElements | long | Number of saved posts that are still visible — not the number ever saved. |

`recipeIds` is always empty in this list: the feed's post shape does not resolve attached recipes, and
this endpoint deliberately returns that same shape.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `page` is negative or `size` is outside 1–100 | `Validation failed` |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- The list is owner-scoped and private (`BR-SAVED-001`); saving is not a public metric.
- A saved post that is no longer published, or is soft-deleted, is omitted without its save being
  deleted, so it returns if the post becomes published again (`BR-SAVED-002`).
- Paging is bounded (`BR-POST-003`): default 20, maximum 100.
- `totalElements` counts visible saves only, so the pages always sum to that total.

## Example

```bash
curl "http://localhost:8080/api/saved/posts?page=0&size=20" \
  -H "Authorization: Bearer <access-token>"
```

### Empty list

```json
{
  "success": true,
  "message": "Saved posts retrieved successfully",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "first": true,
    "last": true
  }
}
```

## Related

- Save a post: `docs/apis/saved/post-saved-posts-postid.md`
- Unsave a post: `docs/apis/saved/delete-saved-posts-postid.md`
- List saved recipes: `docs/apis/saved/get-saved-recipes.md`
- Business rules: `docs/brs/saved-items.md`
