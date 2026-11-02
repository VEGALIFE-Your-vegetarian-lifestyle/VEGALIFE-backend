-- V41__create_platform_setting_table.sql
-- Issue #13: admin-configurable platform settings, grouped by category. Changes are audited via
-- the existing moderation_log table (action = UPDATE_SETTING, target_type = SETTING).

CREATE TABLE platform_setting (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category VARCHAR(30) NOT NULL,
    key VARCHAR(100) NOT NULL UNIQUE,
    value TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_platform_setting_category ON platform_setting(category);

-- Seed the initial concrete set of configurable items (issue #13).
INSERT INTO platform_setting (category, key, value) VALUES
    ('auth', 'registration_open', 'true'),
    ('content', 'auto_moderation_threshold', '0.5'),
    ('ai', 'default_monthly_limit_override', ''),
    ('limits', 'max_upload_size_mb', '50');
