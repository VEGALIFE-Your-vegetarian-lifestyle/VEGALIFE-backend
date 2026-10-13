-- V20__add_media_upload_lifecycle.sql
-- BR-MEDIA-002 / BR-MEDIA-004 (issue #38): a row created for an upload grant has
-- no URL yet, so media_url stops being mandatory. uploaded_by records who issued
-- the grant. external_id stores the provider object key at confirmation so
-- verification never re-derives the naming convention.

ALTER TABLE media
    ALTER COLUMN media_url DROP NOT NULL,
    ADD COLUMN uploaded_by UUID REFERENCES "user"(id),
    ADD COLUMN external_id VARCHAR(255);
