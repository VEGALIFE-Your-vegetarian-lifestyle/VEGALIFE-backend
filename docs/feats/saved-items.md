# Feature Spec: Saved Posts and Recipes

## Status

Implemented

## Author / owner

Vegalife backend team

## Summary

Let a signed-in member bookmark posts and recipes into a private saved list, and read those lists back
newest-saved first.

## Problem / motivation

Today a member has no way to keep something for later. A post scrolls away in the feed, and a recipe
can only be found again by remembering its author or its dish. The only interactive surfaces on a
post are votes and comments, both of which are public and neither of which is a "keep this" action.
Members therefore resort to browser bookmarks or screenshots, which the platform cannot reflect back
to them and which lose the item as soon as the post or recipe changes.

## Goals

- A member can save a published post and an approved recipe, and unsave either.
- A member can page through their own saved posts and saved recipes, newest save first.
- A saved list never shows content the member could not otherwise read.

## Non-goals

- Named, user-created collections or folders. One flat saved list per target type; grouping would be a
  separate feature (`collection` + `collection_item`), and the current table can be superseded by it.
- Saving any target other than a post or a recipe (videos, menus, dishes, locations).
- Public save counts, "saved by N members", or saves appearing in a post's or recipe's response.
- Notifications, digests, or reminders about saved items.
- Saving on someone else's behalf, or reading another member's saved list.

## Requirements

### Functional Requirements

- [ ] FR-001: `POST /api/saved/posts/{postId}` saves a published, non-deleted post for the caller.
      Saving an already-saved post succeeds and does not change `saved_at`.
- [ ] FR-002: `DELETE /api/saved/posts/{postId}` removes the caller's save. Unsaving a post that is
      not saved succeeds.
- [ ] FR-003: `POST /api/saved/recipes/{recipeId}` saves an approved, non-deleted recipe for the
      caller, with the same idempotency as FR-001.
- [ ] FR-004: `DELETE /api/saved/recipes/{recipeId}` removes the caller's save, with the same
      idempotency as FR-002.
- [ ] FR-005: `GET /api/saved/posts` returns the caller's saved posts, newest save first, paginated
      with `page`/`size` (default 20, max 100), as `PageResponse<PostListResponse>`.
- [ ] FR-006: `GET /api/saved/recipes` returns the caller's saved recipes, newest save first, with
      the same paging contract, as `PageResponse<RecipeResponse>`.
- [ ] FR-007: A save of a post that is not `published`, or of a recipe that is not `APPROVED`, fails
      with `404` and the message `Post not found` / `Recipe not found`, and writes nothing.
- [ ] FR-008: A list omits any saved target that is no longer visible (`post.status != published`,
      `recipe.moderationStatus != APPROVED`, or soft-deleted), without deleting the save row.
- [ ] FR-009: A hard-deleted post or recipe removes its saves via `ON DELETE CASCADE`.
- [ ] FR-010: All six endpoints require authentication; no endpoint accepts a member id, so a caller
      can only ever read and write their own list.

### Non-Functional Requirements

- [ ] NFR-SCALE-001: A saved-items page costs two queries (one for the page of saves, one batched
      read of the target bodies) plus one batched avatar lookup for posts — never one query per row.
- [ ] NFR-SEC-001: Every handler carries `@PreAuthorize("isAuthenticated()")` at class level
      (ADR-009); the saved list is owner-scoped by construction, not by a parameter check.
- [ ] NFR-SEC-002: Saving cannot be used to read unpublished posts or unapproved recipes; the
      existence of an invisible target is not confirmed by the error message.
- [ ] NFR-MAINT-001: Post and recipe serialization is reused from `PostService` / `RecipeService`, so
      a saved item's payload cannot drift from the feed's or the recipe list's.
- [ ] NFR-MAINT-002: Uniqueness and "exactly one target" are database constraints, not conventions.

## Design overview

One table, two endpoint pairs. `saved_item` (`V52`) holds `user_id` plus exactly one of `post_id` /
`recipe_id`, with a `CHECK` enforcing "exactly one target" and two partial unique indexes enforcing
"at most one save per member per target". Both target columns are real foreign keys with
`ON DELETE CASCADE`, so hard deletes clean up after themselves while soft-deleted and invisible
targets are filtered at read time instead — this is the difference between `BR-SAVED-002`'s reversible
disappearance and a cascading delete.

`SavedItemService` owns the four writes and two reads. The lists run one query for the page of saves
(joined to the target table so the visibility filter lives in SQL) and one batched read through
`PostService.readPublishedPostsByIds` / `RecipeService.readApprovedRecipesByIds`, which reuse the
existing mappers. `SavedItemController` is a thin, class-level-authenticated controller under
`/api/saved`.

A polymorphic `target_type`/`target_id` table (the `moderation_log` shape) was considered and rejected:
it cannot carry a foreign key, so a hard-deleted recipe would leave an orphan save with nothing to
clean it up.

## Success metrics

Within four weeks of release: at least 15% of weekly active members have saved one item, and the
median saved list holds 3 or more items — measured from `saved_item` grouped by `user_id`, no
per-item tracking. A list that stays at one item suggests saving is being used as a one-off
"open later" rather than a collection, which would be worth revisiting before adding folders.

## Acceptance criteria

**As a** signed-in member, **I want to** bookmark a post or a recipe and find it again later, **so
that** I do not have to remember where I saw it or rely on browser bookmarks.

- [ ] Given a published post, when I save it, then it appears first in my saved posts and appears
      only once however many times I save it.
- [ ] Given a post I already saved, when an admin unpublishes it, then it disappears from my saved
      posts and reappears if the post is published again.
- [ ] Given an approved recipe, when I save it, then it appears in my saved recipes.
- [ ] Given a recipe still awaiting review, when I try to save it, then I get `404` and nothing is
      stored.
- [ ] Given I have saved posts, when another member lists their saved posts, then mine are not
      included.
- [ ] Given I page through my saved posts with `size=5`, when I reach page 2, then the pages are
      disjoint and the total matches the number of visible saves.

## Risks / open questions

- Whether the client needs a `saved` boolean on `PostListResponse` / `RecipeResponse` for the feed and
  detail views. Not included here: it would add a per-row lookup to every list for a field only some
  surfaces render, and can be added later as a separate "is saved" lookup keyed by ids.
- Whether unsaving should ever be soft (`removed_at`) to preserve a member's history. Not now: an
  unsave is the member asking the row to go away, and there is no requirement to show history.
