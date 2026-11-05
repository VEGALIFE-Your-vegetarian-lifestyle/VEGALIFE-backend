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
        "suggestedIngredients": [
          {
            "ingredientId": "7d3e4f5a-6b7c-8d9e-0f1a-2b3c4d5e6f70",
            "name": "rice noodles",
            "moderationStatus": "APPROVED",
            "required": true,
            "createdAt": "2026-10-24T08:00:00Z",
            "updatedAt": "2026-10-24T08:00:00Z"
          }
        ],
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

Ingredient entries carry the same shape plus the nutrition fields — see
[Ingredient nutrition](#ingredient-nutrition-unit-and-per-unit-values) below.

`suggestedIngredients` is the dish's active curated list (BR-DISH-002), embedded so the
admin detail screen does not need a second call. It includes entries whose ingredient name is
still `PENDING` or `REJECTED` — an admin should see what is curated — unlike the member
endpoint, which hides unapproved names. An empty array means nothing is curated yet.

## Ingredient nutrition: unit and per-unit values

Ingredients carry their own measurement basis (V49, ADR-022). Every `/api/admin/ingredients`
endpoint accepts and returns these fields:

```json
{
  "id": "7d3e4f5a-6b7c-8d9e-0f1a-2b3c4d5e6f70",
  "name": "đậu hũ",
  "unit": "G",
  "calories": 1.44,
  "proteinG": 0.1719,
  "carbohydrateG": 0.0278,
  "fatG": 0.0872,
  "fiberG": 0.023,
  "nutritionResolved": true,
  "moderationStatus": "APPROVED",
  "createdAt": "2026-11-05T08:00:00Z",
  "updatedAt": "2026-11-05T08:00:00Z"
}
```

| Field | Type | Notes |
|---|---|---|
| `unit` | string \| null | `G`, `ML` or `PIECE`. Spelling variants (`g`, `grams`, `gam`, `ml`, `piece`, `quả`, …) are accepted as input and normalised. `kg`, `tbsp`, `cup` are **refused** (`400`): a different unit is not a spelling variant. |
| `calories` / `proteinG` / `carbohydrateG` / `fatG` / `fiberG` | number \| null | Denominated per **one** unit of this ingredient — not per 100 g, not per serving. `DECIMAL(12,6)` on write. For a `PIECE` ingredient the value is the estimate for one whole piece; there is no separate piece-weight field. |
| `nutritionResolved` | boolean (read only) | `true` only when `unit` is set **and** all five values are present. An output, not an input. |

### Validation: merged, then all-or-nothing

Nutrition is validated **after merging the request onto the stored row**, not on the payload
alone. That yields two behaviours that look inconsistent until you see the merge:

- On an ingredient that already has all five values, sending **one** value is accepted — it is
  a correction, and the other four are carried over.
- On an ingredient with no values yet, sending one or four is refused (`400`). The result
  would be a half-answer, which the database's `chk_ingredient_nutrition_complete` also
  rejects.
- Sending nutrition of any kind without a `unit` (on the row after the merge) is refused: a
  per-unit figure with no unit means nothing.
- Creating a bare name with neither a unit nor nutrition is **allowed** — that is how a
  pending ingredient enters the curation queue.

Partial sums are never recorded: a recipe built from an ingredient with three of five values
would report a total that silently omits a macro.

### Changing a unit is allowed, and silent

There is no guard, no confirmation flag, and no blocking. `PATCH` may change `G` → `ML`
freely. This is deliberate (ADR-022) and it is a real hazard: `recipe_ingredient.amount` is a
bare number, so `200` on rice becomes 200 ml instead of 200 g (≈730 kcal → ≈260 kcal) with no
error. The service:

1. recomputes every recipe that references the ingredient, in the same transaction;
2. logs at `WARN` the ingredient id, the old and new unit, and the number of recipes affected.

**There is no member recipe-update endpoint**, so a recipe built while the unit was wrong
cannot be corrected by its author — only deleted and recreated, or fixed by an admin editing
the ingredient back and letting the recompute run.

### Bulk recompute after a nutrition edit

Any change to `unit` or to a nutrition value recomputes every referencing recipe before the
request returns (BR-RECP-004). A recipe's totals therefore never lag its ingredients, and the
suggest-dishes nutrition filters never see a stale column.

Approve/reject and rename/delete do **not** trigger a recompute: they change no arithmetic.


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
- [Business rules: recipes](../../brs/recipes.md) — BR-RECP-004, BR-RECP-005,
  BR-RECP-006
- [Feature spec: ingredient-level recipe nutrition](../../feats/ingredient-nutrition.md)
- [ADR-022: Nutrition measured per one unit](../../adrs/022-ingredient-level-nutrition.md)
- [Feature spec: component-level verification](../../feats/component-level-verification.md)
- [ADR-016: Component-aware moderation](../../adrs/016-component-level-moderation.md)
- [Admin moderation of posts](post-posts-postid-approve.md)
