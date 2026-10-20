# API Reference: POST /api/admin/comments/{commentId}/moderate

## Overview

Soft-delete a comment as a moderation action, with a required reason recorded on the moderation log. Admin only (issue #6). Admin editing of comments and bulk moderation are out of scope — this is the only moderation action.

## Endpoint

```text
POST /api/admin/comments/{commentId}/moderate
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`), role `ADMIN`.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| commentId | UUID | Yes | ID of the comment to moderate. |

### Request Body

```json
{
  "reason": "Violates community guidelines on harassment"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| reason | string | Yes | Why the comment is being moderated, at most 1000 characters. Unlike post moderation (`docs/apis/admin/post-posts-postid-moderate.md`), a reason is required here, not optional — issue #6 makes it an acceptance criterion. |

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Comment moderated successfully",
  "data": null
}
```

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `reason` missing/blank or over 1000 characters | `Validation failed` |
| 401 | JWT is missing, invalid, or expired | `Unauthorized` |
| 403 | Authenticated but not an Administrator | `Forbidden` |
| 404 | Comment does not exist or is already soft-deleted | `Comment not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Only an Administrator may moderate a comment (BR-ADMIN-003); this is a separate admin-only action from the existing owner-or-admin `DELETE /api/posts/{postId}/comments/{commentId}`, used when action needs to be auditable with a reason regardless of which post the comment is on.
- Moderation is soft-delete only — `deletedAt` is set, replies keep their `parentId` so the thread shape survives, matching `DELETE /api/posts/{postId}/comments/{commentId}` (BR-COMMENT-003).
- Every moderation action writes a `moderation_log` row: `actorId` (the admin), `action = MODERATE_COMMENT`, `targetType = COMMENT`, `targetId`, and `reason` — BR-ADMIN-002.

## Example

```bash
curl -X POST http://localhost:8080/api/admin/comments/550e8400-e29b-41d4-a716-446655440000/moderate \
  -H "Authorization: Bearer <admin-access-token>" \
  -H "Content-Type: application/json" \
  -d '{"reason":"Violates community guidelines on harassment"}'
```

## Related

- List comments, sorted by report count: `docs/apis/admin/get-comments.md`
- Report a comment: `docs/apis/post/post-posts-postid-comments-commentid-report.md`
- Owner/admin soft-delete: `docs/apis/post/delete-posts-postid-comments-commentid.md`
- Shared error/response contract: `docs/apis/error-responses.md`
