-- V26__add_payment_checkout_columns.sql
-- VNPay checkout (issue #16). The ledger gains our own transaction reference
-- (minted at checkout, unique, what VNPay echoes back in vnp_TxnRef) plus the
-- gateway verdict columns the IPN records. provider / provider_reference keep
-- their #15 meanings: provider = 'VNPAY', provider_reference = vnp_TransactionNo.

ALTER TABLE payment_ledger
    ADD COLUMN txn_ref VARCHAR(64),
    ADD COLUMN response_code VARCHAR(10),
    ADD COLUMN bank_code VARCHAR(32);

-- IPN lookup by echoed reference (one row per checkout, ever).
CREATE UNIQUE INDEX uq_payment_ledger_txn_ref
    ON payment_ledger(txn_ref)
    WHERE txn_ref IS NOT NULL;

-- In-flight reuse lookup: latest pending row per user+plan (BR-PAY-005).
CREATE INDEX idx_payment_ledger_user_plan_pending
    ON payment_ledger(user_id, plan_id, created_at DESC)
    WHERE status = 'pending';
