# API Reference: GET /api/posts

## Overview

Return a paginated list of posts owned by the authenticated user.

## Endpoint

```text
GET /api/posts
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`). The API uses the user ID from that token, so callers cannot choose another user's posts.

## Request

### Path Parameters

No path parameters.

### Query Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | integer | No | Zero-based page index. Defaults to `0`; must be zero or greater. |
| size | integer | No | Number of posts per page. Defaults to `20`; must be between `1` and `100`. |

Posts are ordered by `createdAt` descending. The order cannot be changed by the request.

### Request Body

No request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Posts retrieved successfully",
  "data": {
    "content": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "title": "Easy tofu bowl",
        "content": "A simple plant-based lunch.",
        "featuredImageUrl": "https://example.com/tofu-bowl.jpg",
        "status": "published",
        "viewCount": 12,
        "publishedAt": "2026-09-26T10:00:00Z",
        "createdAt": "2026-09-26T09:50:00Z"
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
| success | boolean | `true` when the request succeeds. |
| message | string | `Posts retrieved successfully`. |
| data.content | array | Posts on this page. An empty array is returned when there are no matching posts. |
| data.content[].id | uuid | Post identifier. |
| data.content[].title | string | Post title. |
| data.content[].content | string | Post text. |
| data.content[].featuredImageUrl | string or null | Featured image URL, if present. |
| data.content[].status | string | Post status: `created`, `processed`, `published`, `unpublished`, or `hidden`. |
| data.content[].viewCount | integer | Number of recorded views. |
| data.content[].publishedAt | string | ISO-8601 publication timestamp. |
| data.content[].createdAt | string | ISO-8601 creation timestamp. |
| data.page | integer | Zero-based page index returned. |
| data.size | integer | Page size used. |
| data.totalElements | integer | Total matching posts. |
| data.totalPages | integer | Total page count. |
| data.first | boolean | Whether this is the first page. |
| data.last | boolean | Whether this is the last page. |

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Page is negative or size is outside `1`–`100` | `Validation failed` |
| 401 | JWT is missing, invalid, or the account is inactive | `Unauthorized` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Only posts owned by the authenticated user are included. The owner ID comes from the JWT; there is no user ID query parameter.
- All post statuses are included so the owner can manage drafts and hidden or unpublished posts. Soft-deleted posts are excluded.
- A valid JWT is required by the existing security configuration.
- Results use zero-based pagination and are ordered by creation time, newest first.

## Related

- Database schema: `src/main/resources/db/migration/V7__create_post_tables.sql`
- Shared pagination response: `src/main/java/com/vegalife/shared/dto/PageResponse.java`
