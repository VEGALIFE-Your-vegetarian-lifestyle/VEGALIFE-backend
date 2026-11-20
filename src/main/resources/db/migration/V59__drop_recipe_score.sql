-- V59__drop_recipe_score.sql
-- ADR-026: only the post body is scored.
--
-- V38 added recipe.score for the RECIPE component of component-level moderation.
-- That component was removed: recipe text is reviewed by an admin before a post may
-- attach it (BR-POST-013), so re-scoring it on publish re-judged approved text with a
-- value that a provider failure could erase and that nothing refreshed when the recipe
-- was edited. The column has had no writer since the filter stopped scoring recipes.
--
-- Forward-only and irreversible: historical scores are discarded, not migrated.

ALTER TABLE recipe DROP COLUMN IF EXISTS score;
