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
        "userId": "9f1c2f4a-8a3b-4c5d-9e6f-7a8b9c0d1e2f",
        "displayName": "John Doe",
        "avatarUrl": "https://res.cloudinary.com/vegalife/avatar/john.jpg",
        "title": "Easy tofu bowl",
        "rawContent": {"type": "doc", "content": []},
        "featuredImageUrl": "https://example.com/tofu-bowl.jpg",
        "categoryIds": ["7c9e6679-7425-40de-944b-e07fc1f90ae7"],
        "mediaIds": [],
        "recipeIds": [],
        "status": "published",
        "flag": null,
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
| data.content[].userId | uuid | Author's user id — the stable, unique way to address the author (the display name is not unique, ADR-019). |
| data.content[].displayName | string | Author's display name, for rendering. |
| data.content[].avatarUrl | string or null | Author's avatar URL; `null` when the author has no profile row yet (BR-PROFILE-003). |
| data.content[].title | string | Post title. |
| data.content[].rawContent | object | Rich-text document produced by the frontend editor (issue #101). |
| data.content[].featuredImageUrl | string or null | Featured image URL, if present. |
| data.content[].categoryIds | array of UUID | Assigned categories. |
| data.content[].mediaIds | array of UUID | Attached uploaded media. |
| data.content[].recipeIds | array of UUID | Always empty on this list endpoint and on create/edit — recipes are only resolved on `GET /api/posts/{postId}` and the two attach endpoints, to avoid a query per row on a paginated list (issue #137). |
| data.content[].status | string | Post status: `drafted`, `processing`, `published`, or `unpublished`. `processing` means a filter verdict or an admin decision is outstanding; `unpublished` means an admin rejected it (BR-POST-010, BR-FILTER-008). |
| data.content[].flag | string or null | Blocker of a held post: `FILTER_FAILED`, `SIMILAR_CONTENT`, or `FILTER_SKIPPED`; `null` when the post is not being held — a draft, an in-flight filter run, or a published/rejected post (ADR-023). |
| data.content[].flagDetail | object or null | The evidence behind `flag`, as a JSON snapshot (ADR-024): `kind` (always equal to `flag`) plus the fields that blocker owns — `outcome`/`score`/`threshold` for `FILTER_FAILED`, `postId`/`similarity`/`threshold` for `SIMILAR_CONTENT`, `cause` for `FILTER_SKIPPED`. Absent fields are omitted. `null` with no blocker. |
| data.content[].rejectionReason | string or null | Why an admin rejected this post. Always populated on this endpoint — these are the caller's own posts. `null` when no reason was recorded. |
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
- All post statuses are included so the owner can manage drafts, posts awaiting a decision, and rejected posts. Soft-deleted posts are excluded.
- `flag`/`flagDetail` are returned for every post; `null` means nothing is holding the post — it is a draft, its filter run is still in flight, or it reached a terminal state (ADR-023).
- A valid JWT is required by the existing security configuration.
- Every item carries its author summary (`userId`, `displayName`, `avatarUrl`), resolved with one batched avatar lookup per page — never one query per post.
- Results use zero-based pagination and are ordered by creation time, newest first.

## Related

- Database schema: `src/main/resources/db/migration/V7__create_post_tables.sql`, `src/main/resources/db/migration/V27__add_post_raw_content_drop_type_video_url.sql`
- Shared pagination response: `src/main/java/com/vegalife/shared/dto/PageResponse.java`
- Shared error/response contract: `docs/apis/error-responses.md`
