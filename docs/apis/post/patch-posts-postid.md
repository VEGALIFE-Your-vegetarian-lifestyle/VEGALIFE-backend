# API Reference: PATCH /api/posts/{postId}

## Overview

Partially update a non-deleted post. The owner or an Administrator may edit (BR-CONTENT-001).

## Endpoint

```text
PATCH /api/posts/{postId}
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`). The caller must own the post or hold the `ADMIN` role.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| postId | UUID | Yes | ID of the post to edit. |

### Request Body

Supply at least one non-null field. Omitted and null fields are left unchanged.

```json
{
  "title": "Updated vegan tofu bowl",
  "content": "Updated plant-based lunch recipe.",
  "featuredImageUrl": "https://example.com/updated-tofu-bowl.jpg",
  "categoryIds": ["7c9e6679-7425-40de-944b-e07fc1f90ae7"],
  "publish": true
}
```

| Field | Type | Description |
|-------|------|-------------|
| title | string | New title; not blank, at most 255 characters. |
| content | string | New post text; not blank. |
| featuredImageUrl | string | New featured image URL. |
| videoUrl | string | Video posts only: replaces the video link; not blank. |
| mediaId | UUID | Video posts only: replaces the uploaded video; media must exist and be `succeed`. |
| categoryIds | array of UUID | Replaces the whole category set; every category must exist and be active (BR-CONTENT-004). |
| publish | boolean | `true` publishes, `false` returns the post to a private draft (BR-CONTENT-003). |
| type | string | Immutable (BR-CONTENT-002). Accepted only if equal to the current type. |

## Responses

### Success Response (200 OK)

Returns the updated post using `PostListResponse` (`id`, `title`, `type`, `content`, `featuredImageUrl`, `videoUrl`, `categoryIds`, `mediaIds`, `status`, `viewCount`, `publishedAt`, `createdAt`).

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | All fields omitted/null; invalid title/content; different `type`; video fields on a blog; unknown/inactive category; media not ready; publishing without category (or, for video, without video file/link); non-admin changing a `hidden` post | `Validation failed` or the specific rule message |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 404 | Post does not exist or is soft-deleted; for non-admins, also when it belongs to another user; referenced media not found | `Post not found` / `Media not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Only the owner or an Administrator can edit a post; a non-admin gets `404` for others' posts.
- Every edit an Administrator makes to another user's post is written to `moderation_log` (BR-ADMIN-002).
- Publishing requires at least one active category and the information required for the post type; a published post cannot lose its last category.
- Unpublishing sets status `created` and clears `publishedAt`.
- The endpoint does not trigger semantic filtering (deferred).
- Schema: migration `V18__create_moderation_log.sql` adds `moderation_log`.

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
