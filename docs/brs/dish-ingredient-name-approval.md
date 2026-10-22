# BR-DISH-001: Dish and Ingredient Name Approval

## Rule

A dish or ingredient name becomes visible to users only after an administrator has
approved it. Approval is **per name, platform-wide** — dishes and ingredients have
no owner and are shared by every user, recipe and menu.

## States

| State | Meaning | Visible in public listings | Can be created again |
|---|---|---|---|
| `PENDING` | Introduced but not reviewed | No | Yes — resolves to this row |
| `APPROVED` | Reviewed and accepted | Yes | Yes — resolves to this row |
| `REJECTED` | Reviewed and refused | No | **No** — permanently blocked |

## How a name enters each state

| Path | Initial state |
|---|---|
| `POST /api/recipes` introduces a previously unknown name | `PENDING` |
| Admin creates it via the admin CRUD endpoint | `APPROVED` |
| Any row that existed before this rule was introduced | `APPROVED` (migration backfill) |
| Admin approves a post that references it | `APPROVED` |
| Admin rejects a post that references it, while still `PENDING` | `REJECTED` |
| Admin rejects it directly from the admin CRUD endpoint | `REJECTED` |

## Rejection is permanent and non-destructive

- A rejected name cannot be created again by any user. A lookup for that name
  finds the `REJECTED` row and refuses the request.
- Rejection **never deletes** the row, its recipes, or the posts and menus that
  reference it. Other users' content is unaffected.
- Rejection does not cascade to the recipe that introduced the name, and rejection
  of a post does not cascade to the names it references unless they are still
  `PENDING`.

## Approving a post approves its names

Approving a post sets every `PENDING` dish, ingredient and recipe it references to
`APPROVED`, in the same transaction. This is the only routine path by which a
user-introduced name becomes visible; there is no separate dish queue.

Consequence: **any post that introduces a new name requires admin review before
that name is visible**, even if the post's own text would have passed the filter.

## Editing

An administrator may edit an approved name, and doing so does **not** reset its
state: the admin is the approver, so their edit is itself an approval decision.

Members cannot edit or delete dishes, ingredients or recipes. Their only write
path is `POST /api/recipes`, which creates names only in `PENDING`.

## Rules this interacts with

- **BR-CONTENT-003** — filtering before publication: a post referencing a
  `PENDING` name is withheld from publication for the same reason a post failing
  the content filter is.
- **BR-POST-012** — admin moderation: approval and rejection are admin-only.

## Related

- [Feature spec: component-level verification](../feats/component-level-verification.md)
- [ADR-016: Component-aware moderation](../../adrs/016-component-level-moderation.md)
