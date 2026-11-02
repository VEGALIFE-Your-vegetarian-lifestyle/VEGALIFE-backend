# API Reference: POST /api/posts/{postId}/comments/{commentId}/report

## Overview

Report a comment, incrementing its report count by one (issue #6). There is no report table — no record of who reported what, and no de-duplication of repeat reports from the same user. Admins use the count to sort reported comments first on `GET /api/admin/comments`.

## Endpoint

```text
POST /api/posts/{postId}/comments/{commentId}/report
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`). Any authenticated user may report a comment, including its own author.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| postId | UUID | Yes | ID of the post the comment belongs to. |
| commentId | UUID | Yes | ID of the comment to report. |

### Request Body

No request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Comment reported successfully",
  "data": null
}
```

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | JWT is missing, invalid, or expired | `Unauthorized` |
| 404 | Comment does not exist, is already soft-deleted, or does not belong to `postId` | `Comment not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Reporting only increments a counter (`comment.report_count`); it never soft-deletes the comment or notifies anyone by itself.
- Calling this endpoint repeatedly, including by the same user, keeps incrementing the count — there is no per-user report record to de-duplicate against.
- Admins act on reported comments through the separate `POST /api/admin/comments/{commentId}/moderate` endpoint.

## Example

```bash
curl -X POST http://localhost:8080/api/posts/550e8400-e29b-41d4-a716-446655440000/comments/660e8400-e29b-41d4-a716-446655440001/report \
  -H "Authorization: Bearer <access-token>"
```

## Related

- Moderate a comment: `docs/apis/admin/post-comments-commentid-moderate.md`
- List comments, sorted by report count: `docs/apis/admin/get-comments.md`
- Shared error/response contract: `docs/apis/error-responses.md`
