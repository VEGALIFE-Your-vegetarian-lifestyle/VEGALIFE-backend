# API Reference: POST /api/saved/posts/{postId}

## Overview

Saves a published post to the caller's private saved list. Repeat calls succeed and keep the original
save time (BR-SAVED-001).

## Endpoint

```text
POST /api/saved/posts/{postId}
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`).

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| postId | UUID | Yes | ID of the post to save. It must be published and not soft-deleted. |

### Request Body

No request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Post saved successfully",
  "data": null
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | Human-readable confirmation |
| data | null | The action returns no payload |

Saving an already-saved post returns the same response and does not change its `saved_at`.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 404 | The post does not exist, is soft-deleted, or is not `published` | `Post not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- A saved item is private to the member who saved it, and saving is idempotent (`BR-SAVED-001`).
- Only a published, non-deleted post can be saved; anything else is reported as not found rather than
  confirming it exists (`BR-SAVED-002`, `BR-POST-010`).
- Saving a post has no effect on the post's view count, votes, status, or `flag`.

## Example

```bash
curl -X POST http://localhost:8080/api/saved/posts/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer <access-token>"
```

```json
{
  "success": true,
  "message": "Post saved successfully",
  "data": null
}
```

## Related

- List saved posts: `docs/apis/saved/get-saved-posts.md`
- Unsave a post: `docs/apis/saved/delete-saved-posts-postid.md`
- Business rules: `docs/brs/saved-items.md`
- Feature spec: `docs/feats/saved-items.md`
