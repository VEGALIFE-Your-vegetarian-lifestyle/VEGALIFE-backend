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
  "type": "blog",
  "content": "A simple plant-based lunch.",
  "featuredImageUrl": "https://example.com/tofu-bowl.jpg",
  "categoryIds": ["7c9e6679-7425-40de-944b-e07fc1f90ae7"],
  "publish": true
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| title | string | Yes | Post title. Must not be blank and must be at most 255 characters. |
| type | string | Yes | `blog` or `video` (BR-CONTENT-002). |
| content | string | Yes for `blog` | Post text; required for blog posts, an optional description for video posts. |
| featuredImageUrl | string or null | No | URL of the featured image. |
| videoUrl | string | Video posts | Either `videoUrl` or `mediaId` is required for video posts (BR-CONTENT-002). |
| mediaId | UUID | Video posts | Uploaded video that must already be in state `succeed`. |
| categoryIds | array of UUID | No | Active categories; required when `publish` is `true` (BR-CONTENT-004). |
| publish | boolean | No | `false` (default) keeps the post as a private draft; `true` expresses publish intent and is subject to content filtering (BR-POST-004). |

The client must not send `userId`, `status`, `flag`, `viewCount`, or timestamps. The server sets these values.

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
    "flag": "PENDING",
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
| data.status | string | Initial status is `created`. With `publish: true` the post still starts as `created` and is only set to `published` after filtering returns `PASSED` (BR-POST-004). |
| data.flag | string or null | Content filter state: `null` (never filtered — the default for drafts), `PENDING`, `PASSED`, `REJECTED`, or `NEEDS_REVIEW` (BR-FILTER-007). |
| data.viewCount | integer | Starts at `0`. |
| data.publishedAt | string or null | `null` until the post is published. |
| data.createdAt | string | Creation timestamp in ISO-8601 format. |

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Title is blank/overlong, `type` missing or not `blog`/`video`, blog without content, video without a file or link, unknown/inactive category, `publish: true` without a category | `Validation failed` or the specific rule message |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 404 | The authenticated user record no longer exists, or `mediaId` does not reference existing media | `User not found` / `Media not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- The author is always the user identified by the authenticated JWT.
- New posts are persisted with status `created`, view count `0`, and no publication timestamp.
- `publish: true` does not publish immediately: the post is stored with `flag: PENDING`, `publish_intent` set, and an entry on the outbound outbox (`CONTENT_FILTER` channel); it stays `created` until filtering returns `PASSED` (BR-POST-004, BR-FILTER-004, BR-FILTER-005).
- Static hard rejects (`TOO_SHORT`, `LINK_SPAM`) are applied on publish intent and can return `400` before the post is stored (BR-FILTER-003).
- Filtering runs asynchronously; this endpoint never blocks on the embedding service (BR-FILTER-006).
- Schema: the `flag`, `filter_queued_at`, and `publish_intent` columns are added by migration `V19__add_post_filtering.sql`; no other new columns are used by this endpoint.

## Example

```bash
curl -X POST http://localhost:8080/api/posts \
  -H "Authorization: Bearer <access-token>" \
  -H "Content-Type: application/json" \
  -d '{"title":"Vegan tofu bowl","type":"blog","content":"A simple plant-based lunch.","featuredImageUrl":"https://example.com/tofu-bowl.jpg"}'
```

## Related

- List the authenticated user's posts: `docs/apis/post/get-posts.md`
- Content filtering feature: `docs/feats/post-content-filtering.md`
- Database schema: `src/main/resources/db/migration/V7__create_post_tables.sql`
