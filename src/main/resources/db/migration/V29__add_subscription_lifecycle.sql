-- V29__add_subscription_lifecycle.sql
-- Subscription lifecycle (issue #112): free user_id from its one-row unique
-- constraint so a member can accumulate subscription history, and add the
-- columns/statuses the lifecycle needs (docs/feats/subscription-lifecycle.md
-- FR-001/FR-002).

ALTER TABLE ai_subscription DROP CONSTRAINT ai_subscription_user_id_key;

ALTER TABLE ai_subscription
    ADD COLUMN extended_from_id UUID REFERENCES ai_subscription(id),
    ADD COLUMN cancelled_at TIMESTAMPTZ;

ALTER TABLE ai_subscription DROP CONSTRAINT ai_subscription_status_check;
ALTER TABLE ai_subscription
    ADD CONSTRAINT ai_subscription_status_check
    CHECK (status IN ('active','cancelled','past_due','scheduled','expired'));

-- Backs the expiry sweep (status + due-date scan) and the in-effect lookups.
CREATE INDEX idx_ai_subscription_status_renewal_date
    ON ai_subscription(status, renewal_date);
