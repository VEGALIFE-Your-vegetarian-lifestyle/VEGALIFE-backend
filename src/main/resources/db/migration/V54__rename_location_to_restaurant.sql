-- V54__rename_location_to_restaurant.sql
-- Issue #194: the directory only ever holds restaurants; rename `location` to
-- `restaurant` and drop the unused `place_type` column/enum/filter entirely.
-- Every statement below is a rename or a column/constraint drop — no row is
-- ever deleted.

ALTER TABLE location RENAME TO restaurant;

-- place_type is removed entirely (issue #194): drop its index and CHECK
-- constraint before dropping the column itself.
DROP INDEX IF EXISTS idx_location_place_type;
ALTER TABLE restaurant DROP CONSTRAINT IF EXISTS location_place_type_check;
ALTER TABLE restaurant DROP COLUMN place_type;

-- Surviving indexes keep their original definitions; only their names follow
-- the table rename.
ALTER INDEX idx_location_active RENAME TO idx_restaurant_active;
ALTER INDEX idx_location_coords RENAME TO idx_restaurant_coords;
ALTER INDEX idx_location_opening_hours RENAME TO idx_restaurant_opening_hours;

-- post.location_id was never mapped by the JPA entity, but the column, its
-- index, and its FK are real (V7__create_post_tables.sql) and must follow the
-- rename too. The FK itself already targets the renamed table automatically
-- (Postgres tracks it by relation OID, not by name) and keeps its original
-- ON DELETE SET NULL behaviour.
ALTER TABLE post RENAME COLUMN location_id TO restaurant_id;
ALTER INDEX idx_post_location_id RENAME TO idx_post_restaurant_id;
ALTER TABLE post RENAME CONSTRAINT post_location_id_fkey TO post_restaurant_id_fkey;
