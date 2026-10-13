-- V15__create_outbound_message.sql
-- Persistent outbound message queue (transactional outbox) for email delivery
-- Business transactions enqueue a row; a background drainer delivers with retries.
-- See ADR-005 / issue #49.

CREATE TABLE outbound_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel VARCHAR(16) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    payload JSONB,  -- message body JSON; cleared when the row reaches a terminal status
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,  -- business deadline (OTP expiry); past it the row expires without sending
    locked_at TIMESTAMPTZ,  -- when the current worker claimed the row (visibility timeout)
    locked_by VARCHAR(64),  -- worker id owning the PROCESSING row
    completed_at TIMESTAMPTZ,  -- set when the message was handed to SMTP successfully
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_outbound_message_status
        CHECK (status IN ('PENDING','PROCESSING','COMPLETED','DEFERRED','FAILED','EXPIRED')),
    CONSTRAINT chk_outbound_message_channel
        CHECK (channel IN ('EMAIL')),
    CONSTRAINT chk_outbound_message_attempts CHECK (attempts >= 0)
);

-- Drainer claim probe: filter by due status + time
CREATE INDEX idx_outbound_message_due ON outbound_message(status, next_attempt_at);
-- Retention purge by age
CREATE INDEX idx_outbound_message_created_at ON outbound_message(created_at);
