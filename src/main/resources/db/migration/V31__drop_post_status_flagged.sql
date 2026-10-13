-- V31__drop_post_status_flagged.sql
-- Retire post.status = 'flagged'. The filter verdict lives only in post.flag;
-- a non-PASSED verdict is represented by status 'unpublished' (BR-FILTER-008/009).
-- 'unpublished' already exists in the CHECK, so no new status value is added.

-- 1) Rewrite existing rejected/uncertain rows before shrinking the CHECK.
UPDATE post SET status = 'unpublished' WHERE status = 'flagged';

-- 2) Shrink the status CHECK. V19 named it chk_post_status; drop that name and
--    re-add without 'flagged'.
ALTER TABLE post DROP CONSTRAINT chk_post_status;
ALTER TABLE post
    ADD CONSTRAINT chk_post_status
        CHECK (status IN ('created','processed','published','unpublished','hidden'));
