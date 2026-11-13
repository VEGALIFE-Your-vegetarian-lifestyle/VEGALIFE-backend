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
  "rawContent": {"type": "doc", "content": []},
  "featuredImageUrl": "https://example.com/tofu-bowl.jpg",
  "categoryIds": ["7c9e6679-7425-40de-944b-e07fc1f90ae7"],
  "publish": true
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| title | string | Yes | Post title. Must not be blank and must be at most 255 characters. |
| content | string | Yes | Plain text used only as the semantic-filtering input (BR-FILTER-004); must not be blank. |
| rawContent | object | Yes | Rich-text document produced by the frontend editor; must be a JSON object. |
| featuredImageUrl | string or null | No | URL of the featured image. |
| categoryIds | array of UUID | No | Active categories; required when `publish` is `true` (BR-CONTENT-004). |
| publish | boolean | No | `false` (default) keeps the post as a private draft; `true` requests publication and is subject to content filtering (BR-POST-004). |

The client must not send `userId`, `status`, `flag`, `viewCount`, `type`, `videoUrl`, `mediaId`, or timestamps. The server sets, or no longer accepts, these values (`type`/`videoUrl`/`mediaId` were removed by issue #101; post↔media linkage is managed elsewhere, not at create time).

## Responses

### Success Response (201 Created)

```json
{
  "success": true,
  "message": "Post created successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "userId": "9f1c2f4a-8a3b-4c5d-9e6f-7a8b9c0d1e2f",
    "displayName": "John Doe",
    "avatarUrl": "https://res.cloudinary.com/vegalife/avatar/john.jpg",
    "title": "Vegan tofu bowl",
    "rawContent": {"type": "doc", "content": []},
    "featuredImageUrl": "https://example.com/tofu-bowl.jpg",
    "status": "processing",
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
| data.userId | uuid | Author's user id — taken from the JWT, never from the request body. |
| data.displayName | string | Author's display name. |
| data.avatarUrl | string or null | Author's avatar URL; `null` when the author has no profile row yet (BR-PROFILE-003). |
| data.title | string | Post title. |
| data.rawContent | object | Rich-text document produced by the frontend editor. |
| data.featuredImageUrl | string or null | Featured image URL, if supplied. |
| data.status | string | `drafted` without `publish: true` (a private draft), or `processing` with it — the post is not `published` until the filter passes or an admin approves (BR-POST-004, BR-FILTER-007). |
| data.rejectionReason | string or null | Always `null` here: a freshly created post has never been reviewed by an admin. Included because this endpoint returns the same `PostListResponse` shape. |
| data.viewCount | integer | Starts at `0`. |
| data.publishedAt | string or null | `null` until the post is published. |
| data.createdAt | string | Creation timestamp in ISO-8601 format. |

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Title blank/overlong, `content` blank or missing, `rawContent` missing or not a JSON object, unknown/inactive category, `publish: true` without a category | `Validation failed` or the specific rule message |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 404 | The authenticated user record no longer exists | `User not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- The author is always the user identified by the authenticated JWT.
- A new post is persisted with view count `0`, no publication timestamp, and status `drafted` (no `publish`) or `processing` (with `publish: true`).
- `publish: true` does not publish immediately: the post is stored as `processing` with no `flag` and an entry on the outbound outbox (`CONTENT_FILTER` channel); it becomes `published` only when the filter passes or an admin approves it (BR-POST-004, BR-FILTER-004, BR-FILTER-005, BR-FILTER-007).
- The post's `flag`/`flagDetail` verdict is **not** part of this member-facing response — it is exposed on the admin post endpoints only (ADR-025). Readers answer "can I see it?" from `status`.
- Filtering runs asynchronously; this endpoint never blocks on the embedding service (BR-FILTER-006).
- `content` stays the semantic-filtering input; `rawContent` is never read by the filter (BR-FILTER-005).
- Schema: the `flag` column is added by migration `V19__add_post_filtering.sql`; `raw_content` is added and `type`/`video_url` are dropped by migration `V27__add_post_raw_content_drop_type_video_url.sql`; `V56__post_processing_status_and_flag_blocker.sql` reshapes `status`/`flag` and adds `flag_detail`; `V57__store_post_flag_detail_as_json.sql` converts `flag_detail` to `JSONB` and enforces `flag_detail->>'kind' = flag` (ADR-024); `V58__keep_post_flag_after_publication.sql` adds `FILTER_PASSED` and allows a flag on any status except `drafted` (ADR-025).

## Example

```bash
curl -X POST http://localhost:8080/api/posts \
  -H "Authorization: Bearer <access-token>" \
  -H "Content-Type: application/json" \
  -d '{"title":"Vegan tofu bowl","content":"A simple plant-based lunch.","rawContent":{"type":"doc","content":[]},"featuredImageUrl":"https://example.com/tofu-bowl.jpg"}'
```

## Related

- List the authenticated user's posts: `docs/apis/post/get-posts.md`
- Shared error/response contract: `docs/apis/error-responses.md`
- Content filtering feature: `docs/feats/post-content-filtering.md`
- Database schema: `src/main/resources/db/migration/V7__create_post_tables.sql`, `src/main/resources/db/migration/V27__add_post_raw_content_drop_type_video_url.sql`
