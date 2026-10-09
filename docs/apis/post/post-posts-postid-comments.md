# API Reference: POST /api/posts/{postId}/comments

## Overview

Create a plain-text top-level comment or nested reply on a published post.

## Endpoint

```text
POST /api/posts/{postId}/comments
```

## Authentication

Required: a valid JWT access token in `Authorization: Bearer <token>`. The user ID is taken from the authenticated principal.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| postId | UUID | Yes | ID of the published post to comment on. |

### Query Parameters

No query parameters.

### Request Body

```json
{
  "content": "This looks delicious!",
  "parentId": null
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| content | string | Yes | Plain-text comment content; must not be blank. |
| parentId | UUID or null | No | Omit or send `null` for a top-level comment. Set to an active comment ID on this post to create a reply. Replies may target another reply. |

The caller must not send `userId` or `postId`; the server derives these from the JWT and path.

## Responses

### Success Response (201 Created)

```json
{
  "success": true,
  "message": "Comment created successfully",
  "data": {
    "id": "98b73399-d47a-4ad3-a092-d5f5edc2c7a6",
    "postId": "85fb5914-f304-45c9-b44e-480e90ec67fa",
    "parentId": null,
    "userId": "249cf378-643a-4e56-b6ba-96ff2141eff0",
    "content": "This looks delicious!",
    "createdAt": "2026-10-10T12:00:00Z",
    "updatedAt": "2026-10-10T12:00:00Z"
  }
}
```

`data.parentId` is the thread position: `null` means a top-level comment; a UUID means the new comment is a reply under that comment.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `content` is blank, a request field is invalid, or `postId`/`parentId` is not a valid UUID | `Validation failed` |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 404 | Post is missing, deleted, or not published; parent comment is missing, removed, or belongs to another post | `Post not found` / `Parent comment not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Comment creation requires an authenticated user; see `BR-COMMENT-001` in `docs/brs/comments.md`.
- Only published, non-deleted posts can receive comments.
- A reply's parent must be active and belong to the same post. A reply may itself have replies.
- Public comment reading is not part of this endpoint or issue #41. The existing `GET /api/admin/comments` remains Admin-only.

## Related

- Feature spec: `docs/feats/create-comments-api.md`
- Admin comment listing: `GET /api/admin/comments` (`docs/apis/admin/get-comments.md`)
