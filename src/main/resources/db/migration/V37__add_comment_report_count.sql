-- V37__add_comment_report_count.sql
-- Issue #6: lets admins sort reported comments first. No report table — a single counter is
-- enough for "how many times has this been reported", and nothing here needs to track who
-- reported what or de-duplicate repeat reports from the same user.

ALTER TABLE comment ADD COLUMN report_count INT NOT NULL DEFAULT 0;
