-- V4__create_media_table.sql
-- Media table

CREATE TABLE media (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    media_url TEXT NOT NULL,
    thumbnail_url TEXT,
    description TEXT,
    status VARCHAR(20) NOT NULL CHECK (status IN ('uploading','succeed','failed')),
    duration_seconds INT,
    file_size_bytes BIGINT,
    mime_type VARCHAR(100),
    width INT,
    height INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_media_status ON media(status) WHERE deleted_at IS NULL;