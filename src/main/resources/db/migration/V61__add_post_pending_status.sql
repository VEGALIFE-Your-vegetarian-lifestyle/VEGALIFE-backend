-- V61__add_post_pending_status.sql
-- ADR-029: `pending` is its own lifecycle state, and `processing` means only "a filter run is
-- outstanding".
--
-- Until now `processing` covered both "the verdict has not been reached" and "the verdict is in
-- and an admin has not decided", so the review queue and the dashboard had to infer "held" from
-- `flag IS NOT NULL`. The two facts now have one status each:
--
--      processing   submitted; a filter run is outstanding     flag IS NULL
--      pending      verdict recorded; an admin decision is due  flag carries the verdict
--
-- Media does not get its own status or flag: a media-bearing post that the text filter passed
-- also waits at `pending` with FILTER_PASSED, because the filter never looks at media.

-- 1) status vocabulary --------------------------------------------------------

ALTER TABLE post DROP CONSTRAINT chk_post_status;

-- Existing rows held with a blocker are exactly the posts waiting for a human: they move to
-- `pending`. A `processing` row with no flag is genuinely in flight and stays put. This is a
-- status remap, not a backfill of verdict data: no flag or score is invented.
UPDATE post
   SET status = 'pending'
 WHERE status = 'processing'
   AND flag IS NOT NULL;

ALTER TABLE post
    ADD CONSTRAINT chk_post_status
        CHECK (status IN ('drafted','processing','pending','published','unpublished'));

-- 2) the two axes can no longer overlap ---------------------------------------

-- A run in flight has no verdict. Every writer sets the flag only once the run ends, so this is
-- provable rather than conventional; it also stops a future writer from parking a verdict on a
-- post that is still being filtered.
ALTER TABLE post
    ADD CONSTRAINT chk_post_processing_has_no_flag
        CHECK (status <> 'processing' OR flag IS NULL);

-- `pending` is deliberately not required to carry a flag: a post published before V58 has
-- flag = NULL ("no verdict recorded"), and moving such a post back to review must not invent a
-- verdict it never had (ADR-025).
