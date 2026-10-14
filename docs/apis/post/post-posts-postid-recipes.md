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
| recipeIds | array of UUID | Required (the field itself, not each id — may be empty). Replaces the post's whole recipe set. Every id must reference an existing, non-deleted recipe. An empty array detaches every attached recipe. |

## Responses

### Success Response (200 OK)

Returns the updated post using `PostListResponse` (`id`, `title`, `rawContent`, `featuredImageUrl`, `categoryIds`, `mediaIds`, `recipeIds`, `status`, `flag`, `viewCount`, `publishedAt`, `createdAt`).

```json
{
  "success": true,
  "message": "Recipes attached successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "mediaIds": [],
    "recipeIds": ["7c9e6679-7425-40de-944b-e07fc1f90ae7"],
    "status": "created"
  }
}
```

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `recipeIds` missing, or any id does not exist or is soft-deleted | `Validation failed` or `One or more recipes do not exist or are deleted` |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 404 | Post does not exist or is soft-deleted; for non-admins, also when it belongs to another user | `Post not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Only the owner or an Administrator can attach recipes to a post, reusing the ownership rule from `PATCH /api/posts/{postId}` (`BR-POST-006`); a non-admin gets `404` for another user's post, same as edit.
- Replace-the-full-set semantics: re-calling with the same set is idempotent, and a smaller set detaches the rest. Nothing is written if validation fails for any id in the request.
- Attaching recipes never changes the post's `status`, publication state (`publishedAt`), or triggers content filtering (`flag` is untouched) — this is a pure association change.
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
