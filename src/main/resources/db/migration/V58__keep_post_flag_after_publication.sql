-- V58__keep_post_flag_after_publication.sql
-- ADR-025: flag records the last verdict and outlives publication.
--
-- Until now publishing was the one thing that erased the verdict: a PASS deleted the flag
-- instead of storing one, and an admin approval deleted whatever blocker was there. Both
-- lost information a reviewer actually asks for later ("did this pass, or did a human
-- override it?"), and the public API had already been unable to tell the two apart.
--
-- 1) FILTER_PASSED joins the vocabulary, written by the filter's own PASS.
--      published + FILTER_PASSED     the filter cleared it
--      published + FILTER_FAILED     an admin approved a post the filter refused
--    An approval keeps the blocker it found; it does not rewrite it to FILTER_PASSED.
--
-- 2) A flag is now allowed on every status except 'drafted'. Rejection keeps the blocker
--    next to its rejection_reason, because together they are the decision record. Only a
--    withdrawal clears it: a draft has no verdict, and pretending it does would describe a
--    run that no longer applies.
--
-- No backfill. Rows already published never stored the score or the threshold of the run
-- that published them, so they keep flag = NULL, meaning "no verdict recorded", rather than
-- a FILTER_PASSED snapshot that would invent the numbers it reports.

ALTER TABLE post DROP CONSTRAINT chk_post_flag;

ALTER TABLE post
    ADD CONSTRAINT chk_post_flag
        CHECK (flag IS NULL
               OR flag IN ('FILTER_FAILED','SIMILAR_CONTENT','FILTER_SKIPPED','FILTER_PASSED'));

-- Provable separation: a verdict exists for everything that has been submitted, and for
-- nothing that has not. The old constraint said "flag only while processing"; that is no
-- longer true, so it is replaced rather than kept under a name that would now lie.
ALTER TABLE post DROP CONSTRAINT chk_post_flag_requires_processing;

ALTER TABLE post
    ADD CONSTRAINT chk_post_flag_absent_on_draft
        CHECK (flag IS NULL OR status <> 'drafted');
