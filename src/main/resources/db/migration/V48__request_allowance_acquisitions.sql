-- V48__request_allowance_acquisitions.sql
-- Non-expiring AI request allowances (issue #181, ADR-021).
--
-- Replaces the subscription lifecycle with an acquisition history: one
-- ai_subscription row per plan a member acquired, bought or claimed.
-- payment_ledger stops describing plans and describes money only; the plan
-- link moves onto the acquisition row.
--
-- THIS MIGRATION IS IRREVERSIBLE. After it runs, `ai_subscription.payment_id
-- IS NULL` means "claimed for free". The guard in section 3 aborts the whole
-- migration rather than silently converting an unmatched purchase into a
-- free grant.

-- ---------------------------------------------------------------------------
-- 1. ai_plan: a description, and a quota name that matches reality
-- ---------------------------------------------------------------------------

ALTER TABLE ai_plan ADD COLUMN description TEXT;

-- There is no monthly reset any more; the old name would document behaviour
-- this schema no longer has.
ALTER TABLE ai_plan RENAME COLUMN monthly_request_limit TO request_quota;

UPDATE ai_plan
   SET request_quota = 5,
       description   = 'Free starter credits — 5 AI requests, claimable once per account'
 WHERE code = 'FREE';

-- No `claimable` column: a plan is claimable exactly when price_amount = 0.

-- ---------------------------------------------------------------------------
-- 2. ai_subscription: point at the payment that produced it
-- ---------------------------------------------------------------------------

ALTER TABLE ai_subscription
    ADD COLUMN payment_id UUID REFERENCES payment_ledger(id) ON DELETE CASCADE;

-- ON DELETE CASCADE is mandatory, not stylistic: with ON DELETE SET NULL a
-- deleted payment would flip the acquisition to payment_id = NULL, which this
-- schema reads as "claimed for free".

-- Exact backfill while payment_ledger.subscription_id still exists (V28).
UPDATE ai_subscription s
   SET payment_id = l.id
  FROM payment_ledger l
 WHERE l.subscription_id = s.id;

-- `granted_at` is the moment the allowance became usable: paid time for a
-- purchase, claim time for a free plan, NULL while a checkout is still open.
ALTER TABLE ai_subscription RENAME COLUMN started_at TO granted_at;
ALTER TABLE ai_subscription ALTER COLUMN granted_at DROP NOT NULL;

-- A payment can exist without a subscription: the old purchase gate could deny
-- fulfilment after the money was taken, and a checkout still waiting on its
-- notification never got one either. Preserve the plan link on an ungranted
-- acquisition instead of losing what was bought. granted_at stays NULL, so no
-- allowance is handed out and the IPN can still complete an open checkout.
INSERT INTO ai_subscription (user_id, plan_id, payment_id, granted_at, created_at)
SELECT l.user_id, l.plan_id, l.id, NULL, l.created_at
  FROM payment_ledger l
 WHERE l.subscription_id IS NULL
   AND NOT EXISTS (SELECT 1 FROM ai_subscription s WHERE s.payment_id = l.id);

-- ---------------------------------------------------------------------------
-- 3. Pre-flight gate: every acquisition must have found its payment
-- ---------------------------------------------------------------------------

DO $$
DECLARE unmatched BIGINT;
BEGIN
  SELECT count(*) INTO unmatched FROM ai_subscription WHERE payment_id IS NULL;
  IF unmatched > 0 THEN
    RAISE EXCEPTION
      'Aborting V48: % ai_subscription row(s) have no payment_ledger row. '
      'Continuing would turn them into free claims. Resolve them first.', unmatched;
  END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 4. ai_subscription: drop the lifecycle
-- ---------------------------------------------------------------------------

DROP INDEX idx_ai_subscription_status_renewal_date;

ALTER TABLE ai_subscription
    DROP COLUMN status,
    DROP COLUMN renewal_date,
    DROP COLUMN extended_from_id,
    DROP COLUMN cancelled_at,
    DROP COLUMN updated_at;

-- "Claim once per plan"; purchases may repeat and stack.
CREATE UNIQUE INDEX uq_ai_subscription_one_claim
    ON ai_subscription (user_id, plan_id)
    WHERE payment_id IS NULL;

-- One payment produces at most one acquisition.
CREATE UNIQUE INDEX uq_ai_subscription_payment
    ON ai_subscription (payment_id)
    WHERE payment_id IS NOT NULL;

-- Allowance SUM, history page, and per-user lookups.
CREATE INDEX idx_ai_subscription_user_id
    ON ai_subscription (user_id);
CREATE INDEX idx_ai_subscription_user_created_at
    ON ai_subscription (user_id, created_at DESC);

-- "Is there an unfinished checkout for this plan?" — replaces
-- idx_payment_ledger_user_plan_pending.
CREATE INDEX idx_ai_subscription_pending
    ON ai_subscription (user_id, plan_id, created_at DESC)
    WHERE granted_at IS NULL AND payment_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 5. payment_ledger: money only
-- ---------------------------------------------------------------------------

DROP INDEX idx_payment_ledger_user_plan_pending;

-- subscription_id (V28) and plan_id (V25) both go; plan_id's FK to ai_plan
-- goes with the column, so payment_ledger no longer references the catalogue.
ALTER TABLE payment_ledger DROP COLUMN subscription_id;
ALTER TABLE payment_ledger DROP COLUMN plan_id;

-- idx_payment_ledger_user_paid_at (V25) and idx_payment_ledger_user_created_at
-- (V28) survive: neither references a dropped column.

-- ---------------------------------------------------------------------------
-- 6. ai_usage: one lifetime counter per member
-- ---------------------------------------------------------------------------

-- Collapse multi-month rows before the unique constraint is added. Rows whose
-- user_id is NULL are left alone: Postgres treats NULLs as distinct, so they
-- cannot violate UNIQUE (user_id), and there is no owner to sum them for.
--
-- Step 1 copies each member's total onto every one of their rows, so whichever
-- row survives step 2 already carries the total. Step 2 keeps one row per
-- member using the physical row id, because `MIN(uuid)` does not exist in
-- Postgres: the survivor cannot be picked by an aggregate over the primary key.
UPDATE ai_usage u
   SET request_count = totals.total
  FROM (SELECT user_id, SUM(request_count) AS total
          FROM ai_usage
         WHERE user_id IS NOT NULL
         GROUP BY user_id) totals
 WHERE u.user_id = totals.user_id;

DELETE FROM ai_usage a
 USING ai_usage b
 WHERE a.user_id IS NOT NULL
   AND a.user_id = b.user_id
   AND a.ctid > b.ctid;

ALTER TABLE ai_usage DROP CONSTRAINT ai_usage_user_id_period_start_period_end_key;
ALTER TABLE ai_usage DROP COLUMN period_start;
ALTER TABLE ai_usage DROP COLUMN period_end;
ALTER TABLE ai_usage ADD CONSTRAINT uq_ai_usage_user UNIQUE (user_id);
