# API Reference: GET /api/posts/{postId}/comments

## Overview

Return a published post's comments as a nested thread: paginated root comments, each with its full reply subtree. Public — no authentication required (`BR-COMMENT-001`).

## Endpoint

```text
GET /api/posts/{postId}/comments
```

## Authentication

None required.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| postId | UUID | Yes | ID of the published post whose comments to list. |

### Query Parameters

| Name | Type | Required | Default | Description |
|------|------|----------|---------|-------------|
| page | integer | No | 0 | Zero-based page index over root (top-level) comments. |
| size | integer | No | 20 | Page size for root comments, 1-100. |

Replies are not paginated: each root comment in the page carries its entire reply subtree.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Comments retrieved successfully",
  "data": {
    "content": [
      {
        "id": "98b73399-d47a-4ad3-a092-d5f5edc2c7a6",
        "userId": "249cf378-643a-4e56-b6ba-96ff2141eff0",
        "username": "veganchef",
        "avatarUrl": "https://cdn.example.com/avatars/veganchef.png",
        "content": "This looks delicious!",
        "edited": false,
        "deleted": false,
        "createdAt": "2026-10-10T12:00:00Z",
        "updatedAt": "2026-10-10T12:00:00Z",
        "replies": [
          {
            "id": "a1b2c3d4-0000-0000-0000-000000000001",
            "userId": null,
            "username": null,
            "avatarUrl": null,
            "content": "[deleted]",
            "edited": false,
            "deleted": true,
            "createdAt": "2026-10-10T12:05:00Z",
            "updatedAt": "2026-10-11T09:00:00Z",
            "replies": [
              {
                "id": "a1b2c3d4-0000-0000-0000-000000000002",
                "userId": "c7d8e9f0-0000-0000-0000-000000000003",
                "username": "another_user",
                "avatarUrl": null,
                "content": "Still visible even though the parent was removed",
                "edited": true,
                "deleted": false,
                "createdAt": "2026-10-10T12:10:00Z",
                "updatedAt": "2026-10-10T12:20:00Z",
                "replies": []
              }
            ]
          }
        ]
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `page`/`size` out of range, or `postId` is not a valid UUID | `Validation failed` |
| 404 | Post is missing, deleted, or not published | `Post not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Reading comments requires no authentication (`BR-COMMENT-001`); allowlisted as `permitAll` for `GET` in `SecurityConfig`, per ADR-009.
- Only published, non-deleted posts expose their comments (same visibility rule as `POST .../comments`).
- A soft-deleted comment (`BR-COMMENT-003`) is rendered as a `"[deleted]"` placeholder with no author fields, but its replies remain nested in place.
- `avatarUrl` is `null` when the author has no profile row yet (`BR-PROFILE-003`).

## Related

- Feature spec: `docs/feats/list-comments-api.md`
- Create a comment: `docs/apis/post/post-posts-postid-comments.md`
- Edit a comment: `docs/apis/post/patch-posts-postid-comments-commentid.md`
- Delete a comment: `docs/apis/post/delete-posts-postid-comments-commentid.md`
- Shared error/response contract: `docs/apis/error-responses.md`
