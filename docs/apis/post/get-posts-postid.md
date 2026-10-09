# API Reference: GET /api/posts/{postId}

## Overview

Return a single post by id, using the same response shape as the post list endpoints. Visibility depends only on the post's `published` status, never on ownership — the caller's own draft is not visible either.

## Endpoint

```text
GET /api/posts/{postId}
```

## Authentication

Optional. No JWT is needed; the route is `permitAll`. Any caller — guest or authenticated — may read any `published` post. If a token is sent it is still validated: an invalid or expired token returns `401`.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| postId | UUID | Yes | ID of the post to read. |

### Query Parameters

No query parameters.

### Request Body

No request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Post retrieved successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "Easy tofu bowl",
    "rawContent": {"type": "doc", "content": []},
    "featuredImageUrl": "https://example.com/tofu-bowl.jpg",
    "categoryIds": ["7c9e6679-7425-40de-944b-e07fc1f90ae7"],
    "mediaIds": [],
    "status": "published",
    "flag": "PASSED",
    "viewCount": 12,
    "publishedAt": "2026-09-26T10:00:00Z",
    "createdAt": "2026-09-26T09:50:00Z"
  }
}
```

Same `PostListResponse` shape as `GET /api/posts` — see `docs/apis/post/get-posts.md` for the full field table.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | A token is supplied but is invalid, expired, or the account is inactive | `Unauthorized` |
| 404 | The post does not exist, is soft-deleted, or its status is not `published` (including the caller's own non-published post) | `Post not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Visibility is gated by `status == published` and `deleted_at IS NULL` only (BR-POST-011); ownership is never checked, so another user's published post is returned and the caller's own draft is not.
- A non-published or soft-deleted post, and an unknown id, are all reported as `404 Post not found` — the same not-found-over-leak pattern used elsewhere in the post API, so no post data or existence is leaked.
- Public access: this route is `permitAll`, so guests may read any `published` post — like `GET /api/posts/feed` and `GET /api/users/{userId}/posts`. Only a supplied-but-invalid token is rejected (`401`); a missing token is not.
- No view-count increment, caching, or ETags are performed by this endpoint.

## Example

```bash
curl http://localhost:8080/api/posts/550e8400-e29b-41d4-a716-446655440000
```

## Related

- Feature spec: `docs/feats/get-post-by-id.md`
- Business rules: `docs/brs/posts.md`
- List responses use the same shape: `docs/apis/post/get-posts.md`
- Shared error/response contract: `docs/apis/error-responses.md`
