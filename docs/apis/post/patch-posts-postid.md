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

`content` and `rawContent` are required on every edit; `title`, `featuredImageUrl`, `categoryIds`, and `publish` stay a partial update — omitted or null fields are left unchanged.

```json
{
  "title": "Updated vegan tofu bowl",
  "content": "Updated plant-based lunch recipe.",
  "rawContent": {"type": "doc", "content": []},
  "featuredImageUrl": "https://example.com/updated-tofu-bowl.jpg",
  "categoryIds": ["7c9e6679-7425-40de-944b-e07fc1f90ae7"],
  "publish": true
}
```

| Field | Type | Description |
|-------|------|-------------|
| title | string | New title; not blank, at most 255 characters. Optional, left unchanged when omitted. |
| content | string | Required on every request. Plain text used only as the semantic-filtering input; must not be blank. |
| rawContent | object | Required on every request. Rich-text document produced by the frontend editor; must be a JSON object. |
| featuredImageUrl | string | New featured image URL. Optional, left unchanged when omitted. |
| categoryIds | array of UUID | Replaces the whole category set; every category must exist and be active (BR-CONTENT-004). Optional, left unchanged when omitted. |
| publish | boolean | `true` publishes, `false` returns the post to a private draft (BR-CONTENT-003). Optional, left unchanged when omitted. |

## Responses

### Success Response (200 OK)

Returns the updated post using `PostListResponse` (`id`, `title`, `rawContent`, `featuredImageUrl`, `categoryIds`, `mediaIds`, `status`, `flag`, `viewCount`, `publishedAt`, `createdAt`). `flag` is the content filter state: `null` (never filtered), `PENDING`, `PASSED`, `REJECTED`, or `NEEDS_REVIEW`.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `content` or `rawContent` missing/invalid; invalid title; unknown/inactive category; publishing without a category; non-admin changing a `hidden` post | `Validation failed` or the specific rule message |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 404 | Post does not exist or is soft-deleted; for non-admins, also when it belongs to another user | `Post not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Only the owner or an Administrator can edit a post; a non-admin gets `404` for others' posts.
- Every edit an Administrator makes to another user's post is written to `moderation_log` (BR-ADMIN-002).
- Publishing requires at least one active category; a published post cannot lose its last category.
- `publish: true` queues the post for filtering rather than publishing it directly: the post goes to `flag: PENDING` and waits for filtering (BR-POST-004). Unpublishing (`publish: false`) withdraws the post immediately, sets status `created`, clears `publishedAt`, and returns it to a private draft — a `flagged` post keeps its `flag` (BR-POST-007).
- A title or content change to a published or flagged post re-queues semantic filtering and returns the post to `flag: PENDING` (BR-POST-007). `rawContent` is never read by the filter and a `rawContent`-only change does not re-queue.
- A `REJECTED` or `NEEDS_REVIEW` post is returned to the owner as status `flagged`, not `published` (BR-POST-010, BR-FILTER-008).
- Filtering itself is never triggered synchronously by this endpoint; it runs through the async outbox (BR-FILTER-006).
- Schema: migration `V18__create_moderation_log.sql` adds `moderation_log`; migration `V19__add_post_filtering.sql` adds the `flag` column exposed in the response; migration `V27__add_post_raw_content_drop_type_video_url.sql` adds `raw_content` and drops `type`/`video_url`.

## Example

```bash
curl -X PATCH http://localhost:8080/api/posts/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer <access-token>" \
  -H "Content-Type: application/json" \
  -d '{"title":"Updated vegan tofu bowl","content":"Updated plant-based lunch recipe.","rawContent":{"type":"doc","content":[]}}'
```

## Related

- Feature spec: `docs/feats/edit-user-post.md`
- Business rules: `docs/brs/posts.md`
- Create a post: `docs/apis/post/post-posts.md`
- Shared error/response contract: `docs/apis/error-responses.md`
