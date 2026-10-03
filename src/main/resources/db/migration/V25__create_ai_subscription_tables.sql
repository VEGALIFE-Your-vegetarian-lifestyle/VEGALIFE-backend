-- V25__create_ai_subscription_tables.sql
-- AI subscription domain (issue #15): plans, member subscriptions, and the
-- read-only payment ledger. Seeded plan rows are the source of truth for
-- limits and prices (BR-SUBS-002) — changing a plan is a data change, not a
-- code change.

CREATE TABLE ai_plan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    monthly_request_limit INT NOT NULL,
    price_amount BIGINT NOT NULL,
    price_currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE ai_subscription (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES "user"(id) ON DELETE CASCADE,
    plan_id UUID NOT NULL REFERENCES ai_plan(id),
    status VARCHAR(20) NOT NULL DEFAULT 'active'
        CHECK (status IN ('active','cancelled','past_due')),
    renewal_date TIMESTAMPTZ,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE payment_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    plan_id UUID NOT NULL REFERENCES ai_plan(id),
    amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('pending','succeeded','failed','refunded')),
    provider VARCHAR(30),
    provider_reference TEXT,
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- A succeeded payment always has a paid_at: the latest-payment lookup
    -- ranks by paid_at, so a succeeded NULL would rank unpredictably first.
    CONSTRAINT chk_payment_ledger_succeeded_paid_at
        CHECK (status <> 'succeeded' OR paid_at IS NOT NULL)
);

CREATE INDEX idx_ai_plan_active_sort ON ai_plan(sort_order) WHERE active;
CREATE INDEX idx_payment_ledger_user_paid_at
    ON payment_ledger(user_id, paid_at DESC)
    WHERE status = 'succeeded';

-- Seed plans: FREE is the BR-SUBS-003 default; PRO's price is a placeholder
-- pending product confirmation (see docs/feats/subscription-api.md Risks).
INSERT INTO ai_plan (code, name, monthly_request_limit, price_amount, price_currency, active, sort_order)
VALUES
    ('FREE', 'Free', 20, 0, 'VND', TRUE, 1),
    ('PRO', 'Pro', 500, 49000, 'VND', TRUE, 2);
