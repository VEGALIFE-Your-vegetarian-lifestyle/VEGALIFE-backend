# ADR-026: Embed Only the Post Body and Drop `recipe.score`

## Status

Accepted

## Date

2026-11-19

## Deciders

Product owner (scope call), backend team (implementation and consequences review).

## Context

[ADR-016](016-component-level-moderation.md) decision 2 scored exactly two
components per publish: `CONTENT` (`title + content`) and `RECIPE`
(`name + description + instructions`), storing their scores on `post` and
`recipe` respectively. The `RECIPE` half has been implemented as
`ContentFilterOutboundAdapter.scoreAttachedRecipes`, which embeds the recipe
body once per attached recipe on every `CONTENT_FILTER` run and writes the
result to `recipe.score` (V38).

Five facts about that implementation, verified in the code, make it the wrong
shape for this system:

1. **It re-judges text a human already approved.** Only an `APPROVED`,
   non-deleted recipe can be attached to a post — `PostService.resolveRecipes`
   refuses anything else, and `BR-POST-013` states the rule. The recipe score
   therefore never withholds an unreviewed body from the public; approval
   already did that. It only re-litigates approved text with a centroid.
2. **A provider failure erases a good score.** `recipe.setScore(result.score()
   .orElse(null))` writes `null` when the component embedding throws, so an
   outage downgrades an existing measurement to "never scored" — indistinguishable
   from a low score in the admin UI.
3. **The value is not a property of the recipe.** It is overwritten on every
   filter run of every post carrying the recipe, so editing an unrelated post's
   title can change a recipe's score.
4. **It goes stale on the only path that changes recipe text.** An admin
   `PATCH /api/admin/recipes/{recipeId}` rewrites instructions without
   re-scoring (deliberately — see `AdminRecipeService.updateRecipe`), so a
   stored score can describe text the recipe no longer has.
5. **It is actively misleading on the read surfaces that rank by it.** A recipe
   created through `POST /api/recipes` is never scored at creation; it acquires
   a score only if some post later attaches it. `RecipeRecommendationService`
   orders by `score DESC NULLS LAST`, so a dish's suggested recipes are ranked
   by "was ever attached to a published post", not by quality.

The cost side is one extra embedding call per attached recipe per filter run —
a publish with two recipes makes three calls where two would do.

ADR-016's other decisions (score `CONTENT`, do not score dish/ingredient names,
gate on `PENDING` names, store component state on the row rather than as
per-occurrence verdicts) all still hold. Only decision 2's `RECIPE` half is
withdrawn.

## Decision

Stop embedding and scoring recipe bodies. Score only the post body
(`title + content`), and remove `recipe.score` from the database, the domain
model, every API response, and every ordering that consulted it.

## Considered options

- **Option A — keep scoring recipe bodies.** Rejected on facts 1–5 above: the
  check duplicates an approval gate that already ran, and its stored value is
  unstable, occasionally erased by outages, and never refreshed when the text
  it describes changes.
- **Option B — stop writing the score, keep the column and the API field.**
  Rejected: leaves a permanently constant `null` that every future reader must
  be told to ignore, and keeps the admin `sort=score` ordering meaningful for a
  value that no longer means anything.
- **Option C — remove the scoring machinery but keep the column for one
  release, then drop it.** Rejected in favour of a single clean drop, accepting
  that recovering historical scores after the migration would require
  re-deriving them from post-attachment history.
- **Option D — remove the RECIPE component entirely, including its centroids
  and thresholds, and drop `recipe.score`.** Chosen.

## Consequences

**Positive:**

- One fewer embedding call per publish, and no recipe-proportional growth in
  provider cost or latency on the publish path.
- `recipe.score` stops being a value that means three different things at once
  (approved text, stale text, or a wiped value), so no read surface can leak a
  false quality signal.
- Dish suggestions rank by an honest signal (recency within the dish) rather
  than by post-attachment accident.
- Less configuration to calibrate and keep honest: one centroid pair, one
  accept/reject threshold pair, one seed corpus.

**Negative / trade-offs:**

- **Recipe instruction text now has no automatic check at all.** Recipe
  moderation is exclusively human: `PENDING` at creation, then an admin
  decision. The `BR-POST-013` attach gate is the only automatic barrier, and it
  bars *unapproved* recipes, not *approved-but-poor* ones. A recipe whose
  instructions are off-topic but which an admin approved can now reach the
  public through a post with no second look. This is accepted because the
  previous score could not prevent that either — it could only hold the post,
  and an admin who had already approved the recipe would almost always release
  it.
- **Dropping the column is irreversible.** Historical scores are lost. Rolling
  back requires re-applying V38's column and re-deriving values, not restoring
  them.
- **Breaking API change.** `score` disappears from the admin recipe list/detail
  and from `recipes[].score` in the admin post detail, and `score` stops being
  an accepted sort property.
- The recipe centroid corpus, thresholds and regression tests that existed to
  justify those numbers are deleted rather than left dormant; the calibration
  knowledge in [the calibration spec](../feats/filter-calibration-gemini.md)
  survives as history but no longer describes live code.

## Verification

Revisit if either becomes true:

- Off-topic recipe instructions are observed reaching the public through
  approved posts at a frequency the admin queue cannot absorb — that would mean
  recipe text needs *some* automated check, and the cheaper fix is re-instating
  it on the **recipe create/approve** path (where no human has judged it yet)
  rather than on the publish path removed here.
- A future feature needs a per-recipe semantic vector (for example recipe
  recommendations by similarity). That is a different requirement and would add
  a purpose-built embedding store, not reinstate `recipe.score`.
