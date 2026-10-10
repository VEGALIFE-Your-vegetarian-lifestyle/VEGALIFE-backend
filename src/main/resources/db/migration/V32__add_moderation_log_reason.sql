-- V32__add_moderation_log_reason.sql
-- Issue #5: an admin moderation action records why it was taken, alongside actor/action/target/time.
-- Nullable and additive so existing writers (EDIT_POST, DELETE_POST, HIDE_POST, UNHIDE_POST) are unaffected.

ALTER TABLE moderation_log ADD COLUMN reason TEXT;
