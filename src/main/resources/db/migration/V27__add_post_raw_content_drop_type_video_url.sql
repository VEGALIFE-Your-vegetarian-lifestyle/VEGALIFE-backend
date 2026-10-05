-- V27__add_post_raw_content_drop_type_video_url.sql
-- Issue #101: posts move to a single rich-text format. raw_content stores the
-- frontend editor's JSON document; plain content stays as the semantic-filtering
-- input (BR-FILTER-*). The blog/video split from V17 is legacy and is dropped.
-- Note: the issue's acceptance criteria name this migration "V23", but V23 was
-- already used by V23__make_user_profile_fields_nullable.sql; V27 is the next
-- free version.

ALTER TABLE post
    ADD COLUMN raw_content JSONB NOT NULL DEFAULT '{}';

ALTER TABLE post
    DROP COLUMN type,
    DROP COLUMN video_url;
