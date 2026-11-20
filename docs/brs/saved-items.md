# Business Rules: Saved Items

Saved posts and saved recipes — a private bookmark list belonging to one member.

---

# Business Rule: A Saved Item Is Private to the Member Who Saved It

## Rule ID

`BR-SAVED-001`

## Status

Active

## Statement

A saved item is a private bookmark owned by the member who created it. Saving and unsaving are
idempotent: saving a target that is already saved is a successful no-op that keeps the original
`saved_at`, and unsaving a target that is not saved is a successful no-op. A member may save a given
post or recipe at most once, enforced by a unique index rather than by a read-then-write check.

No endpoint exposes another member's saved items, and no saved-item count is published as a post or
recipe metric. Save counts are not likes; they are never returned alongside a post or recipe.

## Rationale

A saved list is a personal collection, not social proof. Exposing it would turn bookmarking into a
public signal a member did not agree to, and a save count would invite the same "engagement farming"
the vote counters already serve. Idempotency is stated as a rule because the client's most common
mistake is a double tap or a retried request: failing those would make saving feel broken for a
reason the member cannot see.

The uniqueness rule lives in a partial unique index (`uq_saved_item_post`, `uq_saved_item_recipe`)
rather than in application code, so two concurrent saves cannot both insert.

## Scope & Exceptions

Applies to `POST`/`DELETE /api/saved/posts/{postId}`, `POST`/`DELETE /api/saved/recipes/{recipeId}`,
and `GET /api/saved/posts` / `GET /api/saved/recipes`. It does not change the post vote
(`BR-VOTE-001`) or any author-facing metric.

## Enforcement

- Table: `saved_item` (migration `V52`), with `user_id` plus exactly one of `post_id` / `recipe_id`.
- Repository: `SavedItemRepository` — the `findByUserIdAndPostId` / `findByUserIdAndRecipeId` reads
  plus the partial unique indexes are the uniqueness enforcement.
- Service: `SavedItemService.savePost` / `saveRecipe` return early when the row exists;
  `unsavePost` / `unsaveRecipe` run an idempotent `delete ... where` that succeeds on zero rows.
- Security: `SavedItemController` carries a class-level `@PreAuthorize("isAuthenticated()")`, and no
  path identifies the member — the caller is always the owner of the list.
- Tests: `SavedItemServiceTest`, `SavedItemControllerTest`,
  `SavedItemControllerIntegrationTest.savePost_twiceStoresOneRow`.

## Last Reviewed

2026-11-12, by Vegalife backend team

---

# Business Rule: Only Visible Content Can Be Saved or Listed as Saved

## Rule ID

`BR-SAVED-002`

## Status

Active

## Statement

A save may only be created for a target the caller could read publicly right now: a post with
`status = published` and `deleted_at IS NULL`, or a recipe with `moderationStatus = APPROVED` and
`deleted_at IS NULL`. An id that is unknown, soft-deleted, or not visible fails with `404` and the
message `Post not found` / `Recipe not found` — the same response a caller gets for an id that does
not exist, so the endpoint never confirms the existence of content it will not show.

The saved lists apply the same rule at read time and **omit** rows that no longer qualify, rather
than deleting them and rather than returning the content anyway. A post unpublished or hidden after
being saved, and a recipe demoted from `APPROVED`, disappear from the list; they reappear if the
target becomes visible again, because the save itself was never removed. Hard-deleting a target does
remove the save (`ON DELETE CASCADE`).

## Rationale

Without the write-side rule, the saved list would be a side channel around moderation: a member could
bookmark a draft, a rejected recipe, or another member's unpublished post and read it back from a
"private" list. Without the read-side rule, content that a moderator took down would stay readable
through every member who had bookmarked it — which would make unpublishing and rejection cosmetic for
exactly the users most likely to have saved the item.

Keeping the row instead of deleting it is what makes the rule reversible. A post that is temporarily
unpublished while an admin fixes it, or a recipe returned to the queue for re-review, should come
back to its savers when it is restored; deleting their saves would be a silent loss of their data for
a decision they had no part in.

## Scope & Exceptions

Applies to all six saved-item endpoints. It does not add visibility rules to the post or recipe read
endpoints themselves — those already have theirs (`BR-POST-010`, and the approved-only recipe
surfaces). It deliberately does not apply to the author's own view: they see their own draft through
their own endpoints, not by saving it.

## Enforcement

- Service: `SavedItemService.savePost` / `saveRecipe` load the target with a visibility-qualified
  lookup and throw `ResourceNotFoundException` when it does not qualify.
- Repository: `findVisibleSavedPosts` / `findVisibleSavedRecipes` join the target table and filter on
  `status` / `moderationStatus` plus `deletedAt IS NULL` in SQL, so a page is never padded with rows
  the caller may not see.
- Migration: `V52` adds `ON DELETE CASCADE` on both target foreign keys.
- Tests: `SavedItemServiceTest.savePost_invisiblePostReturnsNotFound`,
  `saveRecipe_pendingRecipeReturnsNotFound`,
  `SavedItemControllerIntegrationTest.savePost_hiddenAfterSavingDisappearsFromList`,
  `saveRecipe_demotedAfterSavingDisappearsFromList`.

## Last Reviewed

2026-11-12, by Vegalife backend team
