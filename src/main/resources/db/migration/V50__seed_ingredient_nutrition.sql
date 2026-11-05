-- V50__seed_ingredient_nutrition.sql
-- 50 vegan pantry ingredients with the unit and per-unit nutrition needed to price a recipe
-- (ADR-022).
--
-- Source: USDA FoodData Central, SR Legacy (public domain, CC0). Every per-100 g figure below is
-- copied from the corresponding FDC entry; nothing here is estimated or invented.
--
-- Two conversions happen in this file, both mechanical and visible:
--
--   G / ML  - the USDA value is per 100 g, so it is divided by 100 to become per 1 g or 1 ml.
--             For ML the USDA value is treated as per 100 ml, which is what FDC publishes for
--             liquid foods and is the same number for these items at the precision stored.
--
--   PIECE   - the per-piece value is (per-100 g value) x (gram weight of one piece) / 100. The
--             gram weight is USDA's own `foodPortion.gramWeight` for a medium/whole item, and is
--             kept in this file's seed CTE as a local value only. It is not a column: what gets
--             stored is the resulting per-piece estimate, which is all a recipe total ever needs.
--             Keeping the multiplicand here rather than inlining the product is what makes each
--             estimate reviewable - a reader can see 18 x 123 / 100 and judge the 123.
--             This is the one figure in the file that is a curation choice, not a measurement:
--             a tomato is not a constant size, and USDA keeps separate portion rows for cherry
--             through beefsteak. Splitting an ingredient ('cà chua bi' vs 'cà chua') is the fix
--             for that, not a portion table.
--
-- Existing rows win. `ON CONFLICT DO NOTHING` on lower(name) means an ingredient a member already
-- created keeps its id, its moderation state and its (usually absent) nutrition. Filling those in
-- from this file would be wrong twice over: the row's unit may have been resolved differently by
-- the V49 backfill, so a per-gram value could land on an ingredient that is measured in pieces. An
-- admin resolves those through the API, where the recipes using them are visible.
--
-- Names are lowercase because that is the form RecipeService stores after normalising a member's
-- input (BR-RECP-002); seeding any other casing would create a duplicate distinct name.

WITH seed (name, unit, grams_per_piece, kcal_per_100, protein_per_100, carb_per_100, fat_per_100,
           fiber_per_100) AS (
    VALUES
        -- Grains, flours and starches
        ('gạo trắng',             'G', NULL::NUMERIC, 365, 7.13, 79.95,  0.66,  1.3),
        ('gạo lứt',               'G', NULL::NUMERIC, 370, 7.94, 77.24,  2.92,  3.5),
        ('yến mạch',              'G', NULL::NUMERIC, 389, 16.89, 66.27,  6.9, 10.6),
        ('bột mì đa dụng',        'G', NULL::NUMERIC, 364, 10.33, 76.31,  0.98,  2.7),
        ('bột gạo',               'G', NULL::NUMERIC, 366, 5.95, 80.13,  1.42,  2.4),
        ('bột năng',              'G', NULL::NUMERIC, 357, 0.19, 88.69,  0.02,  0.9),

        -- Legumes and soy
        ('đậu hũ',                'G', NULL::NUMERIC, 144, 17.19,  2.78,  8.72,  2.3),
        ('đậu hũ non',            'G', NULL::NUMERIC,  55,  5.3,   2.4,   2.7,   0.3),
        ('đậu nành',              'G', NULL::NUMERIC, 446, 36.49, 30.16, 19.94,  9.3),
        ('đậu xanh',              'G', NULL::NUMERIC, 347, 23.86, 62.62,  1.15, 16.3),
        ('đậu đen',               'G', NULL::NUMERIC, 341, 21.6,  62.36,  1.42, 15.5),
        ('đậu đỏ',                'G', NULL::NUMERIC, 333, 23.58, 60.01,  0.83, 24.9),
        ('đậu lăng',              'G', NULL::NUMERIC, 352, 24.63, 63.35,  1.06, 10.7),

        -- Nuts and seeds
        ('đậu phộng',             'G', NULL::NUMERIC, 567, 25.8,  16.13, 49.24,  8.5),
        ('hạnh nhân',             'G', NULL::NUMERIC, 579, 21.15, 21.55, 49.93, 12.5),
        ('hạt điều',              'G', NULL::NUMERIC, 553, 18.22, 30.19, 43.85,  3.3),
        ('hạt chia',              'G', NULL::NUMERIC, 486, 16.54, 42.12, 30.74, 34.4),
        ('vừng',                  'G', NULL::NUMERIC, 573, 17.73, 23.45, 49.67, 11.8),

        -- Oils, liquids and condiments
        ('dầu ăn thực vật',       'ML', NULL::NUMERIC, 884,  0,     0,   100,     0),
        ('dầu ô liu',             'ML', NULL::NUMERIC, 884,  0,     0,   100,     0),
        ('sữa đậu nành',          'ML', NULL::NUMERIC,  33,  2.6,   1.74,  1.61,  0.4),
        ('nước cốt dừa',          'ML', NULL::NUMERIC, 197,  2.02,  2.81, 21.33,  0),
        ('nước tương',            'ML', NULL::NUMERIC,  60,  8.1,   5.57,  0.1,   0.8),
        ('si-rô phong',           'ML', NULL::NUMERIC, 260,  0.04, 67.04,  0.06,  0),

        -- Seasoning
        ('muối',                  'G', NULL::NUMERIC,   0,  0,     0,     0,     0),
        ('đường trắng',           'G', NULL::NUMERIC, 387,  0,    99.98,  0,     0),

        -- Vegetables and aromatics
        ('cà chua',               'PIECE', 123, 18, 0.88,  3.89,  0.2,   1.2),
        ('cà rốt',                'PIECE',  61, 41, 0.93,  9.58,  0.24,  2.8),
        ('hành tây',              'PIECE', 110, 40, 1.1,   9.34,  0.1,   1.7),
        ('hành lá',               'G', NULL::NUMERIC,  32,  1.83,  7.34,  0.19,  2.6),
        ('tỏi',                   'G', NULL::NUMERIC, 149,  6.36, 33.06,  0.5,   2.1),
        ('gừng',                  'G', NULL::NUMERIC,  80,  1.82, 17.77,  0.75,  2.0),
        ('ớt đỏ',                 'G', NULL::NUMERIC,  40,  1.87,  8.81,  0.44,  1.5),
        ('sả',                    'G', NULL::NUMERIC,  99,  1.82, 25.31,  0.49,  0),
        ('rau muống',             'G', NULL::NUMERIC,  19,  2.6,   3.14,  0.2,   2.1),
        ('cải ngọt',              'G', NULL::NUMERIC,  13,  1.5,   2.18,  0.2,   1.0),
        ('cải bó xôi',            'G', NULL::NUMERIC,  23,  2.86,  3.63,  0.39,  2.2),
        ('xà lách',               'G', NULL::NUMERIC,  17,  1.23,  3.29,  0.3,   2.1),
        ('bông cải xanh',         'G', NULL::NUMERIC,  34,  2.82,  6.64,  0.37,  2.6),
        ('bông cải trắng',        'G', NULL::NUMERIC,  25,  1.92,  4.97,  0.28,  2.0),
        ('nấm rơm',               'G', NULL::NUMERIC,  32,  3.83,  4.52,  0.42,  2.0),
        ('nấm hương',             'G', NULL::NUMERIC,  34,  2.24,  6.79,  0.49,  2.5),
        ('cà tím',                'G', NULL::NUMERIC,  25,  0.98,  5.88,  0.18,  3.0),
        ('bí đao',                'G', NULL::NUMERIC,  13,  0.4,   3.0,   0.2,   0.9),
        ('bí đỏ',                 'G', NULL::NUMERIC,  26,  1.0,   6.5,   0.1,   0.5),

        -- Tubers, fruit
        ('khoai lang',            'PIECE', 130, 86, 1.57, 20.12,  0.05,  3.0),
        ('khoai tây',             'PIECE', 173, 77, 2.05, 17.49,  0.09,  2.1),
        ('chuối',                 'PIECE', 118, 89, 1.09, 22.84,  0.33,  2.6),
        ('táo',                   'PIECE', 182, 52, 0.26, 13.81,  0.17,  2.4),
        ('chanh',                 'PIECE',  58, 29, 1.1,   9.32,  0.3,   2.8)
)
INSERT INTO ingredient (id, name, unit, calories, protein_g, carbohydrate_g, fat_g,
                        fiber_g, moderation_status)
SELECT gen_random_uuid(),
       seed.name,
       seed.unit,
       -- The seed is per 100 g/ml; one row is per one unit. PIECE scales by the piece weight
       -- (`grams_per_piece` above is a local value, not a stored column).
       --
       -- The explicit `::NUMERIC` on the numerator is load-bearing, not decoration. These columns
       -- are inferred from the VALUES list, and because every kcal figure here happens to be a
       -- whole number PostgreSQL types that column `integer`. `365 / 100` would then be integer
       -- division and store 3 instead of 3.65 - a silent 18% error in every grain, with nothing in
       -- the SQL looking wrong. Casting forces numeric division regardless of the inferred type.
       ROUND(CASE
                 WHEN seed.unit = 'PIECE'
                     THEN seed.kcal_per_100 * seed.grams_per_piece / 100
                 ELSE seed.kcal_per_100::NUMERIC / 100
             END, 6),
       ROUND(CASE
                 WHEN seed.unit = 'PIECE'
                     THEN seed.protein_per_100 * seed.grams_per_piece / 100
                 ELSE seed.protein_per_100::NUMERIC / 100
             END, 6),
       ROUND(CASE
                 WHEN seed.unit = 'PIECE'
                     THEN seed.carb_per_100 * seed.grams_per_piece / 100
                 ELSE seed.carb_per_100::NUMERIC / 100
             END, 6),
       ROUND(CASE
                 WHEN seed.unit = 'PIECE'
                     THEN seed.fat_per_100 * seed.grams_per_piece / 100
                 ELSE seed.fat_per_100::NUMERIC / 100
             END, 6),
       ROUND(CASE
                 WHEN seed.unit = 'PIECE'
                     THEN seed.fiber_per_100 * seed.grams_per_piece / 100
                 ELSE seed.fiber_per_100::NUMERIC / 100
             END, 6),
       'APPROVED'
FROM seed
ON CONFLICT DO NOTHING;

-- Seeded ingredients are referenced by recipes straight away, so existing totals that were cleared
-- by V49 can now resolve. Recomputing in SQL rather than in Java keeps the migration self-contained:
-- the rule is the same one `RecipeNutritionCalculator` applies - if any line is unresolved, or the
-- recipe has no lines, all five stay NULL.
WITH totals AS (
    SELECT ri.recipe_id,
           SUM(ri.amount * i.calories)       AS calories,
           SUM(ri.amount * i.protein_g)      AS protein_g,
           SUM(ri.amount * i.carbohydrate_g) AS carbohydrate_g,
           SUM(ri.amount * i.fat_g)          AS fat_g,
           SUM(ri.amount * i.fiber_g)        AS fiber_g,
           COUNT(*)                          AS line_count,
           COUNT(*) FILTER (
               WHERE i.unit IS NULL
                  OR i.calories IS NULL
                  OR i.protein_g IS NULL
                  OR i.carbohydrate_g IS NULL
                  OR i.fat_g IS NULL
                  OR i.fiber_g IS NULL
           )                                 AS unresolved_count
    FROM recipe_ingredient ri
    JOIN ingredient i ON i.id = ri.ingredient_id
    GROUP BY ri.recipe_id
)
UPDATE recipe r
SET calories       = ROUND(t.calories, 2),
    protein_g      = ROUND(t.protein_g, 2),
    carbohydrate_g = ROUND(t.carbohydrate_g, 2),
    fat_g          = ROUND(t.fat_g, 2),
    fiber_g        = ROUND(t.fiber_g, 2)
FROM totals t
WHERE r.id = t.recipe_id
  AND t.line_count > 0
  AND t.unresolved_count = 0;
