# API Reference: DELETE /api/posts/{postId}

## Overview

Soft-delete a post. The owner or an Administrator may delete (BR-CONTENT-001).

## Endpoint

```text
DELETE /api/posts/{postId}
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`). The caller must own the post or hold the `ADMIN` role.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| postId | UUID | Yes | ID of the post to delete. |

No query parameters or request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Post deleted successfully",
  "data": null
}
```

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 404 | Post does not exist or is already deleted; for non-admins, also when it belongs to another user | `Post not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Deletion is soft: `deleted_at` is set and the row is kept. Deleted posts no longer appear in `GET /api/posts` and cannot be edited or deleted again.
- A non-admin gets `404` for other users' posts, so existence is not disclosed.
- Every deletion an Administrator makes to another user's post is written to `moderation_log` with action `DELETE_POST` (BR-ADMIN-002).
- No new migration: uses the existing `post.deleted_at` column and `moderation_log` from `V18`.

## Example

```bash
curl -X DELETE http://localhost:8080/api/posts/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer <access-token>"
```

## Related

- Feature spec: `docs/feats/delete-user-post.md`
- Business rules: `docs/brs/posts.md`
- Edit a post: `docs/apis/post/patch-posts-postid.md`
- Shared error/response contract: `docs/apis/error-responses.md`
