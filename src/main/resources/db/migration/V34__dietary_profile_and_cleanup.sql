-- V34__dietary_profile_and_cleanup.sql
-- Move dietary info to the user profile (one JSONB column), drop the
-- per-menu dietary preference (now profile-owned; per-week nuance lives in
-- menu.notes), and drop two ai_conversation columns declared in V10 that
-- were never read or written by any code path.

ALTER TABLE user_profile
    ADD COLUMN dietary_preferences JSONB;

ALTER TABLE menu
    DROP COLUMN dietary_preferences;

ALTER TABLE ai_conversation
    DROP COLUMN summary,
    DROP COLUMN recent_messages;
