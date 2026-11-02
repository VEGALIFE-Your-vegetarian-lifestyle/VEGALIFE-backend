-- V45__rename_username_to_display_name.sql
-- ADR-019: replace the unique login handle `username` with a non-unique,
-- human-facing `display_name`.
--
-- The column is renamed rather than dropped and re-added so existing accounts
-- keep their value (the old username becomes the initial display name); no
-- backfill statement is needed. The UNIQUE constraint is removed because a
-- display name is not an identifier: two people may legitimately share one, and
-- a Google account's name is not guaranteed unique.
--
-- `email` remains the sole unique login identifier.

-- V1 declared the column as `username VARCHAR(50) NOT NULL UNIQUE`, so Postgres
-- generated the default constraint name. Drop it by that name; IF EXISTS keeps
-- this migration safe on a database where it was already removed by hand.
ALTER TABLE "user" DROP CONSTRAINT IF EXISTS user_username_key;

ALTER TABLE "user" RENAME COLUMN username TO display_name;

-- Human names (Google's `name` claim) are longer than the 3-50 handle this
-- column used to hold.
ALTER TABLE "user" ALTER COLUMN display_name TYPE VARCHAR(100);

COMMENT ON COLUMN "user".display_name IS
    'Human-facing name shown in the UI. Not unique, never used to authenticate.';
