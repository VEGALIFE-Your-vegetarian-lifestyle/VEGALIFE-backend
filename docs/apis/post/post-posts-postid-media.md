# API Reference: POST /api/posts/{postId}/media

## Overview

Replace the whole set of media attached to a post with the given set (issue #137).

## Endpoint

```text
POST /api/posts/{postId}/media
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`). The caller must own the post or hold the `ADMIN` role.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| postId | UUID | Yes | ID of the post to attach media to. |

### Request Body

```json
{
  "mediaIds": ["7c9e6679-7425-40de-944b-e07fc1f90ae7"]
}
```

| Field | Type | Description |
|-------|------|--------------|
| mediaIds | array of UUID | Required (the field itself, not each id — may be empty). Replaces the post's whole media set. Every id must reference existing, non-deleted media with `status = succeed`. An empty array detaches every attached media. |

## Responses

### Success Response (200 OK)

Returns the updated post using `PostListResponse` (`id`, `title`, `rawContent`, `featuredImageUrl`, `categoryIds`, `mediaIds`, `recipeIds`, `status`, `flag`, `viewCount`, `publishedAt`, `createdAt`).

```json
{
  "success": true,
  "message": "Media attached successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "mediaIds": ["7c9e6679-7425-40de-944b-e07fc1f90ae7"],
    "recipeIds": [],
    "status": "created"
  }
}
```

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `mediaIds` missing, or any id does not exist, is soft-deleted, or has not finished uploading (`status != succeed`) | `Validation failed` or `One or more media do not exist, are deleted, or have not finished uploading` |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 404 | Post does not exist or is soft-deleted; for non-admins, also when it belongs to another user | `Post not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Only the owner or an Administrator can attach media to a post, reusing the ownership rule from `PATCH /api/posts/{postId}` (`BR-POST-006`); a non-admin gets `404` for another user's post, same as edit.
- Replace-the-full-set semantics: re-calling with the same set is idempotent, and a smaller set detaches the rest. Nothing is written if validation fails for any id in the request.
- Attaching media never changes the post's `status`, publication state (`publishedAt`), or triggers content filtering (`flag` is untouched) — this is a pure association change.
- Creating media, hard-deleting media, media ordering, cover images, and captions are out of scope for this endpoint (issue #137 non-goals); media is created separately via `POST /api/media/upload` and `POST /api/media/{mediaId}/confirm`.
- Deleting the post or the media later cascades the `post_media` row automatically (`ON DELETE CASCADE`); this endpoint never needs to clean that up.

## Example

```bash
curl -X POST http://localhost:8080/api/posts/550e8400-e29b-41d4-a716-446655440000/media \
  -H "Authorization: Bearer <access-token>" \
  -H "Content-Type: application/json" \
  -d '{"mediaIds":["7c9e6679-7425-40de-944b-e07fc1f90ae7"]}'
```

## Related

- Attach a recipe: `docs/apis/post/post-posts-postid-recipes.md`
- Read a post: `docs/apis/post/get-posts-postid.md`
- Edit a post: `docs/apis/post/patch-posts-postid.md`
- Upload media: `docs/apis/media/post-upload.md`, `docs/apis/media/post-media-mediaid-confirm.md`
- Shared error/response contract: `docs/apis/error-responses.md`
