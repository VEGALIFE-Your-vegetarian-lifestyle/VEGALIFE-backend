# API Reference: Admin Management of Recipes

## Overview

A recipe created through `POST /api/recipes` starts `PENDING` (ADR-016) and needs an
admin decision before it is searchable. These endpoints are where that decision is
made, and where a recipe is corrected when a member reports a mistake.

Members cannot edit or delete a recipe. That is deliberate: a member who could edit
after approval would have a way around the filter — publish clean text, get it
approved, then rewrite it. The cost is that a member with a typo must ask an admin.

| Endpoint | Purpose |
|---|---|
| `GET /api/admin/recipes` | List, filterable to the review queue |
| `GET /api/admin/recipes/{recipeId}` | One recipe, with its dish and ingredient states |
| `PATCH /api/admin/recipes/{recipeId}` | Partial update |
| `DELETE /api/admin/recipes/{recipeId}` | Soft delete |
| `POST /api/admin/recipes/{recipeId}/approve` | Approve the recipe row |
| `POST /api/admin/recipes/{recipeId}/reject` | Reject the recipe row |

There is no admin create: a recipe always originates from a member, so it always has
an author.

## Authentication

All require a JWT with the `ADMIN` role. Other roles get `403`.

## GET /api/admin/recipes

Query parameters: `page`, `size`, `sort`, `userId`, `categoryId`, `createdFrom`,
`createdTo`, `moderationStatus`.

| Parameter | Notes |
|---|---|
| `moderationStatus` | `PENDING`, `APPROVED` or `REJECTED`. Absent = every state. `PENDING` is the review queue. |
| `sort` | One of `createdAt`, `updatedAt`, `name`, `moderationStatus`, `score`. Default `createdAt,desc`. |
| `createdFrom` / `createdTo` | ISO-8601 instants; `400` if `createdFrom` is after `createdTo`. |

### Response (200 OK)

```json
{
  "success": true,
  "message": "Recipes retrieved successfully",
  "data": {
    "content": [
      {
        "id": "0b0f8c1e-1f2a-4c3d-9e8f-7a6b5c4d3e2f",
        "name": "Phở chay",
        "description": null,
        "instructions": "Simmer the broth for two hours…",
        "prepTimeMinutes": 20,
        "cookTimeMinutes": 120,
        "servings": 4,
        "difficulty": "MEDIUM",
        "status": "ACTIVE",
        "moderationStatus": "PENDING",
        "score": 0.6104,
        "dishId": "6c2d3e4f-5a6b-7c8d-9e0f-1a2b3c4d5e6f",
        "dishName": "phở chay",
        "dishModerationStatus": "PENDING",
        "categoryIds": ["…"],
        "ingredients": [
          {
            "ingredientId": "7d3e4f5a-6b7c-8d9e-0f1a-2b3c4d5e6f70",
            "name": "đậu hũ",
            "moderationStatus": "APPROVED",
            "amount": 200,
            "unit": "g"
          }
        ],
        "createdAt": "2026-10-24T08:00:00Z",
        "updatedAt": "2026-10-24T08:00:00Z",
        "userId": "9f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f",
        "username": "jane",
        "email": "jane@example.com"
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

| Field | Notes |
|---|---|
| `status` | `ACTIVE` or `DELETED`, derived from `deletedAt`. |
| `moderationStatus` | Review state. A recipe can be `ACTIVE` and still `PENDING`. |
| `score` | Relevance score at submission time. `null` = never scored. |
| `dishModerationStatus`, `ingredients[].moderationStatus` | The shared names' own states. |

## PATCH /api/admin/recipes/{recipeId}

```json
{ "instructions": "Simmer for 20 minutes", "servings": 4, "difficulty": "EASY" }
```

Partial: an omitted field keeps its current value. Accepted: `name`, `description`,
`instructions`, `prepTimeMinutes`, `cookTimeMinutes`, `servings`, `difficulty`.

Not accepted: `dishName` (the dish is a shared name with its own endpoint),
ingredient lines (adding or removing rows is an ingredient operation, not a field
change), and `moderationStatus`.

Neither `moderationStatus` nor `score` changes:

- The state stays put because the admin's edit is itself an approval decision.
- The score stays as measured at submission — it records why the row was queued,
  and recomputing it here would quietly rewrite that reason.

| Status | Condition |
|---|---|
| 200 | Updated |
| 400 | Blank name or instructions, or an unknown `difficulty` |
| 404 | Missing or soft-deleted |

## DELETE /api/admin/recipes/{recipeId}

Sets `deletedAt`. `post_recipe` rows keep pointing at it, so it simply stops being
listed or readable. Returns `200`; `404` if it is already gone.

## POST /api/admin/recipes/{recipeId}/approve

Body (optional): `{ "reason": "instructions verified" }`

Approves **the recipe row only**. Its dish and ingredients are separate shared names
with their own endpoints. Approving a post already covers all of them at once;
doing it implicitly here would let one recipe approval publish a name that a
different post is still waiting to have reviewed.

An already-approved recipe is a no-op — no state change, no log entry.

## POST /api/admin/recipes/{recipeId}/reject

Body (optional): `{ "reason": "instructions are off-topic" }`

Rejects the recipe row. Nothing is deleted.

## Business Rules

- Approval is what makes a recipe searchable; search filters on `APPROVED`
  (BR-DISH-001, ADR-016).
- The recipe score escalates a passing post to `NEEDS_REVIEW` but never downgrades a
  `REJECTED` verdict.
- Every real change writes a `moderation_log` row (`DELETE_RECIPE`,
  `APPROVE_COMPONENT`, `REJECT_COMPONENT`).

## Related

- [Business rule: dish and ingredient name approval](../../brs/dish-ingredient-name-approval.md)
- [Feature spec: component-level verification](../../feats/component-level-verification.md)
- [ADR-016: Component-aware moderation](../../adrs/016-component-level-moderation.md)
- [Admin management of dish and ingredient names](dish-and-ingredient-crud.md)
