# API Reference: POST /api/admin/posts/{postId}/moderate

## Overview

Administrator moderation: publish a filter-withheld post or unpublish a live one, recording the
admin and an optional reason (BR-POST-012, BR-ADMIN-002).

## Endpoint

```text
POST /api/admin/posts/{postId}/moderate
```

## Authentication

Required: a valid JWT access token with the `ADMIN` role. Enforced by
`@PreAuthorize("hasRole('ADMIN')")` on `AdminPostController` (ADR-009); other roles receive
`403 Forbidden`.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| postId | UUID | Yes | ID of the post to publish or unpublish. |

### Query Parameters

None.

### Request Body

```json
{
  "action": "PUBLISH",
  "reason": "Vegan recipe, filter false positive"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| action | string | Yes | `PUBLISH` or `UNPUBLISH`. |
| reason | string | No | Why the action was taken; stored on the moderation log. Max 1000 chars. |

## Responses

### Success Response (200 OK)

Returns the post as `AdminPostListResponse`. `flag` is unchanged by moderation.

```json
{
  "success": true,
  "message": "Post published successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "Vegan pho at home",
    "status": "published",
    "flag": "REJECTED",
    "publishedAt": "2026-10-10T12:00:00Z",
    "createdAt": "2026-10-09T08:00:00Z",
    "userId": "9f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f",
    "username": "jane",
    "email": "jane@example.com"
  }
}
```

The message is `Post unpublished successfully` when `action` is `UNPUBLISH`; `publishedAt` is then
`null`.

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | `Post published successfully` / `Post unpublished successfully` |
| data | object | The moderated post, same shape as `GET /api/admin/posts` items |

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `action` missing; `PUBLISH` on a post with no category; post is `hidden`; `reason` over 1000 chars | `Validation failed` / `At least one category is required to publish a post` / `A hidden post is moderated through the visibility endpoint, not publish/unpublish` |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 403 | Caller is not an Administrator | `Forbidden` |
| 404 | Post does not exist or is soft-deleted | `Post not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Only an Administrator may publish or unpublish a post (BR-POST-012).
- `PUBLISH` sets `status = published` and `publishedAt = now`; `UNPUBLISH` sets
  `status = unpublished` and clears `publishedAt`. Neither touches `flag` (ADR-011).
- `PUBLISH` requires at least one category (BR-CONTENT-003).
- A `hidden` post is moderated through `PATCH /api/posts/{postId}/visibility`, not here.
- A request whose target status already holds is a no-op: `200` with the post unchanged and no
  `moderation_log` entry.
- Every real change writes a `moderation_log` row (`PUBLISH_POST` / `UNPUBLISH_POST`, actor, target,
  reason) (BR-ADMIN-002).

## Example

### Request
```bash
curl -X POST http://localhost:8080/api/admin/posts/550e8400-e29b-41d4-a716-446655440000/moderate \
  -H "Authorization: Bearer <admin-access-token>" \
  -H "Content-Type: application/json" \
  -d '{"action":"PUBLISH","reason":"Vegan recipe, filter false positive"}'
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Post published successfully",
  "data": { "id": "550e8400-e29b-41d4-a716-446655440000", "status": "published", "flag": "REJECTED" }
}
```

### Error Response (400)
```json
{
  "success": false,
  "message": "At least one category is required to publish a post",
  "data": null
}
```

## Related

- Feature spec: `docs/feats/moderate-posts-admin-api.md`
- Driving issue: https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/5
- Business rules: `docs/brs/posts.md` (BR-POST-012, BR-ADMIN-002)
- List posts (Admin): `docs/apis/admin/get-posts.md`
- Hide or unhide a post (Admin): `docs/apis/post/patch-posts-postid-visibility.md`
- Shared error/response contract: `docs/apis/error-responses.md`
