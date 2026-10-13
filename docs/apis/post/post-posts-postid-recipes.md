# API Reference: POST /api/posts/{postId}/recipes

## Overview

Replace the complete set of recipe attachments on an owned post with existing recipes.

## Endpoint

```text
POST /api/posts/{postId}/recipes
```

## Authentication

Required: a valid JWT access token in `Authorization: Bearer <token>`. The caller must own the post, unless the caller is an Administrator.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| postId | UUID | Yes | ID of the post whose recipe set is being replaced. |

### Query Parameters

No query parameters.

### Request Body

```json
{
  "recipeIds": ["85fb5914-f304-45c9-b44e-480e90ec67fa"]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| recipeIds | UUID array | Yes | Complete replacement set. Each recipe row must exist and be non-deleted. An empty array clears all recipe attachments. |

The request replaces the full recipe set; it does not append to the current set. Media attachments and all other post fields remain unchanged.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Post recipes updated successfully",
  "data": {
    "id": "98b73399-d47a-4ad3-a092-d5f5edc2c7a6",
    "title": "Vegan tofu bowl",
    "content": "A simple plant-based lunch.",
    "rawContent": {},
    "featuredImageUrl": null,
    "categoryIds": [],
    "mediaIds": [],
    "recipeIds": ["85fb5914-f304-45c9-b44e-480e90ec67fa"],
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
| message | string | `Post recipes updated successfully`. |
| data | PostListResponse | Updated post, including `mediaIds` and `recipeIds`. |

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Request is malformed, contains invalid UUIDs, or one or more recipe IDs are missing or deleted | `Validation failed` or a specific validation message |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 404 | Post does not exist, is deleted, or belongs to another user when caller is not an Administrator | `Post not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- The supplied list replaces the complete recipe attachment set; `[]` clears it.
- Recipe ownership is not checked; any existing non-deleted recipe may be attached.
- The operation does not change post status/publication state and does not enqueue content filtering.
- See [Feature Spec: Attach Media and Recipes to a Post](../../feats/attach-media-and-recipes-to-post.md) and issue #137.

## Related

- Related endpoint: [`POST /api/posts/{postId}/media`](post-posts-postid-media.md)
- Shared error response contract: [`error-responses.md`](../error-responses.md)
