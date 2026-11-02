-- V42__recipe_nutrition_totals.sql
-- Recipe-level nutrition (issue #163).
--
-- Nutrition belongs on the recipe, not the ingredient: an ingredient's macros are
-- per-100g of a raw substance, while a dish's nutrition depends on the amounts used
-- and the number of servings. `recipe.servings` (V6) already exists, so per-serving
-- values are derived rather than stored.
--
-- Every column is nullable on purpose. NULL means "the author did not provide it",
-- which is deliberately distinct from 0 - the same convention ADR-016 uses for
-- `recipe.score`. Validation must never require these, or the existing recipe
-- corpus would become unsavable.
--
-- Values are the TOTAL for the whole recipe, not per serving.

ALTER TABLE recipe ADD COLUMN calories DECIMAL(10,2) CHECK (calories >= 0);
ALTER TABLE recipe ADD COLUMN protein_g DECIMAL(10,2) CHECK (protein_g >= 0);
ALTER TABLE recipe ADD COLUMN carbohydrate_g DECIMAL(10,2) CHECK (carbohydrate_g >= 0);
ALTER TABLE recipe ADD COLUMN fat_g DECIMAL(10,2) CHECK (fat_g >= 0);
ALTER TABLE recipe ADD COLUMN fiber_g DECIMAL(10,2) CHECK (fiber_g >= 0);
