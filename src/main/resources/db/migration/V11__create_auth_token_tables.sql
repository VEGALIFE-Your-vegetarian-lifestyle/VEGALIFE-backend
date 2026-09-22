-- V11__create_auth_token_tables.sql
-- Refresh token and access token blacklist tables

-- Refresh tokens for JWT token refresh flow
CREATE TABLE refresh_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,  -- SHA-256 hex hash of raw refresh token
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_token_user_id ON refresh_token(user_id);
CREATE INDEX idx_refresh_token_hash ON refresh_token(token_hash);
CREATE INDEX idx_refresh_token_expires ON refresh_token(expires_at);

-- Access token blacklist for immediate revocation
-- Identified by JWT ID (jti) + issuer (iss)
CREATE TABLE blacklist_token (
    jti VARCHAR(36) NOT NULL,
    issuer VARCHAR(100) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (jti, issuer)
);

CREATE INDEX idx_blacklist_token_expires ON blacklist_token(expires_at);