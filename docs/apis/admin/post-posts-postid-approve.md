# API Reference: Admin Moderation of Post Components

## Overview

Two endpoints replace `POST /api/admin/posts/{postId}/moderate` and one is added.
Together they let an administrator see why a post was withheld and decide on it,
including the dishes, ingredients and recipe the post carries.

| Endpoint | Purpose |
|---|---|
| `GET /api/admin/posts/{postId}` | Full detail: scores and referenced component states |
| `POST /api/admin/posts/{postId}/approve` | Publish the post and approve pending components |
| `POST /api/admin/posts/{postId}/reject` | Unpublish the post and reject pending components |

**Breaking change.** `POST /api/admin/posts/{postId}/moderate` is removed. Clients
must switch to `/approve` or `/reject` in the same release.

## Authentication

All three require a valid JWT access token with the `ADMIN` role, enforced by
`@PreAuthorize("hasRole('ADMIN')")` on `AdminPostController`
([ADR-009](../../adrs/009-authorize-endpoints-with-method-security.md)). Other
roles receive `403 Forbidden`.

## GET /api/admin/posts/{postId}

### Response (200 OK)

```json
{
  "success": true,
  "message": "Post retrieved successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "Vegan pho at home",
    "content": "…",
    "status": "unpublished",
    "flag": "NEEDS_REVIEW",
    "contentScore": 0.8123,
    "publishedAt": null,
    "createdAt": "2026-10-23T08:00:00Z",
    "userId": "9f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f",
    "username": "jane",
    "components": [
      {
        "id": "0b0f8c1e-1f2a-4c3d-9e8f-7a6b5c4d3e2f",
        "type": "RECIPE",
        "name": "Phở chay",
        "moderationStatus": "PENDING",
        "score": 0.6104
      },
      {
        "id": "6c2d3e4f-5a6b-7c8d-9e0f-1a2b3c4d5e6f",
        "type": "DISH",
        "name": "phở chay",
        "moderationStatus": "PENDING",
        "score": null
      },
      {
        "id": "7d3e4f5a-6b7c-8d9e-0f1a-2b3c4d5e6f70",
        "type": "INGREDIENT",
        "name": "cocain",
        "moderationStatus": "PENDING",
        "score": null
      }
    ]
  }
}
```

| Field | Type | Description |
|---|---|---|
| contentScore | number \| null | Relevance score for `title + content`. `null` = never scored. |
| components[].type | string | `RECIPE`, `DISH` or `INGREDIENT`. |
| components[].moderationStatus | string | `PENDING`, `APPROVED` or `REJECTED`. |
| components[].score | number \| null | The recipe's score for `RECIPE`; `null` for dish and ingredient, which are not scored. |

Components are assembled at read time from the recipe, dish and ingredient rows
the post references. They are not stored.

## POST /api/admin/posts/{postId}/approve

### Request Body

```json
{ "reason": "Confirmed vegan recipe, ingredient names valid" }
```

| Field | Type | Required | Description |
|---|---|---|---|
| reason | string | No | Stored on the moderation log. Max 1000 chars. |

### Behaviour

- Sets `status = published`, `publishedAt = now`.
- Sets every referenced `PENDING` dish, ingredient and recipe to `APPROVED`.
- Does **not** delete anything.
- Requires at least one category, as before.

### Responses

| Status | Condition | Message |
|---|---|---|
| 200 | Approved | `Post approved successfully` |
| 400 | No category | `At least one category is required to publish a post` |
| 400 | Post is `hidden` | `A hidden post is approved through the visibility endpoint` |
| 404 | Missing or soft-deleted | `Post not found` |

## POST /api/admin/posts/{postId}/reject

### Request Body

```json
{ "reason": "Contains a non-food ingredient" }
```

### Behaviour

- Sets `status = unpublished`, clears `publishedAt`.
- Sets referenced rows that are still `PENDING` to `REJECTED`.
- Leaves already-`APPROVED` rows untouched — a shared name does not become bad
  because one post reused it.
- Deletes nothing.

### Responses

| Status | Condition | Message |
|---|---|---|
| 200 | Rejected | `Post rejected successfully` |
| 400 | Post is `hidden` | `A hidden post is rejected through the visibility endpoint` |
| 404 | Missing or soft-deleted | `Post not found` |

## Business Rules

- Approval and rejection are Admin-only (BR-POST-012).
- Every real change writes a `moderation_log` row (`APPROVE_POST` /
  `REJECT_POST`) with actor, target and reason (BR-ADMIN-002).
- A `hidden` post is handled through `PATCH /api/posts/{postId}/visibility`.
- Approving approves the referenced names; rejecting rejects only the pending
  ones (BR-DISH-001).
- Neither endpoint changes `flag`: an approval does not rewrite the filter's
  verdict, and a rejection is a visibility decision, not a re-scoring.

## Related

- [Business rule: dish and ingredient name approval](../../brs/dish-ingredient-name-approval.md)
- [Feature spec: component-level verification](../../feats/component-level-verification.md)
- [ADR-016: Component-aware moderation](../../adrs/016-component-level-moderation.md)
- List posts (Admin): [`admin/get-posts.md`](get-posts.md)
- Hide or unhide a post: `docs/apis/post/patch-posts-postid-visibility.md`
