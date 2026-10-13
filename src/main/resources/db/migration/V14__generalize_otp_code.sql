-- V14__generalize_otp_code.sql
-- Rename password_reset_otp -> otp_code and add purpose discriminator (ADR-004)
-- Existing rows default to PASSWORD_RESET (password-reset codes from V13)

ALTER TABLE password_reset_otp RENAME TO otp_code;

ALTER TABLE otp_code
    ADD COLUMN purpose VARCHAR(32) NOT NULL DEFAULT 'PASSWORD_RESET';

ALTER INDEX idx_password_reset_otp_user_id RENAME TO idx_otp_code_user_id;
ALTER INDEX idx_password_reset_otp_expires RENAME TO idx_otp_code_expires;
