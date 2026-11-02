-- V40__add_location_image_urls_description.sql
-- Issue #159: presentation-only fields for the location directory. Forward-only, nullable,
-- no backfill — existing rows read image_urls back as NULL.

ALTER TABLE location ADD COLUMN image_urls TEXT[], ADD COLUMN description TEXT;
