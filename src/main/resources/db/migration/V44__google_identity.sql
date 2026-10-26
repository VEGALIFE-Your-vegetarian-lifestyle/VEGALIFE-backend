-- V44__google_identity.sql
-- Google sign-in (issue #31, ADR-017, BR-AUTH-022/023):
--   * a dedicated identity table links a provider subject to a user, so the
--     linkage survives email changes and a second provider adds rows not columns;
--   * password_hash becomes nullable because a Google-created account has no
--     local password (BR-AUTH-023).

ALTER TABLE "user" ALTER COLUMN password_hash DROP NOT NULL;

CREATE TABLE user_identity (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL CHECK (provider IN ('GOOGLE')),
    -- The provider's stable subject claim (Google's `sub`). Never the email:
    -- an address can be reassigned, a subject cannot.
    provider_subject VARCHAR(255) NOT NULL,
    email_at_provider VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- One account per provider subject and one subject per account+provider.
    CONSTRAINT uq_user_identity_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uq_user_identity_user_provider UNIQUE (user_id, provider)
);

CREATE INDEX idx_user_identity_user_id ON user_identity(user_id);
