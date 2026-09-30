# API Reference: GET /api/admin/posts

## Overview
Return a paginated list of every non-deleted post across all users and all statuses, optionally filtered by status, author, category, and creation date range, with the semantic content-filter verdict on each item. Admin only.

## Endpoint
```
GET /api/admin/posts
```

## Authentication
Required: Valid JWT access token in Authorization header (`Bearer <token>`), role `ADMIN`.

## Request

### Path Parameters
None.

### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | integer | No | 0-based page index, default 0 |
| size | integer | No | Page size, default 20, max 100 |
| sort | string | No | Format `property,direction`. Default `createdAt,desc`. Property must be one of: `createdAt`, `publishedAt`, `updatedAt`, `viewCount`, `title` |
| status | string | No | One of: `created`, `processed`, `published`, `unpublished`, `hidden`, `flagged` |
| userId | uuid | No | Only posts authored by this user |
| categoryId | uuid | No | Only posts belonging to this category |
| createdFrom | string | No | ISO-8601 datetime lower bound (inclusive) on `createdAt`, e.g. `2026-01-01T00:00:00Z` |
| createdTo | string | No | ISO-8601 datetime upper bound (inclusive) on `createdAt`, e.g. `2026-01-31T23:59:59Z` |

### Request Body
No request body

## Responses

### Success Response (200 OK)
```json
{
  "success": true,
  "message": "Posts retrieved successfully",
  "data": {
    "content": [
      {
        "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
        "title": "Vegan pho at home",
        "type": "blog",
        "content": "A step-by-step guide to a clear mushroom broth...",
        "featuredImageUrl": "https://cdn.example.com/posts/pho.jpg",
        "videoUrl": null,
        "categoryIds": ["1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed"],
        "mediaIds": [],
        "status": "published",
        "flag": "PASSED",
        "viewCount": 42,
        "publishedAt": "2026-09-28T08:15:00Z",
        "createdAt": "2026-09-28T08:00:00Z",
        "userId": "550e8400-e29b-41d4-a716-446655440000",
        "username": "jane",
        "email": "jane@example.com"
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
| success | boolean | Always true for success |
| message | string | "Posts retrieved successfully" |
| data.content | array | Post items for this page |
| data.content[].id | uuid | Post id |
| data.content[].title | string | Post title |
| data.content[].type | string | `blog` or `video` |
| data.content[].content | string | Post body |
| data.content[].featuredImageUrl | string | Featured image URL, nullable |
| data.content[].videoUrl | string | Video URL, nullable (`type=video`) |
| data.content[].categoryIds | uuid[] | Category ids the post belongs to |
| data.content[].mediaIds | uuid[] | Media ids attached to the post |
| data.content[].status | string | created, processed, published, unpublished, hidden, or flagged |
| data.content[].flag | string | Content-filter verdict: PENDING, PASSED, REJECTED, NEEDS_REVIEW; null when never filtered |
| data.content[].viewCount | integer | View counter |
| data.content[].publishedAt | string | ISO-8601 publish timestamp, nullable |
| data.content[].createdAt | string | ISO-8601 creation timestamp |
| data.content[].userId | uuid | Author user id |
| data.content[].username | string | Author username |
| data.content[].email | string | Author email |
| data.page | integer | 0-based page index returned |
| data.size | integer | Page size used |
| data.totalElements | integer | Total matching posts |
| data.totalPages | integer | Total page count |
| data.first | boolean | Whether this is the first page |
| data.last | boolean | Whether this is the last page |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Unknown `sort` property, invalid `status`/date format, `createdFrom` after `createdTo`, or `size` > 100 | "Validation failed" or the parse error message |
| 401 | Missing/invalid JWT | "Unauthorized" |
| 403 | Authenticated non-admin | "Forbidden" |
| 500 | Server error | "Internal server error" |

## Business Rules
- Soft-deleted posts (`deletedAt != null`) are excluded (issue #1 / FR-006).
- Only `ROLE_ADMIN` may call this endpoint (SecurityConfig `/api/admin/**`).
- Unlike `GET /api/posts` and `GET /api/users/{userId}/posts`, no status restriction is applied: every status is listable (issue #1 / FR-002).

## Example

### Request
```bash
curl -X GET "http://localhost:8080/api/admin/posts?page=0&size=20&status=flagged&categoryId=1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed&sort=createdAt,desc" \
  -H "Authorization: Bearer <admin_access_token>"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Posts retrieved successfully",
  "data": {
    "content": [
      {
        "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
        "title": "Vegan pho at home",
        "type": "blog",
        "content": "A step-by-step guide to a clear mushroom broth...",
        "featuredImageUrl": "https://cdn.example.com/posts/pho.jpg",
        "videoUrl": null,
        "categoryIds": ["1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed"],
        "mediaIds": [],
        "status": "flagged",
        "flag": "REJECTED",
        "viewCount": 42,
        "publishedAt": "2026-09-28T08:15:00Z",
        "createdAt": "2026-09-28T08:00:00Z",
        "userId": "550e8400-e29b-41d4-a716-446655440000",
        "username": "jane",
        "email": "jane@example.com"
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

### Error Response (403)
```json
{
  "success": false,
  "message": "Forbidden",
  "data": null
}
```

## Related
- Feature Spec: `docs/feats/list-all-posts-admin-api.md`
- Driving issue: https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/1
- Business Rules: `docs/brs/posts.md` (post visibility), `docs/brs/auth.md` (JWT authentication)
