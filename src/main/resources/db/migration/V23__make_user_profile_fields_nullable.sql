-- V22__make_user_profile_fields_nullable.sql
-- Issue #88 / BR-PROFILE-003: an empty profile row can now be created on first
-- read, so body metrics must tolerate NULL until the user fills them in.

ALTER TABLE user_profile ALTER COLUMN height_cm DROP NOT NULL;
ALTER TABLE user_profile ALTER COLUMN weight_kg DROP NOT NULL;
ALTER TABLE user_profile ALTER COLUMN age DROP NOT NULL;
ALTER TABLE user_profile ALTER COLUMN gender DROP NOT NULL;
