-- V57__store_post_flag_detail_as_json.sql
-- ADR-024: flag_detail is one JSON snapshot per blocker, not a free-form caption.
--
-- Before this migration the column held three unrelated shapes:
--   FILTER_FAILED     score=0.312
--   SIMILAR_CONTENT   SIMILAR_CONTENT_POST_<postId>
--   FILTER_SKIPPED    FILTER_DISABLED | FILTER_SWEPT | FILTER_UNAVAILABLE: <cause>
-- and a 64-character limit silently truncated the last of them. Each blocker now
-- writes one object, always carrying `kind`, with the fields that blocker owns:
--
--   {"kind":"FILTER_FAILED","outcome":"FAIL","score":0.312,"threshold":0.45}
--   {"kind":"SIMILAR_CONTENT","postId":"<uuid>","similarity":0.9712,"threshold":0.85}
--   {"kind":"FILTER_SKIPPED","cause":"DISABLED"|"SWEPT"|"UNAVAILABLE"}
--
-- The backfill carries only what was stored. Thresholds, similarity and outcome were
-- never persisted, so rows blocked before this migration keep their score/id/cause and
-- nothing else; inventing the missing numbers from today's settings would misreport
-- the decision that was actually made. Free-text causes move out of the row entirely -
-- the full provider message already lives in moderation_log.reason.

ALTER TABLE post DROP CONSTRAINT chk_post_flag_detail_requires_flag;

ALTER TABLE post
    ALTER COLUMN flag_detail TYPE JSONB
        USING CASE
                  WHEN flag_detail IS NULL OR flag IS NULL THEN NULL
                  WHEN flag = 'SIMILAR_CONTENT'
                      THEN jsonb_build_object(
                              'kind', 'SIMILAR_CONTENT',
                              'postId', regexp_replace(flag_detail, '^SIMILAR_CONTENT_POST_', ''))
                  WHEN flag = 'FILTER_FAILED' AND flag_detail ~ '^score=[0-9]+(\.[0-9]+)?$'
                      THEN jsonb_build_object(
                              'kind', 'FILTER_FAILED',
                              'score', substring(flag_detail FROM 7)::numeric)
                  WHEN flag = 'FILTER_SKIPPED'
                      THEN jsonb_build_object(
                              'kind', 'FILTER_SKIPPED',
                              'cause', CASE
                                           WHEN flag_detail LIKE 'FILTER_DISABLED%' THEN 'DISABLED'
                                           WHEN flag_detail LIKE 'FILTER_SWEPT%' THEN 'SWEPT'
                                           ELSE 'UNAVAILABLE'
                                       END)
                  ELSE jsonb_build_object('kind', flag)
              END;

-- flag_detail only qualifies a flag; it is never a fact on its own.
ALTER TABLE post
    ADD CONSTRAINT chk_post_flag_detail_requires_flag
        CHECK (flag_detail IS NULL OR flag IS NOT NULL);

-- The snapshot must be an object: `->>` on an array yields NULL, which would slip past
-- the discriminator check below.
ALTER TABLE post
    ADD CONSTRAINT chk_post_flag_detail_is_object
        CHECK (flag_detail IS NULL OR jsonb_typeof(flag_detail) = 'object');

-- The snapshot describes the blocker it is attached to, so the two can never disagree.
ALTER TABLE post
    ADD CONSTRAINT chk_post_flag_detail_kind_matches_flag
        CHECK (flag_detail IS NULL OR flag_detail->>'kind' = flag);
