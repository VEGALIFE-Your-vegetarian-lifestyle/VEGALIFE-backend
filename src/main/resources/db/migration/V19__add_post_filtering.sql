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

-- 2) filter state, publish intent (BR-FILTER-005) and queueing timestamp (BR-FILTER-009)
ALTER TABLE post
    ADD COLUMN flag VARCHAR(16),
    ADD COLUMN publish_intent BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN filter_queued_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_post_flag
        CHECK (flag IS NULL OR flag IN ('PENDING','PASSED','REJECTED','NEEDS_REVIEW'));

-- Stale-pending sweep probe: pending posts ordered by queue time
CREATE INDEX idx_post_filter_pending ON post(filter_queued_at) WHERE flag = 'PENDING';

-- 3) the ADR-005 outbox gains a second channel (no new broker)
ALTER TABLE outbound_message
    DROP CONSTRAINT chk_outbound_message_channel;
ALTER TABLE outbound_message
    ADD CONSTRAINT chk_outbound_message_channel
        CHECK (channel IN ('EMAIL','CONTENT_FILTER'));

-- 4) one audit row per filter run (BR-FILTER-010; V18 pattern).
--    Entity and repository arrive with the adapter in Phase 9.
CREATE TABLE post_filter_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES post(id) ON DELETE CASCADE,
    flag VARCHAR(16) NOT NULL CHECK (flag IN ('PENDING','PASSED','REJECTED','NEEDS_REVIEW')),
    score DOUBLE PRECISION,
    reasons TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_post_filter_log_post_id ON post_filter_log(post_id);
