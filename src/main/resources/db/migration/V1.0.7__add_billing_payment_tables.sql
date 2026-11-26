-- ============================================================
-- V1.0.7 - Billing / Payment persistence
-- ============================================================
-- Purpose:
--   Add provider-independent payment persistence.
--
-- Existing tables:
--   plans
--   subscriptions
--
-- This migration intentionally does NOT modify those tables.
-- Payment providers such as Razorpay can be replaced later.
-- ============================================================

CREATE TABLE payment_customers (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL,

    provider VARCHAR(50) NOT NULL,
    provider_customer_id VARCHAR(255),

    email VARCHAR(320),
    name VARCHAR(255),

    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payment_customer_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_payment_customer_user_provider
        UNIQUE (user_id, provider)
);

CREATE INDEX idx_payment_customers_user_id
    ON payment_customers(user_id);

CREATE INDEX idx_payment_customers_provider_customer_id
    ON payment_customers(provider, provider_customer_id);


-- ============================================================
-- Payment transactions
-- ============================================================

CREATE TABLE payment_transactions (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL,

    provider VARCHAR(50) NOT NULL,

    provider_order_id VARCHAR(255),
    provider_payment_id VARCHAR(255),

    payment_type VARCHAR(50) NOT NULL DEFAULT 'ONE_TIME',

    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',

    status VARCHAR(30) NOT NULL DEFAULT 'CREATED',

    description VARCHAR(500),

    metadata JSONB,

    paid_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payment_transaction_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_payment_transaction_amount
        CHECK (amount >= 0)
);

CREATE INDEX idx_payment_transactions_user_id
    ON payment_transactions(user_id);

CREATE INDEX idx_payment_transactions_provider
    ON payment_transactions(provider);

CREATE INDEX idx_payment_transactions_provider_order
    ON payment_transactions(provider, provider_order_id);

CREATE INDEX idx_payment_transactions_provider_payment
    ON payment_transactions(provider, provider_payment_id);

CREATE INDEX idx_payment_transactions_status
    ON payment_transactions(status);

CREATE INDEX idx_payment_transactions_created_at
    ON payment_transactions(created_at);


-- ============================================================
-- Refunds
-- ============================================================

CREATE TABLE payment_refunds (
    id BIGSERIAL PRIMARY KEY,

    payment_transaction_id BIGINT NOT NULL,

    provider VARCHAR(50) NOT NULL,
    provider_refund_id VARCHAR(255),

    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',

    status VARCHAR(30) NOT NULL DEFAULT 'CREATED',

    reason VARCHAR(500),

    metadata JSONB,

    refunded_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payment_refund_transaction
        FOREIGN KEY (payment_transaction_id)
        REFERENCES payment_transactions(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_payment_refund_amount
        CHECK (amount >= 0)
);

CREATE INDEX idx_payment_refunds_transaction
    ON payment_refunds(payment_transaction_id);

CREATE INDEX idx_payment_refunds_provider_refund
    ON payment_refunds(provider, provider_refund_id);


-- ============================================================
-- Payment webhook events
-- ============================================================
-- Used for idempotency.
-- provider_event_id must be unique per provider.
-- ============================================================

CREATE TABLE payment_webhook_events (
    id BIGSERIAL PRIMARY KEY,

    provider VARCHAR(50) NOT NULL,
    provider_event_id VARCHAR(255) NOT NULL,

    event_type VARCHAR(255),

    payload JSONB,

    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',

    error_message TEXT,

    processed_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_payment_webhook_provider_event
        UNIQUE (provider, provider_event_id)
);

CREATE INDEX idx_payment_webhook_events_provider
    ON payment_webhook_events(provider);

CREATE INDEX idx_payment_webhook_events_status
    ON payment_webhook_events(status);

CREATE INDEX idx_payment_webhook_events_created_at
    ON payment_webhook_events(created_at);