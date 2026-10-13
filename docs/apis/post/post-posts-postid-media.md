# API Reference: POST /api/posts/{postId}/media

## Overview

Replace the complete set of media attachments on an owned post with existing uploaded media.

## Endpoint

```text
POST /api/posts/{postId}/media
```

## Authentication

Required: a valid JWT access token in `Authorization: Bearer <token>`. The caller must own the post, unless the caller is an Administrator.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| postId | UUID | Yes | ID of the post whose media set is being replaced. |

### Query Parameters

No query parameters.

### Request Body

```json
{
  "mediaIds": ["85fb5914-f304-45c9-b44e-480e90ec67fa"]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| mediaIds | UUID array | Yes | Complete replacement set. Each media row must exist, be non-deleted, and have status `succeed`. An empty array clears all media attachments. |

The request replaces the full media set; it does not append to the current set. Recipe attachments and all other post fields remain unchanged.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Post media updated successfully",
  "data": {
    "id": "98b73399-d47a-4ad3-a092-d5f5edc2c7a6",
    "title": "Vegan tofu bowl",
    "content": "A simple plant-based lunch.",
    "rawContent": {},
    "featuredImageUrl": null,
    "categoryIds": [],
    "mediaIds": ["85fb5914-f304-45c9-b44e-480e90ec67fa"],
    "recipeIds": [],
    "status": "published",
    "flag": "PASSED",
    "viewCount": 0,
    "publishedAt": "2026-10-13T12:00:00Z",
    "createdAt": "2026-10-13T11:00:00Z"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success. |
| message | string | `Post media updated successfully`. |
| data | PostListResponse | Updated post, including `mediaIds` and `recipeIds`. |

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Request is malformed, contains invalid UUIDs, or one or more media IDs are missing, deleted, or not in `succeed` status | `Validation failed` or a specific validation message |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 404 | Post does not exist, is deleted, or belongs to another user when caller is not an Administrator | `Post not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- The supplied list replaces the complete media attachment set; `[]` clears it.
- The operation does not change post status/publication state and does not enqueue content filtering.
- Media uploader ownership is not an access boundary for attachment; see BR-MEDIA-002 in `docs/brs/media.md`.
- See [Feature Spec: Attach Media and Recipes to a Post](../../feats/attach-media-and-recipes-to-post.md) and issue #137.

## Related

- Related endpoint: [`POST /api/posts/{postId}/recipes`](post-posts-postid-recipes.md)
- Shared error response contract: [`error-responses.md`](../error-responses.md)
