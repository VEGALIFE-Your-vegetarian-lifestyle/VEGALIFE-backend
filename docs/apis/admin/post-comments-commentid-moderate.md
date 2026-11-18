# API Reference: POST /api/admin/comments/{commentId}/moderate

## Overview

Soft-delete a comment as a moderation action. Admin only (issue #6). Admin editing of comments and bulk moderation are out of scope — this is the only moderation action.

No reason is accepted and none is stored: the moderation log was removed (ADR-027).

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

None. The endpoint takes no payload; a client that still sends `{"reason": "..."}` is not broken,
because Spring Boot ignores unknown properties — the field is simply discarded.

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
| 401 | JWT is missing, invalid, or expired | `Unauthorized` |
| 403 | Authenticated but not an Administrator | `Forbidden` |
| 404 | Comment does not exist or is already soft-deleted | `Comment not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Only an Administrator may moderate a comment (BR-ADMIN-003); this is a separate admin-only action from the existing owner-or-admin `DELETE /api/posts/{postId}/comments/{commentId}`.
- Moderation is soft-delete only — `deletedAt` is set, replies keep their `parentId` so the thread shape survives, matching `DELETE /api/posts/{postId}/comments/{commentId}` (BR-COMMENT-003).
- No record of the action is kept: `moderation_log` was dropped (ADR-027), so the acting admin and any rationale are not stored.

## Example

```bash
curl -X POST http://localhost:8080/api/admin/comments/550e8400-e29b-41d4-a716-446655440000/moderate \
  -H "Authorization: Bearer <admin-access-token>"
```

## Related

- List comments, sorted by report count: `docs/apis/admin/get-comments.md`
- Report a comment: `docs/apis/post/post-posts-postid-comments-commentid-report.md`
- Owner/admin soft-delete: `docs/apis/post/delete-posts-postid-comments-commentid.md`
- Shared error/response contract: `docs/apis/error-responses.md`
