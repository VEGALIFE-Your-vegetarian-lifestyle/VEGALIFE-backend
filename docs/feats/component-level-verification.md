# Feature: Component-Level Verification and Approval of Post Content

## Status

Accepted — implementation in progress (issue #160).

## Summary

Two problems, one change:

1. **The filter scores one string.** `ContentFilterOutboundAdapter.deliver()`
   scores `title + content` and nothing else. A post's recipe — including its
   `instructions`, the largest free-text field a user controls — is never
   examined. The reviewer asked for every part of a post to be verified, not just
   its prose.
2. **Dishes and ingredients are unowned global rows with no moderation state.**
   Any authenticated user creating a recipe silently introduces new dish and
   ingredient names (`RecipeService.findOrCreateDish` / `findOrCreateIngredient`).
   Those names become permanent reference data used by menus and the AI meal
   planner, with nobody having reviewed them.

This feature scores recipe text alongside post content, and puts new dish and
ingredient names behind admin approval before they are visible.

## Goals

- Score recipe text (name, description, instructions) with the same relevance
  scorer used for post content.
- Never let an unreviewed dish or ingredient name reach other users.
- Give an admin one screen that shows *why* a post was queued and *what* in it is
  unreviewed.
- Make approving and rejecting a post act on its components, without deleting
  anything.

## Non-goals

- **Media moderation.** Post images and videos are not scored.
- **Nutrition values.** Never read, validated or modified by this feature.
- **Centroid scoring of dish and ingredient names.** See "Why names are not
  scored".
- **Post-level automatic rejection.** `Post.Flag.REJECTED` continues to exist and
  the filter continues to produce it. Changing that was proposed and then
  explicitly withdrawn; see "Known contradiction".
- **Per-occurrence verdict storage.** No `post_filter_verdict` table.

## Requirements

| ID | Requirement |
|---|---|
| R1 | `dish`, `ingredient` and `recipe` carry `moderation_status` ∈ `PENDING`/`APPROVED`/`REJECTED`. |
| R2 | All rows existing before this feature are `APPROVED` after migration, in the same migration that adds the column. |
| R3 | A recipe body — `name + "\n" + description + "\n" + instructions` — is embedded and scored on every publish that has a recipe attached. |
| R4 | The post's content score and the recipe's score are persisted (`post.content_score`, `recipe.score`). `NULL` means never scored, distinct from a low score. |
| R5 | A dish or ingredient created through `POST /api/recipes` is `PENDING`. A name created by an admin is `APPROVED`. |
| R6 | A `REJECTED` name can never be created again by anyone. |
| R7 | A post is flagged `NEEDS_REVIEW` if its content score is below the accept threshold, its recipe score is below the recipe threshold, or any dish/ingredient it references is `PENDING`. |
| R8 | `GET /api/admin/posts/{id}` returns the post with its content score, recipe score, and each referenced dish/ingredient as `{id, type, name, moderationStatus}`. |
| R9 | `POST /api/admin/posts/{id}/approve` publishes the post and sets every `PENDING` dish, ingredient and recipe it references to `APPROVED`. |
| R10 | `POST /api/admin/posts/{id}/reject` unpublishes the post and sets referenced rows that are still `PENDING` to `REJECTED`. Rows already `APPROVED` are untouched. Nothing is deleted. |
| R11 | Public dish and ingredient listings return `APPROVED` rows only. A recipe whose dish is `PENDING` is still returned, with the dish marked pending. |
| R12 | A failure to score a component writes a `NULL` score and queues the post for review; the publish request itself still succeeds. |
| R13 | Duplicate detection continues to key off the content verdict alone. |

## Why names are not scored

An earlier revision proposed four centroid pairs, one per component, including
dish and ingredient names. It was withdrawn for two reasons.

**A centroid answers "is this text about vegan food", not "is this name
acceptable".** `"Bún bò Huế chay"` may score low for being unfamiliar while being
a perfectly legitimate dish; `"Thịt chó"` is on-topic as food and would score
well despite being unacceptable on a vegan platform. Short strings give the
centroid very little to work with, so both false positives and false negatives
are likely.

**It does not scale to the long tail.** There are thousands of legitimate
Vietnamese ingredients. A centroid fitted to a small labelled corpus will misfire
on names it has never seen, and every misfire sends a real user's post to a
human who must then reverse the machine's opinion.

Because a new name is invisible until an admin approves it anyway, the approval
step — not the automatic check — is what guarantees correctness. Names therefore
get no score and no threshold.

## Design

### Scoring

On each `CONTENT_FILTER` delivery:

```
CONTENT  ← embed(title + "\n" + content)         1 call, unchanged
RECIPE   ← embed(name + "\n" + desc + "\n" + instructions)   1 call, new, only if a recipe is attached
DISH      ← no scoring; PENDING ⇒ review
INGREDIENT ← no scoring; any PENDING ⇒ review
```

Two embedding calls per publish with a recipe, one without. Before this feature
it was one in both cases.

### Storage

```sql
ALTER TABLE post   ADD COLUMN content_score DOUBLE PRECISION;
ALTER TABLE recipe ADD COLUMN score         DOUBLE PRECISION;

ALTER TABLE dish       ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE ingredient ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE recipe     ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE ingredient ADD COLUMN deleted_at TIMESTAMPTZ;

UPDATE dish       SET moderation_status = 'APPROVED';
UPDATE ingredient SET moderation_status = 'APPROVED';
UPDATE recipe     SET moderation_status = 'APPROVED';
```

There is deliberately **no per-component verdict table**. Everything the admin
screen needs is derivable at read time: which names are new is
`moderation_status = 'PENDING'`; the component type and id are the row itself.

### Why `post.flag` is retained

`flag` is not replaced by the scores. It is the aggregate summary, and four
existing behaviours read it:

- `PendingFilterSweepJob` selects posts whose flag is `PENDING`.
- `AdminPostService` filters the admin list by flag.
- `PostService` sets `PENDING` at enqueue time, before any scoring exists.
- `ContentFilterOutboundAdapter` sets `DUPLICATE`.

## The backfill hazard

`DEFAULT 'PENDING'` is correct for new rows and destructive for existing ones:
every recipe whose dish became `PENDING` would stop resolving, and every
`menu_detail` row (`dish_id NOT NULL`) would point at a hidden dish, breaking
saved menus and the AI meal planner. The three `UPDATE`s therefore ship in the
same migration as the column, and an integration test creates a menu and a recipe
against pre-migration rows and asserts both still resolve afterwards.

## Known contradiction: post-level automatic rejection

The reviewer asked that all content be verified. This feature verifies more of it
(recipe text, and names via approval) but deliberately leaves the post-level
`REJECTED` band exactly as it is: content scoring below `app.filter.reject-threshold`
still results in `Post.Flag.REJECTED` without human involvement.

This is a recorded decision, not an oversight. Removing that band was proposed,
implemented in the plan, and then withdrawn by the product owner; the reason was
not recorded. Should the band be removed later, the places to change are
`EmbeddingRelevanceScorer.score()` (the `REJECT` branch) and
`ContentFilterService.filter()` (the `REJECT` case).

## CRUD added alongside

Moderation needs the admin to be able to inspect and correct the rows it
approves, so this branch also adds:

| Entity | Added |
|---|---|
| Recipe | `GET /api/recipes/{id}`, `GET /api/recipes/me`; admin `PUT`, `DELETE` |
| Dish | admin `POST`, `PUT`, `DELETE` (soft); public `GET /{id}` |
| Ingredient | same, plus the `deleted_at` column it lacked |

Members keep exactly one write path — `POST /api/recipes`. They cannot edit or
delete their own recipes; that requires an admin. This is a deliberate product
decision that keeps moderation unbypassable: a member cannot publish clean text,
get approved, then edit it into something else. The cost is that a member with a
typo in their own recipe must ask an admin to fix it.

## Acceptance criteria

- Publishing a post whose content passes but whose recipe instructions are
  off-topic queues the post, with `post.content_score` above and `recipe.score`
  below its threshold.
- Publishing a post that introduces a new ingredient queues the post and leaves
  the ingredient `PENDING` and invisible in `GET /api/ingredients`.
- Approving that post makes the ingredient `APPROVED` and visible.
- Rejecting a post that references both a `PENDING` and an already-`APPROVED`
  ingredient rejects only the pending one, and deletes neither.
- A rejected name cannot be used again by any user.
- Existing recipes and saved menus resolve after the migration.
- A Gemini failure during scoring queues the post rather than failing the publish.

## Related

- [ADR-016: Component-aware moderation without per-occurrence verdicts](../adrs/016-component-level-moderation.md)
- [ADR-011: Keep post visibility in `status` and the verdict in `flag`](../adrs/011-post-status-and-filter-axis-separation.md)
- [Feature: filter calibration](../feats/filter-calibration-gemini.md)
- [Business rule: dish and ingredient name approval](../brs/dish-ingredient-name-approval.md)
- [API: approve a post](../apis/admin/post-posts-postid-approve.md)
