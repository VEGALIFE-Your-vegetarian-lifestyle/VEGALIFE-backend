# API Reference: PATCH /api/posts/{postId}

## Overview

Partially update an active post owned by the authenticated user.

## Endpoint

```text
PATCH /api/posts/{postId}
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`). The authenticated user must own the post.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| postId | UUID | Yes | ID of the post to edit. |

### Query Parameters

No query parameters.

### Request Body

Supply at least one non-null editable field. Omitted and null fields are left unchanged.

```json
{
  "title": "Updated vegan tofu bowl",
  "content": "Updated plant-based lunch recipe.",
  "featuredImageUrl": "https://example.com/updated-tofu-bowl.jpg"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| title | string or null | No | New title; if supplied, must not be blank and must be at most 255 characters. |
| content | string or null | No | New post text; if supplied, must not be blank. |
| featuredImageUrl | string or null | No | New featured image URL. Null or omission keeps the current URL. |

Sending all fields as omitted or null returns `400 Bad Request`. This endpoint does not clear an image by sending null.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Post updated successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "Updated vegan tofu bowl",
    "content": "Updated plant-based lunch recipe.",
    "featuredImageUrl": "https://example.com/updated-tofu-bowl.jpg",
    "status": "created",
    "viewCount": 0,
    "publishedAt": null,
    "createdAt": "2026-09-26T10:00:00Z"
  }
}
```

The response uses the existing `PostListResponse` fields. System-managed fields such as owner, status, view count, publishedAt, and createdAt cannot be changed through this endpoint.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | All editable fields are omitted/null; a supplied title/content is invalid | `Validation failed` |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 404 | Post does not exist, is soft-deleted, or belongs to a different user | `Post not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Only the authenticated owner can edit a post.
- Soft-deleted posts cannot be edited.
- Only non-null editable values are applied; all other existing values remain unchanged.
- The endpoint does not trigger semantic filtering or change the post lifecycle status.

## Example

```bash
curl -X PATCH http://localhost:8080/api/posts/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer <access-token>" \
  -H "Content-Type: application/json" \
  -d '{"title":"Updated vegan tofu bowl"}'
```

## Related

- Feature spec: `docs/feats/edit-user-post.md`
- Business rules: `docs/brs/posts.md`
- Create a post: `docs/apis/post/post-posts.md`
- List the authenticated user's posts: `docs/apis/post/get-posts.md`
