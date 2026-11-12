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
    "status": "processing",
    "flag": "FILTER_FAILED",
    "flagDetail": "relevance 0.3120 below the 0.45 reject threshold",
    "rejectionReason": null,
    "contentScore": 0.3120,
    "userId": "9f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f",
    "displayName": "jane",
    "email": "jane@example.com",
    "recipes": [
      {
        "id": "0b0f8c1e-1f2a-4c3d-9e8f-7a6b5c4d3e2f",
        "type": "RECIPE",
        "name": "Phở chay",
        "moderationStatus": "PENDING",
        "score": 0.6104
      }
    ],
    "names": [
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
| status | string | `drafted`, `processing`, `published` or `unpublished`. A post awaiting a decision is `processing`. |
| flag | string \| null | Why the post is being held: `FILTER_FAILED`, `SIMILAR_CONTENT` or `FILTER_SKIPPED`; `null` when no decision is outstanding (ADR-023). |
| flagDetail | string \| null | Machine-readable detail behind `flag`: the failing score, `SIMILAR_CONTENT_POST_<postId>`, `FILTER_DISABLED` or `FILTER_SWEPT`. |
| rejectionReason | string \| null | Why an admin rejected this post. Cleared when the post is approved. `null` = no reason recorded. |
| recipes | array | Recipe bodies the post attaches, each with its own score. Empty when none is attached. |
| names | array | Every dish and ingredient name the post references. |
| recipes[].type / names[].type | string | `RECIPE`, `DISH` or `INGREDIENT`. |
| recipes[].moderationStatus, names[].moderationStatus | string | `PENDING`, `APPROVED` or `REJECTED`. |
| recipes[].score | number \| null | The recipe's score; `null` for dish and ingredient names, which are not scored. |

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

- Sets `status = published` and stamps `publishedAt` (`now` when it was null; a re-check
  keeps the original publication time).
- Clears `flag`, `flagDetail` and `rejectionReason` — the post is no longer held, and a
  stale reason would describe a decision that no longer stands.
- Sets every referenced `PENDING` dish, ingredient and recipe to `APPROVED`.
- Does **not** delete anything.
- Requires at least one category, as before.

### Responses

| Status | Condition | Message |
|---|---|---|
| 200 | Approved | `Post approved successfully` |
| 400 | No category | `At least one category is required to publish a post` |
| 400 | Post is `drafted` | `A drafted post has not been submitted for publication and cannot be moderated` |
| 404 | Missing or soft-deleted | `Post not found` |

## POST /api/admin/posts/{postId}/reject

### Request Body

```json
{ "reason": "Contains a non-food ingredient" }
```

### Behaviour

- Sets `status = unpublished`, clears `publishedAt`.
- Clears `flag` and `flagDetail`: the rejection is the terminal state, so nothing is left
  to hold.
- Stores the supplied `reason` in `rejection_reason` (null when the body omits it).
- Sets referenced rows that are still `PENDING` to `REJECTED`, and records the
  same `reason` on each of those recipe rows.
- Leaves already-`APPROVED` rows untouched — a shared name does not become bad
  because one post reused it.
- Deletes nothing.

### Responses

| Status | Condition | Message |
|---|---|---|
| 200 | Rejected | `Post rejected successfully` |
| 400 | Post is `drafted` | `A drafted post has not been submitted for publication and cannot be moderated` |
| 404 | Missing or soft-deleted | `Post not found` |

## Business Rules

- Approval and rejection are Admin-only (BR-POST-012).
- Both endpoints are how a held post is decided: the filter and the sweep only ever
  park a post at `processing` with a blocker, they never unpublish it (ADR-023).
- A `drafted` post is refused — nobody submitted it. A `published` post stays
  moderatable, because approve/reject is also how the dish and ingredient names it
  introduced are decided (ADR-016).
- Every real change writes a `moderation_log` row (`APPROVE_POST` /
  `REJECT_POST`) with actor, target and reason (BR-ADMIN-002).
- Approving approves the referenced names; rejecting rejects only the pending
  ones (BR-DISH-001).
- Both endpoints clear `flag`/`flagDetail`: a blocker describes a post that is
  being held, and neither terminal state holds one.
- `rejection_reason` is current state, not history: rejection stores the reason
  and approval clears it. The full sequence of decisions stays in
  `moderation_log`, which this change does not alter.

## Related

- [Business rule: dish and ingredient name approval](../../brs/dish-ingredient-name-approval.md)
- [Feature spec: component-level verification](../../feats/component-level-verification.md)
- [ADR-016: Component-aware moderation](../../adrs/016-component-level-moderation.md)
- [ADR-023: Post processing status and flag blocker](../../adrs/023-post-processing-status-and-flag-blocker.md)
- List posts (Admin): [`admin/get-posts.md`](get-posts.md)
- The author's own unlist action: `docs/apis/post/patch-posts-postid.md` (`publish: false`)
