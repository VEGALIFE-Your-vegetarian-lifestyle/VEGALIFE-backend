-- V39__moderation_status.sql
-- Component-level verification (issue #160, ADR-016).
--
-- Adds moderation_status to the three globally shared reference tables that users can
-- introduce by publishing a recipe. Dishes and ingredients have no owner: RecipeService
-- creates them on demand, and menu_detail.dish_id is NOT NULL, so hiding one breaks saved
-- menus and the meal planner for everyone.
--
-- DEFAULT 'PENDING' is the correct default for NEW rows (a name a member types is
-- unreviewed until an admin approves it) and a destructive one for EXISTING rows: the
-- backfill below therefore ships in this same migration. Deploying the column without it
-- would make every recipe's dish invisible and orphan every menu_detail row. The backfill
-- is not optional and must not be moved to a later migration.
--
-- ingredient had no soft-delete column while recipe and dish did; add it so all three
-- entities delete the same way.

ALTER TABLE dish
    ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

ALTER TABLE ingredient
    ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

ALTER TABLE ingredient
    ADD COLUMN deleted_at TIMESTAMPTZ;

ALTER TABLE recipe
    ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

-- Everything that already exists was live before this feature. None of it was reviewed,
-- but all of it is in use by recipes and menus right now, so treating it as PENDING would
-- retroactively withhold the entire existing catalogue.
UPDATE dish SET moderation_status = 'APPROVED';
UPDATE ingredient SET moderation_status = 'APPROVED';
UPDATE recipe SET moderation_status = 'APPROVED';

-- Narrow the vocabulary at the database level too: an application bug writing a fourth
-- status should fail loudly rather than silently matching no query.
ALTER TABLE dish
    ADD CONSTRAINT chk_dish_moderation_status
        CHECK (moderation_status IN ('PENDING', 'APPROVED', 'REJECTED'));

ALTER TABLE ingredient
    ADD CONSTRAINT chk_ingredient_moderation_status
        CHECK (moderation_status IN ('PENDING', 'APPROVED', 'REJECTED'));

ALTER TABLE recipe
    ADD CONSTRAINT chk_recipe_moderation_status
        CHECK (moderation_status IN ('PENDING', 'APPROVED', 'REJECTED'));

-- Public listings filter on status; admin queues read the non-approved rows.
CREATE INDEX idx_dish_moderation_status ON dish (moderation_status);
CREATE INDEX idx_ingredient_moderation_status ON ingredient (moderation_status);
CREATE INDEX idx_recipe_moderation_status ON recipe (moderation_status);

-- Name lookups resolve a user-supplied name to an existing row before creating one, and
-- must find REJECTED rows too (a rejected name can never be created again).
CREATE INDEX idx_dish_name ON dish (name);
CREATE INDEX idx_ingredient_name ON ingredient (name);
