-- V47__replace_platform_settings.sql
-- Issue #13 follow-up: the 4 settings seeded by V41 were read-model only (no service ever read
-- them). Replace them with 8 keys backed by a real business rule, in 3 categories (media, content,
-- ai), and add updated_by so the audit trail names the admin on the row itself as well as in
-- moderation_log.

UPDATE platform_setting SET value = '' WHERE value IS NULL;
ALTER TABLE platform_setting ALTER COLUMN value SET NOT NULL;

ALTER TABLE platform_setting
    ADD COLUMN updated_by UUID REFERENCES "user"(id) ON DELETE SET NULL;

DELETE FROM platform_setting
 WHERE key IN ('registration_open', 'auto_moderation_threshold',
               'default_monthly_limit_override', 'max_upload_size_mb');

INSERT INTO platform_setting (category, key, value) VALUES
    ('media',   'upload_allowed_image_types',      'image/jpeg,image/png,image/webp'),
    ('media',   'upload_allowed_video_types',      'video/mp4,video/webm'),
    ('media',   'max_image_upload_mb',             '5'),
    ('media',   'max_video_upload_mb',             '50'),
    ('content', 'content_filter_enabled',          'true'),
    ('content', 'content_filter_accept_threshold', '0.70'),
    ('content', 'content_filter_reject_threshold', '0.45'),
    ('ai',      'ai_chat_enabled',                 'true');
