-- V38__post_and_recipe_scores.sql
-- Component-level verification (issue #160, ADR-016).
--
-- The filter scored a post but threw the score away: only post.flag was stored,
-- so an admin could see that a post needed review but not how close it was. These
-- two columns keep the numbers.
--
-- Both are nullable on purpose. NULL means "never scored" - either the filter is
-- disabled or the embedding call failed - and is deliberately distinct from a low
-- score. Writing 0.0 for an unscored component would be indistinguishable from a
-- component that the model rejected outright.
--
-- The recipe score lives on recipe, not on post, because the recipe row outlives
-- any single post that references it (post_recipe is many-to-many) and because a
-- recipe is approved through its own admin queue (ADR-016).

ALTER TABLE post ADD COLUMN content_score DOUBLE PRECISION;

ALTER TABLE recipe ADD COLUMN score DOUBLE PRECISION;
