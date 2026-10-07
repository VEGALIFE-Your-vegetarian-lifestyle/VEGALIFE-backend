-- V28__add_subscription_id_to_payment_ledger.sql
-- Payment history (issue #111): link a fulfilled ledger row to the subscription it
-- produced (docs/feats/payment-history.md FR-002), and back the member/admin history
-- scan with (user_id, created_at DESC). Existing rows stay NULL (no backfill); failed
-- and refunded rows are never linked. ON DELETE SET NULL so removing a subscription
-- never deletes payment history.

ALTER TABLE payment_ledger
    ADD COLUMN subscription_id UUID REFERENCES ai_subscription(id) ON DELETE SET NULL;

CREATE INDEX idx_payment_ledger_user_created_at
    ON payment_ledger(user_id, created_at DESC);
