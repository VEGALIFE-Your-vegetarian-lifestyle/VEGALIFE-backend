# API Reference: POST /api/posts/{postId}/recipes

## Overview

Replace the whole set of recipes attached to a post with the given set (issue #137).

## Endpoint

```text
POST /api/posts/{postId}/recipes
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`). The caller must own the post or hold the `ADMIN` role.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| postId | UUID | Yes | ID of the post to attach recipes to. |

### Request Body

```json
{
  "recipeIds": ["7c9e6679-7425-40de-944b-e07fc1f90ae7"]
}
```

| Field | Type | Description |
|-------|------|--------------|
| recipeIds | array of UUID | Required (the field itself, not each id — may be empty). Replaces the post's whole recipe set. Every id must reference an existing, non-deleted recipe whose `moderationStatus` is `APPROVED`. An empty array detaches every attached recipe. |

## Responses

### Success Response (200 OK)

Returns the updated post using `PostListResponse` (`id`, `title`, `rawContent`, `featuredImageUrl`, `categoryIds`, `mediaIds`, `recipeIds`, `status`, `rejectionReason`, `viewCount`, `publishedAt`, `createdAt`). `rejectionReason` is returned only to the post's author, so an Administrator attaching recipes to someone else's post gets `null`. The filter verdict (`flag`/`flagDetail`) is not part of this shape (ADR-025).

```json
{
  "success": true,
  "message": "Recipes attached successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "userId": "9f1c2f4a-8a3b-4c5d-9e6f-7a8b9c0d1e2f",
    "displayName": "John Doe",
    "avatarUrl": "https://res.cloudinary.com/vegalife/avatar/john.jpg",
    "mediaIds": [],
    "recipeIds": ["7c9e6679-7425-40de-944b-e07fc1f90ae7"],
    "status": "created"
  }
}
```

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `recipeIds` missing | `Validation failed` |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 404 | Post does not exist or is soft-deleted; for non-admins, also when it belongs to another user | `Post not found` |
| 404 | Any recipe id does not exist, is soft-deleted, or is not `APPROVED` | `One or more recipes are not found or are not approved` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Only the owner or an Administrator can attach recipes to a post, reusing the ownership rule from `PATCH /api/posts/{postId}` (`BR-POST-006`); a non-admin gets `404` for another user's post, same as edit.
- Only an `APPROVED` recipe can be attached (`BR-POST-013`). A `PENDING` or `REJECTED` recipe is refused with `404`, because a post must never become the way an unreviewed or refused recipe body reaches the public. This applies to Administrators too — approval happens in the moderation queue, not by attaching.
- Every requested id is validated, including ids already attached: a post that somehow holds a non-approved recipe cannot keep it by re-sending the same set. The recipe must be detached (or approved) first.
- Replace-the-full-set semantics: re-calling with the same set is idempotent, and a smaller set detaches the rest. Nothing is written if validation fails for any id in the request.
- Attaching recipes never changes the post's `status`, publication state (`publishedAt`), or its
  filter verdict — this is a pure association change.
- `post_recipe` is owned by `Recipe.posts`, not `Post`; the service mutates the recipe side (`recipe.getPosts()`) to keep the association consistent, but this is an implementation detail invisible to the API contract.
- Creating recipes and hard-deleting recipes are out of scope for this endpoint (issue #137 non-goals); recipes are created separately via `POST /api/recipes`.
- Deleting the post or the recipe later cascades the `post_recipe` row automatically (`ON DELETE CASCADE`); this endpoint never needs to clean that up.

## Example

```bash
curl -X POST http://localhost:8080/api/posts/550e8400-e29b-41d4-a716-446655440000/recipes \
  -H "Authorization: Bearer <access-token>" \
  -H "Content-Type: application/json" \
  -d '{"recipeIds":["7c9e6679-7425-40de-944b-e07fc1f90ae7"]}'
```

## Related

- Attach media: `docs/apis/post/post-posts-postid-media.md`
- Read a post: `docs/apis/post/get-posts-postid.md`
- Edit a post: `docs/apis/post/patch-posts-postid.md`
- Create a recipe: `docs/apis/recipes/post-recipes.md`
- Shared error/response contract: `docs/apis/error-responses.md`
