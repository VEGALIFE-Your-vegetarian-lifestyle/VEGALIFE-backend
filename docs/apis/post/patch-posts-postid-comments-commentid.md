# API Reference: PATCH /api/posts/{postId}/comments/{commentId}

## Overview

Edit the content of an existing comment. Only the comment's owner may edit it, and there is no time limit on when the edit can happen.

## Endpoint

```text
PATCH /api/posts/{postId}/comments/{commentId}
```

## Authentication

Required: a valid JWT access token in `Authorization: Bearer <token>`. The user ID is taken from the authenticated principal.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| postId | UUID | Yes | ID of the post the comment belongs to. |
| commentId | UUID | Yes | ID of the comment to edit. |

### Query Parameters

No query parameters.

### Request Body

```json
{
  "content": "This looks delicious! (edited for a typo)"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| content | string | Yes | New plain-text comment content; must not be blank. |

The caller must not send `userId`, `postId`, `commentId`, or `edited`; the server derives ownership from the JWT and the path, and manages the `edited` flag itself.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Comment updated successfully",
  "data": {
    "id": "98b73399-d47a-4ad3-a092-d5f5edc2c7a6",
    "postId": "85fb5914-f304-45c9-b44e-480e90ec67fa",
    "parentId": null,
    "userId": "249cf378-643a-4e56-b6ba-96ff2141eff0",
    "content": "This looks delicious! (edited for a typo)",
    "edited": true,
    "createdAt": "2026-10-10T12:00:00Z",
    "updatedAt": "2026-10-11T09:30:00Z"
  }
}
```

`data.edited` is `true` once the comment has been edited at least once, and stays `true` on every later edit.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `content` is blank, or `postId`/`commentId` is not a valid UUID | `Validation failed` |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 403 | The authenticated user is not the comment's owner | `Forbidden` |
| 404 | Comment is missing, deleted, or belongs to a different `postId` | `Comment not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Only the comment's owner may edit it; this includes Administrators — see `BR-COMMENT-002` in `docs/brs/comments.md`.
- Edits have no time limit relative to the comment's `createdAt`.
- Editing does not change `parentId`, `postId`, or `userId`.

## Related

- Feature spec: `docs/feats/edit-comments-api.md`
- Create endpoint: `docs/apis/post/post-posts-postid-comments.md`
