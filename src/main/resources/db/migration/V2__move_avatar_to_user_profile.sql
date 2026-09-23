-- V2__move_avatar_to_user_profile.sql
-- Move avatar_url from user table to user_profile table

-- Add avatar_url column to user_profile
ALTER TABLE user_profile
    ADD COLUMN avatar_url TEXT;

-- Copy existing avatar_url data from user to user_profile
UPDATE user_profile up
SET avatar_url = u.avatar_url
FROM "user" u
WHERE up.user_id = u.id
  AND u.avatar_url IS NOT NULL;

-- Remove avatar_url from user table
ALTER TABLE "user"
    DROP COLUMN avatar_url;