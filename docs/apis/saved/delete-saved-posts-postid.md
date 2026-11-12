# API Reference: DELETE /api/saved/posts/{postId}

## Overview

Removes a post from the caller's saved list. A post that is not saved is a successful no-op
(BR-SAVED-001).

## Endpoint

```text
DELETE /api/saved/posts/{postId}
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`).

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| postId | UUID | Yes | ID of the post to unsave. |

### Request Body

No request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Post removed from saved items",
  "data": null
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | Human-readable confirmation |
| data | null | The action returns no payload |

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Unsaving is idempotent and never fails because the row is already gone (`BR-SAVED-001`) — notably,
  unsaving a post that was unpublished and therefore no longer in the list still succeeds.
- The post itself is untouched: this endpoint deletes only the caller's `saved_item` row.
- Unsave is not scoped to visibility, unlike save: a member can always remove their own bookmark,
  whatever state the post has since reached.

## Example

```bash
curl -X DELETE http://localhost:8080/api/saved/posts/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer <access-token>"
```

## Related

- Save a post: `docs/apis/saved/post-saved-posts-postid.md`
- List saved posts: `docs/apis/saved/get-saved-posts.md`
- Business rules: `docs/brs/saved-items.md`
