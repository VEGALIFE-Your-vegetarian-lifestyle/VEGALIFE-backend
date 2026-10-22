# API Reference: Admin Management of Dish and Ingredient Names

## Overview

Dishes and ingredients are shared, unowned names (ADR-016). Their review state is
`PENDING` / `APPROVED` / `REJECTED`, and these endpoints are the only place a name
can be created without going through a member's post, and the only place its state
can be changed directly.

| Endpoint | Purpose |
|---|---|
| `GET /api/admin/dishes` | List every state, filterable to the review queue |
| `GET /api/admin/dishes/{dishId}` | One dish |
| `POST /api/admin/dishes` | Create a dish, approved on arrival |
| `PATCH /api/admin/dishes/{dishId}` | Partial update |
| `DELETE /api/admin/dishes/{dishId}` | Soft delete |
| `POST /api/admin/dishes/{dishId}/approve` | Approve the name |
| `POST /api/admin/dishes/{dishId}/reject` | Reject the name |

`/api/admin/ingredients` mirrors all seven. There is no `PUT`: the update body is
partial, and calling a partial update `PUT` would tell clients they can omit fields
and replace the rest.

## Authentication

All require a JWT access token with the `ADMIN` role (`@PreAuthorize`) — see
[ADR-009](../../adrs/009-authorize-endpoints-with-method-security.md). Other roles get
`403`.

## GET /api/admin/dishes

Query parameters: `page`, `size`, `sort`, `name`, `moderationStatus`.

| Parameter | Notes |
|---|---|
| `moderationStatus` | `PENDING`, `APPROVED` or `REJECTED`. Absent = every state. `PENDING` is the review queue. |
| `sort` | One of `name`, `createdAt`, `updatedAt`, `moderationStatus`. Default `name,asc`. |
| `name` | Case-insensitive substring match. |

An unknown `moderationStatus` is a `400`, not an empty page: an empty queue and a
typo'd filter look identical to an admin, and returning "nothing to review" is the
one wrong answer a queue must never give.

### Response (200 OK)

```json
{
  "success": true,
  "message": "Dishes retrieved successfully",
  "data": {
    "content": [
      {
        "id": "6c2d3e4f-5a6b-7c8d-9e0f-1a2b3c4d5e6f",
        "name": "phở chay",
        "description": null,
        "imageUrl": null,
        "cuisineType": null,
        "moderationStatus": "PENDING",
        "createdAt": "2026-10-24T08:00:00Z",
        "updatedAt": "2026-10-24T08:00:00Z"
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

Ingredient entries carry the same shape plus `calories`, `proteinG`,
`carbohydrateG`, `fatG` and `fiberG`.

## POST /api/admin/dishes

```json
{ "name": "bún chả chay", "description": "Grilled noodle bowl", "cuisineType": "Vietnamese" }
```

| Field | Required | Notes |
|---|---|---|
| name | Yes | Trimmed. Max 255 chars (100 for ingredients). |
| description | No | Max 2000 chars. |
| imageUrl, cuisineType | No | |

The name is created `APPROVED` — the creator is the approver. There is no
`moderationStatus` field: state changes go through approve/reject, so a typo fix
cannot silently re-approve a rejected name.

### Responses

| Status | Condition |
|---|---|
| 201 | Created, `moderationStatus: APPROVED` |
| 400 | Name rejected earlier and cannot be re-created (BR-DISH-001) |
| 400 | Blank name |
| 409 | Name already exists |

## PATCH /api/admin/dishes/{dishId}

Partial update: an omitted field keeps its current value.

- **The moderation state is never changed by an edit.** The admin is the approver,
  so their edit is itself an approval decision; re-queueing an approved name would
  withhold it from every recipe and menu that already uses it.
- **Renaming a `REJECTED` row is refused** (`400`). The rejection is a block on that
  name, and renaming the row would free the old name for a member to create again.

| Status | Condition |
|---|---|
| 200 | Updated |
| 400 | Blank name, or an attempt to rename a rejected row |
| 404 | Missing or soft-deleted |
| 409 | The new name is taken by another row |

## DELETE /api/admin/dishes/{dishId}

Soft delete: sets `deletedAt`. Never a hard delete — recipes and `menu_detail` rows
reference the row and must keep resolving (BR-MENU-005).

The name is **not freed**. A member who reuses it gets `400 Dish is not available`,
the same answer as before deletion. Resurrection is not on the table.

## POST /api/admin/dishes/{dishId}/approve

Body (optional): `{ "reason": "real dish" }`

Approves the name outright, **including one previously rejected**. This is the only
way back from `REJECTED`, and it is deliberately narrow: post approval cannot do it
(a post is not a review of every name it mentions), but an admin naming this row by
id is making exactly that decision. Without it, a mistyped rejection would poison a
legitimate name forever.

## POST /api/admin/dishes/{dishId}/reject

Body (optional): `{ "reason": "not food" }`

Rejects the name outright, including one currently approved — an admin acting on the
name itself is the authority that approved it. Nothing is deleted: existing recipes
and menus keep resolving, members simply cannot introduce the name again.

## Business Rules

- A name created by a member through `POST /api/recipes` starts `PENDING`
  (BR-DISH-001).
- Approving a **post** approves every `PENDING` name it references; rejecting a post
  rejects only the ones still pending. These per-name endpoints are the direct route.
- Rejection is permanent for members and non-destructive for content.
- Every write is recorded in `moderation_log` with actor, target and reason
  (`CREATE_DISH`, `DELETE_DISH`, `APPROVE_COMPONENT`, `REJECT_COMPONENT`, and the
  ingredient equivalents).

## Related

- [Business rule: dish and ingredient name approval](../../brs/dish-ingredient-name-approval.md)
- [Feature spec: component-level verification](../../feats/component-level-verification.md)
- [ADR-016: Component-aware moderation](../../adrs/016-component-level-moderation.md)
- [Admin moderation of posts](post-posts-postid-approve.md)
