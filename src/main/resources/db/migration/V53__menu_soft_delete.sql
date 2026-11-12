-- V53__menu_soft_delete.sql
-- Member-facing menu deletion (no driving issue; requested as a follow-up to the
-- menu domain). A menu is soft-deleted: the row and its menu_detail days stay in
-- the database, and deleted_at marks the moment the owner removed it.
--
-- Why the exclusion constraint is rebuilt: V30 declared it as
--   WHERE (status = 'scheduled')
-- so a soft-deleted row that was scheduled when it was deleted would keep
-- occupying its week forever, permanently blocking that member from scheduling
-- the same week again. The predicate must ignore deleted rows:
--   WHERE (status = 'scheduled' AND deleted_at IS NULL)
--
-- Rebuilding an EXCLUDE constraint means dropping and re-adding it: PostgreSQL
-- has no ALTER CONSTRAINT for the predicate.

ALTER TABLE menu
    ADD COLUMN deleted_at TIMESTAMPTZ;

-- Supports the "not deleted" filter that every menu read now carries, together
-- with the owner scoping it always had.
CREATE INDEX idx_menu_user_id_deleted_at ON menu(user_id, deleted_at);

ALTER TABLE menu
    DROP CONSTRAINT uq_menu_scheduled_no_overlap;

ALTER TABLE menu
    ADD CONSTRAINT uq_menu_scheduled_no_overlap
    EXCLUDE USING gist (
        user_id WITH =,
        daterange(start_date, end_date, '[]') WITH &&
    ) WHERE (status = 'scheduled' AND deleted_at IS NULL);
