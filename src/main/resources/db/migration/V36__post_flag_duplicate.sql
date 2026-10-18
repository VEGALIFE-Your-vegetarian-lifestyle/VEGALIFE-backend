-- V36__post_flag_duplicate.sql
-- Duplicate post detection (issue #156, ADR-015).
--
-- post.flag gains DUPLICATE. V19 created chk_post_flag with an explicit name,
-- so it is dropped by name rather than located by definition, unlike the
-- unnamed status constraint V19 had to discover by scanning pg_constraint.
--
-- The verdict still lives only in post.flag, per ADR-011: status stays on its
-- own axis, and a duplicate is unpublished rather than given a new status.

ALTER TABLE post DROP CONSTRAINT chk_post_flag;

ALTER TABLE post
    ADD CONSTRAINT chk_post_flag
        CHECK (flag IS NULL
            OR flag IN ('PENDING','PASSED','REJECTED','NEEDS_REVIEW','DUPLICATE'));
