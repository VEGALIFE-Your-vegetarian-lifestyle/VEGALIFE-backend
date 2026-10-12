-- V16__add_otp_verified_at.sql
-- Optional "verified" stage on the OTP lifecycle (ADR-006, issue #62).
-- Set only by purposes whose workflow defers an action after checking the code
-- (PASSWORD_RESET: verify endpoint proves the code, reset endpoint consumes it).
-- EMAIL_VERIFICATION rows keep NULL: that flow verifies and consumes atomically.
-- Existing rows are unaffected (NULL = not yet verified).

ALTER TABLE otp_code ADD COLUMN verified_at TIMESTAMPTZ NULL;
