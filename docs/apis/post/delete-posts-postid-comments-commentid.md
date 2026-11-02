# API Reference: DELETE /api/posts/{postId}/comments/{commentId}

## Overview

Soft-delete a comment. The owner or an Administrator may delete it (`BR-COMMENT-003`).

## Endpoint

```text
DELETE /api/posts/{postId}/comments/{commentId}
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`). The caller must own the comment or hold the `ADMIN` role.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| postId | UUID | Yes | ID of the post the comment belongs to. |
| commentId | UUID | Yes | ID of the comment to delete. |

No query parameters or request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Comment deleted successfully",
  "data": null
}
```

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 404 | Comment does not exist, is already deleted, or belongs to a different `postId`; for non-admins, also when it belongs to another user | `Comment not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Deletion is soft: `deleted_at` is set and the row is kept. A deleted comment's replies are untouched and keep pointing at it via `parent_id`, so the thread shape survives.
- A non-admin gets `404` for another user's comment, so existence is not disclosed.
- An Administrator deleting someone else's comment is written to `moderation_log` with action `DELETE_COMMENT` (`BR-ADMIN-002`). An Administrator deleting their own comment is not logged (not an override).
- No new migration: uses the existing `comment.deleted_at` column (`V8`) and `moderation_log` table.
- This endpoint does not render "[deleted]" anywhere — there is no public comment-thread read endpoint yet. See `docs/feats/delete-comments-api.md`'s open questions.

## Example

```bash
curl -X DELETE http://localhost:8080/api/posts/550e8400-e29b-41d4-a716-446655440000/comments/660e8400-e29b-41d4-a716-446655440111 \
  -H "Authorization: Bearer <access-token>"
```

## Related

- Feature spec: `docs/feats/delete-comments-api.md`
- Business rules: `docs/brs/comments.md`
- Edit a comment: `docs/apis/post/patch-posts-postid-comments-commentid.md`
- Create a comment: `docs/apis/post/post-posts-postid-comments.md`
- Shared error/response contract: `docs/apis/error-responses.md`
