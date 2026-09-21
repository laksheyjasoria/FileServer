-- Production V2 billing, entitlements and native transfer authorization.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE plans
    ADD COLUMN IF NOT EXISTS product_scope VARCHAR(32),
    ADD COLUMN IF NOT EXISTS billing_interval VARCHAR(16),
    ADD COLUMN IF NOT EXISTS currency VARCHAR(8),
    ADD COLUMN IF NOT EXISTS external_plan_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS trial_days INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS description VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS sort_order INTEGER DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_plans_product_scope_active
    ON plans(product_scope, active);

CREATE UNIQUE INDEX IF NOT EXISTS uk_plans_external_plan_id
    ON plans(external_plan_id)
    WHERE external_plan_id IS NOT NULL;

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS external_subscription_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS provider VARCHAR(32),
    ADD COLUMN IF NOT EXISTS status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS uk_subscriptions_external_id
    ON subscriptions(external_subscription_id)
    WHERE external_subscription_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_subscriptions_user_status_expiry
    ON subscriptions(user_id, status, expiry_date);

CREATE TABLE IF NOT EXISTS plan_capabilities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id VARCHAR(255) NOT NULL,
    capability_key VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    numeric_value BIGINT,
    text_value VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_plan_capability UNIQUE(plan_id, capability_key)
);

CREATE INDEX IF NOT EXISTS idx_plan_capabilities_plan
    ON plan_capabilities(plan_id);

CREATE TABLE IF NOT EXISTS download_authorization (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    file_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255),
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_download_auth_file_user
    ON download_authorization(file_id, user_id, expires_at);
