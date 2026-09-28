# API Reference: PATCH /api/posts/{postId}/visibility

## Overview

Administrator moderation: hide a post from the platform or lift the hide (BR-ADMIN-002).

## Endpoint

```text
PATCH /api/posts/{postId}/visibility
```

## Authentication

Required: a valid JWT access token with the `ADMIN` role. Enforced in `SecurityConfig`; other roles receive `403 Forbidden`.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| postId | UUID | Yes | ID of the post to hide or unhide. |

### Request Body

```json
{ "hidden": true }
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| hidden | boolean | Yes | `true` hides the post; `false` lifts the hide. |

## Responses

### Success Response (200 OK)

Returns the post as `PostListResponse` with the new `status`.

```json
{
  "success": true,
  "message": "Post hidden successfully",
  "data": { "id": "550e8400-e29b-41d4-a716-446655440000", "status": "hidden", "publishedAt": null }
}
```

The message is `Post is visible again` when `hidden` is `false`.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `hidden` missing; `hidden: false` on a post that is not hidden | `Validation failed` / `Post is not hidden` |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 403 | Caller is not an Administrator | `Forbidden` |
| 404 | Post does not exist or is soft-deleted | `Post not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Hiding sets status `hidden` and clears `publishedAt`. Hiding an already hidden post is a no-op (`200`, no log entry).
- Lifting the hide returns the post to `created` (private draft); the owner must publish it again under BR-CONTENT-003.
- While hidden, the owner cannot change the post's publish state (`PATCH /api/posts/{postId}` with `publish`); only an Administrator can.
- Every hide and unhide is recorded in `moderation_log` as `HIDE_POST` / `UNHIDE_POST`, including on the administrator's own posts.

## Example

```bash
curl -X PATCH http://localhost:8080/api/posts/550e8400-e29b-41d4-a716-446655440000/visibility \
  -H "Authorization: Bearer <admin-access-token>" \
  -H "Content-Type: application/json" \
  -d '{"hidden":true}'
```

## Related

- Feature spec: `docs/feats/hide-user-post.md`
- Business rules: `docs/brs/posts.md`
- Edit a post: `docs/apis/post/patch-posts-postid.md`
