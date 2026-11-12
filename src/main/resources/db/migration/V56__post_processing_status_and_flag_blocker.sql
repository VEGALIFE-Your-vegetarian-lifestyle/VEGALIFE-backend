-- V56__post_processing_status_and_flag_blocker.sql
-- ADR-023: status is the lifecycle, flag is why a post is held, and the two can no longer disagree.
--
-- 1) status vocabulary
--      created   -> drafted      (the author has no publish intent, or withdrew)
--      processed -> processing   (submitted; a verdict is outstanding or a blocker is recorded)
--      hidden    -> removed      (withdrawing is the author's unlist; there is no admin hide)
--    unpublished keeps its meaning: an admin rejected the post.
--
-- 2) flag becomes the blocking reason and only exists while status = 'processing'
--      PASSED        -> NULL    (publishing clears the flag)
--      PENDING       -> NULL    (in flight is status = processing with no flag)
--      REJECTED      -> FILTER_FAILED
--      NEEDS_REVIEW  -> FILTER_FAILED
--      DUPLICATE     -> SIMILAR_CONTENT
--
-- The backfill below is one-time and irreversible. Pre-flight (optionally, on a copy):
--   SELECT status, flag, (rejection_reason IS NOT NULL) AS admin_rejected, count(*)
--     FROM post GROUP BY 1, 2, 3 ORDER BY 1, 2, 3;

-- 1) status ------------------------------------------------------------------

ALTER TABLE post DROP CONSTRAINT chk_post_status;

-- A withdrawn post: keep it, it is exactly 'drafted'.
UPDATE post SET status = 'drafted' WHERE status = 'created';

-- 'processed' was never written by this codebase; keep the rewrite for databases that
-- inherited the value from an older build.
UPDATE post SET status = 'processing' WHERE status = 'processed';

-- A hidden post had passed the filter: it was publicly visible before the hide, so it is
-- restored as published. published_at was nulled by the hide and is recovered from updated_at.
UPDATE post
   SET status = 'published',
       published_at = COALESCE(published_at, updated_at)
 WHERE status = 'hidden' AND flag = 'PASSED';

-- A hidden post with no flag was never filtered (a draft an admin hid): return it to its author.
UPDATE post
   SET status = 'drafted'
 WHERE status = 'hidden' AND flag IS NULL;

-- Every other hidden post was held by the filter and never seen by a human: it needs a verdict.
UPDATE post SET status = 'processing' WHERE status = 'hidden';

-- A machine-held post (filter refused or was unsure) that no admin ever rejected belongs in the
-- review queue. `rejection_reason` is written only by an admin rejection (V51), so it is the
-- discriminator between "the filter held this" and "an admin refused this".
UPDATE post
   SET status = 'processing'
 WHERE status = 'unpublished'
   AND rejection_reason IS NULL
   AND flag IN ('REJECTED', 'NEEDS_REVIEW', 'DUPLICATE');

-- A submitted post waiting on the filter was stored as 'created' with flag PENDING; that is now
-- status 'processing' with no flag.
UPDATE post SET status = 'processing' WHERE status = 'created' AND flag = 'PENDING';

ALTER TABLE post
    ADD CONSTRAINT chk_post_status
        CHECK (status IN ('drafted','processing','published','unpublished'));

-- 2) flag --------------------------------------------------------------------

ALTER TABLE post ALTER COLUMN flag TYPE VARCHAR(32);
ALTER TABLE post DROP CONSTRAINT chk_post_flag;

-- The application has never persisted anything longer than 'NEEDS_REVIEW'; the wider column is
-- headroom for a future blocker name, not a place to put data (that is flag_detail).

ALTER TABLE post ADD COLUMN flag_detail VARCHAR(64);

UPDATE post
   SET flag = CASE
                WHEN flag IN ('REJECTED', 'NEEDS_REVIEW') THEN 'FILTER_FAILED'
                WHEN flag = 'DUPLICATE' THEN 'SIMILAR_CONTENT'
                ELSE NULL
              END
 WHERE flag IS NOT NULL;

-- The invariant: a post that is not being held carries no blocker. This also clears the legacy
-- flags left on published/withdrawn rows by the old "keep the flag" behaviour.
UPDATE post SET flag = NULL WHERE status <> 'processing';

ALTER TABLE post
    ADD CONSTRAINT chk_post_flag
        CHECK (flag IS NULL OR flag IN ('FILTER_FAILED','SIMILAR_CONTENT','FILTER_SKIPPED'));

-- Provable separation: a flag can only exist while the post is held.
ALTER TABLE post
    ADD CONSTRAINT chk_post_flag_requires_processing
        CHECK (flag IS NULL OR status = 'processing');

-- flag_detail only qualifies a flag; it is never a fact on its own.
ALTER TABLE post
    ADD CONSTRAINT chk_post_flag_detail_requires_flag
        CHECK (flag_detail IS NULL OR flag IS NOT NULL);
