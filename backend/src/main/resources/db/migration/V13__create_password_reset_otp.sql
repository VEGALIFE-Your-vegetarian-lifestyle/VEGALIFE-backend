-- V13__create_password_reset_otp.sql
-- Password reset OTP codes for the forgot-password flow
-- Stores SHA-256 hex hash of the 6-digit code; raw code never persisted

CREATE TABLE password_reset_otp (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    otp_hash CHAR(64) NOT NULL,  -- SHA-256 hex hash of raw 6-digit OTP
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,  -- NULL = active; set when consumed or superseded
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_reset_otp_user_id ON password_reset_otp(user_id);
CREATE INDEX idx_password_reset_otp_expires ON password_reset_otp(expires_at);
