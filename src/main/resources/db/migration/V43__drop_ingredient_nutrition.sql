-- V43__drop_ingredient_nutrition.sql
-- Ingredient nutrition is superseded by recipe-level totals (issue #163).
--
-- Nutrition never had a per-100g basis recorded, so the ingredient columns could
-- never be turned into a dish's nutrition without guessing portion sizes. The
-- concept now lives on `recipe` as author-entered totals (V42).
--
-- Destructive and irreversible: the stored values are not migrated anywhere,
-- because there is no sound mapping from a per-ingredient figure to a
-- per-recipe total without the amounts and units of a specific recipe.
-- Explicitly approved before landing.

ALTER TABLE ingredient
    DROP COLUMN calories,
    DROP COLUMN protein_g,
    DROP COLUMN carbohydrate_g,
    DROP COLUMN fat_g,
    DROP COLUMN fiber_g;
