-- V49__ingredient_unit_and_nutrition.sql
-- Ingredient-level nutrition with a single unit per ingredient.
--
-- Supersedes the recipe-level author-entered totals added by V42 and the removal of
-- ingredient nutrition in V43. V43's objection was that ingredient nutrition had no
-- recorded basis, so it could never be turned into a per-recipe total without guessing
-- portion sizes. This migration answers exactly that: every ingredient now records the
-- ONE unit its nutrition is expressed in (per 1 G, 1 ML, or 1 PIECE), so a recipe total is
-- a plain sum of `amount * value` with no conversion layer, no density table, and no
-- portion table.
--
-- Three properties are deliberate and load-bearing:
--
--   1. `ingredient.unit` is nullable. A name created by a member through POST /api/recipes
--      carries whatever unit that member typed, and an admin may correct it later. Rows the
--      backfill below cannot resolve stay NULL rather than receiving a guess, and a NULL unit
--      makes every recipe using that ingredient uncomputable. Unknown is not a default.
--
--   2. The five nutrition columns are all-or-nothing, enforced by chk_ingredient_nutrition_
--      complete. A half-filled ingredient could otherwise produce a recipe total that looks
--      authoritative while silently omitting a macro.
--
--   3. `recipe.*` nutrition is computed from ingredients from now on, never entered. The
--      author-entered values of V42 are cleared here because after this migration nothing can
--      distinguish them from computed ones, and an unverified estimate is not a number worth
--      keeping. They refill as ingredients are resolved.

-- ---------------------------------------------------------------------------
-- 1. Ingredient unit + nutrition columns
-- ---------------------------------------------------------------------------

ALTER TABLE ingredient
    ADD COLUMN unit VARCHAR(10),
    ADD COLUMN calories DECIMAL(12,6),
    ADD COLUMN protein_g DECIMAL(12,6),
    ADD COLUMN carbohydrate_g DECIMAL(12,6),
    ADD COLUMN fat_g DECIMAL(12,6),
    ADD COLUMN fiber_g DECIMAL(12,6);

-- There is deliberately no `piece_grams` column. A PIECE ingredient's nutrition is an estimate
-- for one piece, entered as that estimate; recording the assumed mass behind it as well would
-- create a second number that nothing reads and that can silently disagree with the first. The
-- mass assumption belongs to whoever produced the estimate, not to the row.

-- DECIMAL(12,6), not (10,2): these are per-ONE-unit values, so a per-gram figure is small
-- (cooked rice is 0.0130 g protein per gram). Scale 2 would round most macros to zero.
ALTER TABLE ingredient
    ADD CONSTRAINT chk_ingredient_unit
        CHECK (unit IN ('G', 'ML', 'PIECE'));

ALTER TABLE ingredient
    ADD CONSTRAINT chk_ingredient_nutrition_complete
        CHECK (
            (calories IS NULL AND protein_g IS NULL AND carbohydrate_g IS NULL
                AND fat_g IS NULL AND fiber_g IS NULL)
            OR
            (calories IS NOT NULL AND protein_g IS NOT NULL AND carbohydrate_g IS NOT NULL
                AND fat_g IS NOT NULL AND fiber_g IS NOT NULL)
        );

-- Negative nutrition is never valid; 0 is (water, salt, black coffee).
ALTER TABLE ingredient
    ADD CONSTRAINT chk_ingredient_nutrition_non_negative
        CHECK ((calories IS NULL OR calories >= 0)
            AND (protein_g IS NULL OR protein_g >= 0)
            AND (carbohydrate_g IS NULL OR carbohydrate_g >= 0)
            AND (fat_g IS NULL OR fat_g >= 0)
            AND (fiber_g IS NULL OR fiber_g >= 0));

-- ---------------------------------------------------------------------------
-- 2. Backfill `ingredient.unit` from the legacy free-text `recipe_ingredient.unit`
-- ---------------------------------------------------------------------------
--
-- Only values whose dimension is unambiguous are mapped. Anything else - 'bó', 'vừa đủ',
-- a typo, an empty string - leaves the ingredient NULL on purpose, so an admin resolves it
-- through the API rather than inheriting a guess. Amounts are rescaled to the base unit of
-- the mapped dimension (kg -> G, l/tbsp/tsp/cup -> ML), which is a pure unit change and
-- loses no information.

CREATE TEMP TABLE legacy_unit_map (
    raw         TEXT PRIMARY KEY,
    target_unit TEXT NOT NULL,
    factor      NUMERIC(18, 6) NOT NULL
);

INSERT INTO legacy_unit_map (raw, target_unit, factor) VALUES
    ('g', 'G', 1), ('gram', 'G', 1), ('grams', 'G', 1), ('gam', 'G', 1), ('gr', 'G', 1),
    ('kg', 'G', 1000), ('kilogram', 'G', 1000), ('kilograms', 'G', 1000),
    ('kilogam', 'G', 1000), ('kí', 'G', 1000), ('ký', 'G', 1000),
    ('ml', 'ML', 1), ('milliliter', 'ML', 1), ('millilitre', 'ML', 1),
    ('milliliters', 'ML', 1), ('mililit', 'ML', 1),
    ('l', 'ML', 1000), ('lit', 'ML', 1000), ('liter', 'ML', 1000), ('litre', 'ML', 1000),
    ('liters', 'ML', 1000), ('lít', 'ML', 1000),
    ('tbsp', 'ML', 15), ('tablespoon', 'ML', 15), ('tablespoons', 'ML', 15),
    ('muỗng canh', 'ML', 15),
    ('tsp', 'ML', 5), ('teaspoon', 'ML', 5), ('teaspoons', 'ML', 5),
    ('muỗng cà phê', 'ML', 5), ('muỗng cafe', 'ML', 5), ('muỗng cà phe', 'ML', 5),
    ('cup', 'ML', 250), ('cups', 'ML', 250), ('chén', 'ML', 250), ('bát', 'ML', 250);

-- Rescale amounts by the line's own factor. Independent of whether the ingredient resolves,
-- because a kg -> g rewrite is correct regardless of what other units that ingredient uses.
UPDATE recipe_ingredient ri
SET amount = ROUND(ri.amount * m.factor, 3)
FROM legacy_unit_map m
WHERE LOWER(BTRIM(ri.unit)) = m.raw
  AND m.factor <> 1;

-- Assign a unit only when every line of that ingredient maps to the SAME dimension.
-- '200 g' and '1 bó' on the same ingredient is a genuine conflict, not something to
-- average away, so that ingredient stays NULL.
UPDATE ingredient i
SET unit = resolved.target_unit
FROM (
    SELECT ri.ingredient_id, MIN(m.target_unit) AS target_unit
    FROM recipe_ingredient ri
    JOIN legacy_unit_map m ON LOWER(BTRIM(ri.unit)) = m.raw
    WHERE NOT EXISTS (
        SELECT 1
        FROM recipe_ingredient ri2
        LEFT JOIN legacy_unit_map m2 ON LOWER(BTRIM(ri2.unit)) = m2.raw
        WHERE ri2.ingredient_id = ri.ingredient_id
          AND m2.target_unit IS NULL
    )
    GROUP BY ri.ingredient_id
    HAVING COUNT(DISTINCT m.target_unit) = 1
) resolved
WHERE i.id = resolved.ingredient_id;

DROP TABLE legacy_unit_map;

-- ---------------------------------------------------------------------------
-- 3. Clear author-entered recipe nutrition
-- ---------------------------------------------------------------------------

UPDATE recipe
SET calories = NULL,
    protein_g = NULL,
    carbohydrate_g = NULL,
    fat_g = NULL,
    fiber_g = NULL;

-- ---------------------------------------------------------------------------
-- 4. Drop the per-line unit
-- ---------------------------------------------------------------------------
--
-- The unit now belongs to the ingredient, and keeping a per-line copy would let the two
-- disagree with no way to pick a winner. Existing amounts were rescaled above, so nothing
-- is lost. The response still carries `unit`; it reads it from the ingredient.

ALTER TABLE recipe_ingredient DROP COLUMN unit;

-- ---------------------------------------------------------------------------
-- 5. Admin "needs nutrition" queue
-- ---------------------------------------------------------------------------
--
-- Supporting the curated pass over the existing corpus: the admin list filters on
-- "no nutrition yet", ordered by name. Partial, so it stays small as the corpus is filled.

CREATE INDEX idx_ingredient_missing_nutrition
    ON ingredient (name)
    WHERE calories IS NULL AND deleted_at IS NULL;
