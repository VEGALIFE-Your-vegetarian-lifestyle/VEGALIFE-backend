-- V30__menu_servings_preferences_and_scheduled_guard.sql
-- Menu domain foundation (issue #126): add the per-meal servings count and the
-- per-menu dietary preferences the menu feature reads, and guard against two
-- scheduled menus overlapping for one user (BR-MENU-003, ADR-010).

ALTER TABLE menu_detail
    ADD COLUMN servings INT NOT NULL DEFAULT 1 CHECK (servings > 0);

ALTER TABLE menu
    ADD COLUMN dietary_preferences TEXT;

-- Range-exclusion on (user_id =, daterange &&) needs GiST support for the uuid
-- equality operator, provided by btree_gist. See ADR-010 for the fallback if
-- the extension cannot be created on the target database.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE menu
    ADD CONSTRAINT uq_menu_scheduled_no_overlap
    EXCLUDE USING gist (
        user_id WITH =,
        daterange(start_date, end_date, '[]') WITH &&
    ) WHERE (status = 'scheduled');
