-- V19__add_post_filtering.sql
-- Post content filtering (issues #33/#34, ADR-007, BR-FILTER-* / BR-POST-010).
-- flag NULL = never filtered (drafts and all pre-existing rows; nothing is backfilled).

-- 1) post.status gains 'flagged' (BR-POST-010). The V7 CHECK is unnamed, so it
--    is located by definition instead of by a guessed constraint name.
DO $$
DECLARE
    cons RECORD;
BEGIN
    FOR cons IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'post'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE post DROP CONSTRAINT %I', cons.conname);
    END LOOP;
END $$;

ALTER TABLE post
    ADD CONSTRAINT chk_post_status
        CHECK (status IN ('created','processed','published','unpublished','hidden','flagged'));

-- 2) filter state only: the enqueue clock is the outbound row's created_at (BR-FILTER-009)
--    and "does this post want to be live" is derived from status, not a publish-intent column.
ALTER TABLE post
    ADD COLUMN flag VARCHAR(16),
    ADD CONSTRAINT chk_post_flag
        CHECK (flag IS NULL OR flag IN ('PENDING','PASSED','REJECTED','NEEDS_REVIEW'));

-- 3) the ADR-005 outbox gains a second channel (no new broker)
ALTER TABLE outbound_message
    DROP CONSTRAINT chk_outbound_message_channel;
ALTER TABLE outbound_message
    ADD CONSTRAINT chk_outbound_message_channel
        CHECK (channel IN ('EMAIL','CONTENT_FILTER'));
