-- V33__add_comment_is_edited.sql
-- Adds the edited indicator for the edit-comment feature (issue #42)

ALTER TABLE comment ADD COLUMN is_edited BOOLEAN NOT NULL DEFAULT FALSE;
