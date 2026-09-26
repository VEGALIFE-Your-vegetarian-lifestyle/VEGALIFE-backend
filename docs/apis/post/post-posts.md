# API Reference: POST /api/posts

## Overview

Create a post for the currently authenticated user.

## Endpoint

```text
POST /api/posts
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`). The post owner is taken from the token; the request cannot choose a user ID.

## Request

### Path Parameters

No path parameters.

### Query Parameters

No query parameters.

### Request Body

```json
{
  "title": "Vegan tofu bowl",
  "content": "A simple plant-based lunch.",
  "featuredImageUrl": "https://example.com/tofu-bowl.jpg"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| title | string | Yes | Post title. Must not be blank and must be at most 255 characters. |
| content | string | Yes | Post text. Must not be blank. |
| featuredImageUrl | string or null | No | URL of the featured image. |

The client must not send `userId`, `status`, `viewCount`, or timestamps. The server sets these values.

## Responses

### Success Response (201 Created)

```json
{
  "success": true,
  "message": "Post created successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "Vegan tofu bowl",
    "content": "A simple plant-based lunch.",
    "featuredImageUrl": "https://example.com/tofu-bowl.jpg",
    "status": "created",
    "viewCount": 0,
    "publishedAt": null,
    "createdAt": "2026-09-26T10:00:00Z"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | `true` when the request succeeds. |
| message | string | `Post created successfully`. |
| data.id | uuid | Identifier generated for the new post. |
| data.title | string | Post title. |
| data.content | string | Post text. |
| data.featuredImageUrl | string or null | Featured image URL, if supplied. |
| data.status | string | Initial status is `created`. Semantic filtering is not part of this endpoint yet. |
| data.viewCount | integer | Starts at `0`. |
| data.publishedAt | string or null | `null` until the post is published. |
| data.createdAt | string | Creation timestamp in ISO-8601 format. |

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Title or content is blank, or title exceeds 255 characters | `Validation failed` |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 404 | The authenticated user record no longer exists | `User not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- The author is always the user identified by the authenticated JWT.
- New posts are persisted with status `created`, view count `0`, and no publication timestamp.
- Semantic filtering and automatic publishing are deferred to a later task.
- The schema uses the existing `post` table; no migration is required for this API.

## Example

```bash
curl -X POST http://localhost:8080/api/posts \
  -H "Authorization: Bearer <access-token>" \
  -H "Content-Type: application/json" \
  -d '{"title":"Vegan tofu bowl","content":"A simple plant-based lunch.","featuredImageUrl":"https://example.com/tofu-bowl.jpg"}'
```

## Related

- List the authenticated user's posts: `docs/apis/post/get-posts.md`
- Database schema: `src/main/resources/db/migration/V7__create_post_tables.sql`
