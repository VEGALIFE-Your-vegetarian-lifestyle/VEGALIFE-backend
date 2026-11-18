# Feature Spec: Remove Recipe-Body Scoring

## Status

In progress

## Author / owner

Backend team, with the scope decision recorded in
[ADR-026](../adrs/026-remove-recipe-scoring.md).

## Summary

Score only the post body on publication. Recipe instructions stop being embedded
and scored, and `recipe.score` — its column, its API fields, and every ordering
that consulted it — is removed permanently.

## Problem / motivation

ADR-016 decision 2 split publication scoring into two components: `CONTENT`
(`title + content`) and `RECIPE` (`name + description + instructions`). The
`RECIPE` half is implemented as `ContentFilterOutboundAdapter.scoreAttachedRecipes`,
which embeds each attached recipe on every filter run and stores the result on
`recipe.score`.

That implementation does not do the job it was added for, and it actively
misleads. Five defects, each verified in the code:

1. **The text it judges has already been approved.** `PostService.resolveRecipes`
   refuses to attach anything except an `APPROVED`, non-deleted recipe
   (`BR-POST-013`). The recipe score therefore cannot keep unreviewed text off
   the public feed; approval already does that.
2. **An embedding failure destroys a good measurement.** The write is
   `recipe.setScore(result.score().orElse(null))`, so a provider error stores
   `null` — the same value as "never scored".
3. **The score is not a property of the recipe.** It is overwritten by every
   filter run of every post carrying the recipe, including runs triggered by
   editing a *different* post's title.
4. **It is stale on the only path that edits recipe text.** An admin
   `PATCH /api/admin/recipes/{recipeId}` changes instructions without
   re-scoring, so the stored value can describe text that no longer exists.
5. **It corrupts the ranking that reads it.** `RecipeRecommendationService`
   orders by `score DESC NULLS LAST`. A recipe is never scored at creation, only
   if a post later attaches it, so a dish's suggestions are ranked by
   post-attachment history rather than quality.

Cost: a publish attaching `N` recipes makes `1 + N` embedding calls where one
would do.

## Goals

- A publication run makes exactly one embedding call, regardless of how many
  recipes the post attaches.
- No persisted, returned, or sorted-on value called a "recipe score" remains
  anywhere in the system.
- Post-body filtering behaviour is unchanged: the same verdicts, thresholds,
  flags, and pending-name escalation.

## Non-goals

- Adding an automated check of recipe text anywhere else (create, approve, or
  admin edit). ADR-026 records why that was rejected and what would justify
  revisiting it.
- Changing post-body scoring, duplicate detection, or `post_embedding`.
- Reconciling or reporting historical recipe scores before they are dropped;
  they are discarded.
- Any change to dish, ingredient, or recipe moderation states.

## Requirements

### Functional Requirements

- [ ] FR-001: A `CONTENT_FILTER` delivery embeds the post body exactly once and
      makes no embedding call per attached recipe, for any number of attached
      recipes including zero.
- [ ] FR-002: `GET /api/admin/recipes` items carry no `score` property.
- [ ] FR-003: `GET /api/admin/recipes/{recipeId}` carries no `score` property.
- [ ] FR-004: `GET /api/admin/posts/{postId}` keeps `data.contentScore` and every
      field of `data.names[]` except its score. Neither `data.recipes[]` nor
      `data.names[]` carries a `score` property any more: the property is gone
      from `ComponentSummary`, which both lists share, and names only ever had
      `score: null`.
- [ ] FR-005: `GET /api/admin/recipes?sort=score` is rejected with `400` and the
      accepted set is exactly `createdAt`, `updatedAt`, `name`,
      `moderationStatus`.
- [ ] FR-006: Dish suggestions are ordered `createdAt DESC, id ASC`, with no
      score term.
- [ ] FR-007: After migration the `recipe` table has no `score` column.
- [ ] FR-008: No code path reads or writes a recipe score.
- [ ] FR-009: The `RECIPE` component no longer exists: `ComponentScorer.Component`
      has a single value, `centroids.json` defines only `onTopic`/`offTopic`/`mean`,
      and `FilterProperties` has no recipe threshold pair.
- [ ] FR-010: A post whose content score falls below the reject threshold still
      ends at `processing` with `flag=FILTER_FAILED`; a post referencing a
      `PENDING` dish or ingredient name still escalates to review.

### Non-Functional Requirements

- [ ] NFR-PERF-001: A publish attaching three recipes makes one provider call
      instead of four.
- [ ] NFR-MAINT-001: No dormant recipe-scoring configuration, centroid data,
      seed corpus, or test is left behind to mislead a future reader.
- [ ] NFR-SEC-001: No change — the removed path made no authorization decision.
- [ ] NFR-SCALE-001: The removed code was the only part of the filter whose
      provider usage grew with attachment count; provider usage is now flat per
      publish.

## Design overview

Three mechanical removals, in dependency order:

1. **Publish path** — `ContentFilterOutboundAdapter` stops calling a recipe
   scorer; `scoreAttachedRecipes` and `recipeBody` are deleted. `pendingComponentNames`
   stays: pending-name escalation is a state check, not a score, and is unaffected.
2. **Scoring machinery** — `ComponentScorer.Component.RECIPE`, the
   `EmbeddingComponentScorer.Factory` branch for it, `FilterProperties.recipeAcceptThreshold`/
   `recipeRejectThreshold`, the `Centroids` record's `recipeOnTopic`/`recipeOffTopic`/
   `recipeMean` members, those three keys in `centroids.json`, the two
   `seed-recipe-*.txt` corpora, and the recipe half of `CentroidRegenerationTest`
   are deleted, together with the recipe calibration and regression tests.
3. **Stored value** — `Recipe.score` and its column are dropped (new Flyway
   migration), along with `AdminRecipeListResponse.score`, the `score` sort
   property, `AdminPostDetailResponse.ComponentSummary.score` for `type=RECIPE`,
   and the `RecipeRecommendationService` ranking term.

Steps 1 and 2 change behaviour but leave the column in place, so the branch stays
buildable between commits. Step 3 is the breaking one: dropping the field forces
every remaining reader to be updated in the same commit.

The decision and its accepted consequence (recipe text has no automatic check at
all) live in [ADR-026](../adrs/026-remove-recipe-scoring.md), not here.

## Success metrics

- Provider embedding calls per publish: `1 + N` → `1`, observable in provider
  request logs immediately after deploy for any post attaching `N > 0` recipes.
- Zero reads of a recipe score remain — `grep` for `getScore` on a `Recipe`, and
  for `score` in the recipe migration history, returns only the drop statement.
- No increase in posts held at `processing` with `flag=FILTER_FAILED`; if the
  count changes materially, the removal changed post filtering, which it must not.

## Acceptance criteria

**As an** administrator reviewing a post that carries recipes, **I want** the post
held or released purely on its own text and the names it references, **so that** a
recipe I already approved is not re-judged by a number that contradicts my decision.

- [ ] Given a post attaching two recipes, when it is submitted for publication,
      then exactly one embedding call is made.
- [ ] Given a published post attaching a recipe, when any admin reads the recipe
      list or detail, then no `score` property is present and `sort=score` is a
      `400`.
- [ ] Given a recipe whose instructions are edited by an admin, when the post
      carrying it is next read, then no stale score is reported because no score
      exists.
- [ ] Given an approved recipe on a passing post, when the post body passes,
      then the post publishes without consulting anything about the recipe.
- [ ] Given the migration has run, when the schema is inspected, then `recipe`
      has no `score` column and the application starts cleanly against it.

## Risks / open questions

- **Recipe instructions lose their only automated check.** Accepted in ADR-026,
  on the grounds that the removed check could not stop an approved recipe either.
  The residual exposure is "approved but off-topic" text, which no automatic gate
  covered before this change in a way that held.
- **Irreversible data loss.** Dropping the column discards historical scores;
  rollback re-derives nothing automatically. Confirmed as the intended trade-off
  rather than deferred to a later release.
- **Breaking API change for any client reading `score`.** No compatibility shim
  is kept. If a client is found to depend on it, the field can be restored as a
  constant `null` without reintroducing scoring, which is a cheaper fix than
  reverting this spec.
