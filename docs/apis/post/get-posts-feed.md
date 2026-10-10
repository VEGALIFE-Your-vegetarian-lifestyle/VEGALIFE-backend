# API Reference: GET /api/posts/feed

## Overview

Return a paginated, platform-wide feed of published posts, newest first. Public: guests can read it (BR-PUBLIC-001, BR-SEARCH-002, BR-POST-010).

## Endpoint

```text
GET /api/posts/feed
```

## Authentication

Optional. No JWT is needed; the route is `permitAll`. If a token is sent it is still validated — an invalid or expired token returns `401`.

Every caller gets the same result: published, non-deleted posts only.

## Request

### Path Parameters

No path parameters.

### Query Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | integer | No | Zero-based page index. Defaults to `0`; must be `>= 0`. |
| size | integer | No | Page size. Defaults to `20`; between `1` and `100`. |

Order is fixed: `published_at DESC, created_at DESC`. It cannot be changed; there is no sort, category, or keyword parameter.

### Request Body

No request body.

## Responses

### Success Response (200 OK)

Standard pagination envelope (`ApiResponse<PageResponse<PostListResponse>>`), message `Posts retrieved successfully`:

```json
{
  "success": true,
  "message": "Posts retrieved successfully",
  "data": {
    "content": [
      {
        "postId": "550e8400-e29b-41d4-a716-446655440000",
        "title": "Vegan pho for beginners",
        "status": "published",
        "publishedAt": "2026-10-01T09:15:00",
        "createdAt": "2026-10-01T09:10:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3
  }
}
```

Item fields follow the existing `PostListResponse` shape used by `GET /api/posts` (the exact field set is defined by that DTO).

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `page` or `size` out of range | `Validation failed` |
| 401 | A token is supplied but is invalid, expired, or the account is inactive | `Unauthorized` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- BR-POST-010: only `published`, non-deleted posts are public; drafts, `processed`, `unpublished`, and `hidden` posts never appear (BR-CONTENT-003 keeps them private to owner and Administrators). A filter-rejected post is `unpublished` (BR-FILTER-008).
- BR-POST-003 pagination bounds apply: page `0`, size `20`, `size` 1–100.
- Soft-deleted posts (`deleted_at IS NOT NULL`) are never returned.
- No schema migration is needed.

## Example

```bash
curl "http://localhost:8080/api/posts/feed?page=0&size=10"
```

## Related

- Feature spec: `docs/feats/posts-feed.md`
- Business rules: `docs/brs/posts.md`
- List your own posts: `docs/apis/post/get-posts.md`
- List a member's posts: `docs/apis/post/get-users-userid-posts.md`
- Shared error/response contract: `docs/apis/error-responses.md`
