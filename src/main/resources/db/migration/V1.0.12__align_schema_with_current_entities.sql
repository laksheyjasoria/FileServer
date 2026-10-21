-- ============================================================
-- V1.0.12__align_schema_with_current_entities.sql
-- Align database schema with current JPA entities.
-- Adds missing tables, columns, constraints, and indexes.
-- Does NOT remove extra columns.
-- ============================================================

-- ============================================================
-- 1. Missing tables for subscription entities
-- ============================================================

CREATE TABLE IF NOT EXISTS subscription_plans (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    external_plan_id VARCHAR(255),
    description TEXT,
    price NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    billing_period VARCHAR(255) NOT NULL DEFAULT 'MONTH',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    max_accounts INTEGER NOT NULL DEFAULT 1,
    max_replies_hour INTEGER NOT NULL DEFAULT 30,
    max_replies_day INTEGER NOT NULL DEFAULT 200,
    max_gemini_day INTEGER NOT NULL DEFAULT 200,
    max_rules INTEGER NOT NULL DEFAULT 20,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_plan_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS user_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'TRIAL',
    started_at TIMESTAMP,
    expires_at TIMESTAMP,
    auto_renew BOOLEAN NOT NULL DEFAULT FALSE,
    external_subscription_id VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sub_user ON user_subscriptions(user_id);

CREATE TABLE IF NOT EXISTS instagram_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    plan_code VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    payment_provider VARCHAR(50),
    provider_payment_id VARCHAR(200),
    started_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_instagram_subscription_user ON instagram_subscriptions(user_id);
CREATE INDEX IF NOT EXISTS idx_instagram_subscription_status ON instagram_subscriptions(status);

-- ============================================================
-- 2. instagram_automation_settings: add missing entity columns
-- ============================================================

ALTER TABLE instagram_automation_settings
    ADD COLUMN IF NOT EXISTS max_replies_hour INTEGER,
    ADD COLUMN IF NOT EXISTS max_replies_day INTEGER,
    ADD COLUMN IF NOT EXISTS max_gemini_day INTEGER,
    ADD COLUMN IF NOT EXISTS max_reply_length INTEGER DEFAULT 180,
    ADD COLUMN IF NOT EXISTS emoji_enabled BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS reply_style VARCHAR(255) DEFAULT 'natural, warm, concise',
    ADD COLUMN IF NOT EXISTS language VARCHAR(255) DEFAULT 'auto';

-- Copy data from old columns to new columns
UPDATE instagram_automation_settings
SET max_replies_hour = max_replies_per_hour
WHERE max_replies_hour IS NULL AND max_replies_per_hour IS NOT NULL;

UPDATE instagram_automation_settings
SET max_replies_day = max_replies_per_day
WHERE max_replies_day IS NULL AND max_replies_per_day IS NOT NULL;

UPDATE instagram_automation_settings
SET max_gemini_day = max_gemini_requests_per_day
WHERE max_gemini_day IS NULL AND max_gemini_requests_per_day IS NOT NULL;

-- Set defaults for any remaining nulls
UPDATE instagram_automation_settings SET max_replies_hour = 30 WHERE max_replies_hour IS NULL;
UPDATE instagram_automation_settings SET max_replies_day = 200 WHERE max_replies_day IS NULL;
UPDATE instagram_automation_settings SET max_gemini_day = 200 WHERE max_gemini_day IS NULL;
UPDATE instagram_automation_settings SET max_reply_length = 180 WHERE max_reply_length IS NULL;
UPDATE instagram_automation_settings SET emoji_enabled = TRUE WHERE emoji_enabled IS NULL;
UPDATE instagram_automation_settings SET reply_style = 'natural, warm, concise' WHERE reply_style IS NULL;
UPDATE instagram_automation_settings SET language = 'auto' WHERE language IS NULL;

-- Make new columns NOT NULL where entity expects
ALTER TABLE instagram_automation_settings
    ALTER COLUMN max_replies_hour SET NOT NULL,
    ALTER COLUMN max_replies_day SET NOT NULL,
    ALTER COLUMN max_gemini_day SET NOT NULL,
    ALTER COLUMN max_reply_length SET NOT NULL,
    ALTER COLUMN emoji_enabled SET NOT NULL;

-- ============================================================
-- 3. instagram_comments: add missing entity columns
-- ============================================================

ALTER TABLE instagram_comments
    ADD COLUMN IF NOT EXISTS commenter_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS commenter_username VARCHAR(255),
    ADD COLUMN IF NOT EXISTS post_context TEXT,
    ADD COLUMN IF NOT EXISTS proposed_reply TEXT,
    ADD COLUMN IF NOT EXISTS sent_reply TEXT,
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'RECEIVED',
    ADD COLUMN IF NOT EXISTS error_message TEXT,
    ADD COLUMN IF NOT EXISTS processed_at TIMESTAMP;

-- Copy data from old columns
UPDATE instagram_comments
SET commenter_id = instagram_user_id
WHERE commenter_id IS NULL AND instagram_user_id IS NOT NULL;

UPDATE instagram_comments
SET commenter_username = instagram_username
WHERE commenter_username IS NULL AND instagram_username IS NOT NULL;

-- Set default status for existing rows
UPDATE instagram_comments SET status = 'RECEIVED' WHERE status IS NULL;

-- Set processed_at for already processed comments
UPDATE instagram_comments
SET processed_at = received_at
WHERE processed_at IS NULL AND processed = TRUE;

-- Make status NOT NULL
ALTER TABLE instagram_comments
    ALTER COLUMN status SET NOT NULL;

-- ============================================================
-- 4. instagram_usage: add unique constraint expected by entity
-- ============================================================

-- Entity expects unique constraint on (user_id, usage_date).
-- Note: existing data may have multiple rows per user per day
-- (one per hour_bucket). This constraint will fail if such duplicates exist.
-- If it fails, review data before applying.
ALTER TABLE instagram_usage
    ADD CONSTRAINT uk_ig_usage_user_date UNIQUE (user_id, usage_date);

-- ============================================================
-- 5. Other entity/table alignment already satisfied
-- ============================================================
-- InstagramAccount unique constraint already exists as uk_instagram_account_instagram_user.
-- InstagramAutomationSettings unique constraint name differs, but Hibernate does not validate names.
-- InstagramMediaSettings unique constraint already exists.
-- InstagramComment unique constraint already exists.
-- UploadChunk unique constraint already exists.
-- Friends unique constraint already exists.
-- Users unique email already exists.

-- ============================================================
-- End of migration
-- ============================================================