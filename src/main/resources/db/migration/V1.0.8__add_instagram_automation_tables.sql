-- ============================================================
-- V1.0.8 - Instagram automation
-- ============================================================

-- ============================================================
-- Instagram connected accounts
-- ============================================================

CREATE TABLE instagram_accounts (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL,

    instagram_user_id VARCHAR(255) NOT NULL,
    instagram_username VARCHAR(255),

    facebook_page_id VARCHAR(255),

    access_token_encrypted TEXT,
    token_expires_at TIMESTAMP,

    token_status VARCHAR(30) NOT NULL DEFAULT 'CONNECTED',

    account_type VARCHAR(50),

    connected BOOLEAN NOT NULL DEFAULT TRUE,

    last_verified_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_instagram_account_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_instagram_account_instagram_user
        UNIQUE (instagram_user_id)
);

CREATE INDEX idx_instagram_accounts_user_id
    ON instagram_accounts(user_id);

CREATE INDEX idx_instagram_accounts_username
    ON instagram_accounts(instagram_username);

CREATE INDEX idx_instagram_accounts_connected
    ON instagram_accounts(user_id, connected);


-- ============================================================
-- Instagram automation settings
-- ============================================================

CREATE TABLE instagram_automation_settings (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL,

    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    dry_run BOOLEAN NOT NULL DEFAULT TRUE,

    auto_reply_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    auto_like_enabled BOOLEAN NOT NULL DEFAULT FALSE,

    approval_mode BOOLEAN NOT NULL DEFAULT FALSE,

    reply_mode VARCHAR(30) NOT NULL DEFAULT 'GEMINI',

    max_replies_per_hour INTEGER NOT NULL DEFAULT 20,
    max_replies_per_day INTEGER NOT NULL DEFAULT 100,

    max_likes_per_hour INTEGER NOT NULL DEFAULT 30,
    max_likes_per_day INTEGER NOT NULL DEFAULT 150,

    max_gemini_requests_per_hour INTEGER NOT NULL DEFAULT 20,
    max_gemini_requests_per_day INTEGER NOT NULL DEFAULT 100,

    gemini_model VARCHAR(100),

    system_prompt TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_instagram_settings_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_instagram_settings_user
        UNIQUE (user_id),

    CONSTRAINT chk_instagram_reply_hour
        CHECK (max_replies_per_hour >= 0),

    CONSTRAINT chk_instagram_reply_day
        CHECK (max_replies_per_day >= 0),

    CONSTRAINT chk_instagram_like_hour
        CHECK (max_likes_per_hour >= 0),

    CONSTRAINT chk_instagram_like_day
        CHECK (max_likes_per_day >= 0),

    CONSTRAINT chk_instagram_gemini_hour
        CHECK (max_gemini_requests_per_hour >= 0),

    CONSTRAINT chk_instagram_gemini_day
        CHECK (max_gemini_requests_per_day >= 0)
);

CREATE INDEX idx_instagram_settings_user_id
    ON instagram_automation_settings(user_id);


-- ============================================================
-- Instagram media / post contexts
-- ============================================================

CREATE TABLE instagram_media_settings (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL,

    instagram_media_id VARCHAR(255) NOT NULL,

    post_context TEXT,

    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    auto_reply_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    auto_like_enabled BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_instagram_media_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_instagram_media_user_media
        UNIQUE (user_id, instagram_media_id)
);

CREATE INDEX idx_instagram_media_user_id
    ON instagram_media_settings(user_id);

CREATE INDEX idx_instagram_media_instagram_id
    ON instagram_media_settings(instagram_media_id);


-- ============================================================
-- Comment rules
-- ============================================================

CREATE TABLE instagram_comment_rules (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL,

    rule_type VARCHAR(50) NOT NULL,
    match_type VARCHAR(50),

    pattern TEXT,

    action VARCHAR(50) NOT NULL,

    reply_text TEXT,

    priority INTEGER NOT NULL DEFAULT 100,

    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_instagram_rule_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_instagram_rules_user_id
    ON instagram_comment_rules(user_id);

CREATE INDEX idx_instagram_rules_enabled_priority
    ON instagram_comment_rules(user_id, enabled, priority);


-- ============================================================
-- Incoming Instagram comments
-- ============================================================

CREATE TABLE instagram_comments (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL,

    instagram_account_id BIGINT NOT NULL,

    instagram_comment_id VARCHAR(255) NOT NULL,

    instagram_media_id VARCHAR(255),

    instagram_user_id VARCHAR(255),
    instagram_username VARCHAR(255),

    comment_text TEXT,

    parent_comment_id VARCHAR(255),

    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    processed BOOLEAN NOT NULL DEFAULT FALSE,

    ignored BOOLEAN NOT NULL DEFAULT FALSE,

    ignored_reason VARCHAR(500),

    duplicate BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_instagram_comment_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_instagram_comment_account
        FOREIGN KEY (instagram_account_id)
        REFERENCES instagram_accounts(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_instagram_comment_id
        UNIQUE (instagram_comment_id)
);

CREATE INDEX idx_instagram_comments_user_id
    ON instagram_comments(user_id);

CREATE INDEX idx_instagram_comments_account
    ON instagram_comments(instagram_account_id);

CREATE INDEX idx_instagram_comments_media
    ON instagram_comments(instagram_media_id);

CREATE INDEX idx_instagram_comments_received
    ON instagram_comments(received_at);

CREATE INDEX idx_instagram_comments_processed
    ON instagram_comments(processed);


-- ============================================================
-- Automation logs
-- ============================================================

CREATE TABLE instagram_automation_logs (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL,

    instagram_account_id BIGINT,

    instagram_comment_id BIGINT,

    action VARCHAR(50) NOT NULL,

    status VARCHAR(30) NOT NULL,

    generated_reply TEXT,

    final_reply TEXT,

    error_message TEXT,

    provider VARCHAR(50),

    provider_request_id VARCHAR(255),

    dry_run BOOLEAN NOT NULL DEFAULT FALSE,

    retry_count INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,

    CONSTRAINT fk_instagram_log_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_instagram_log_account
        FOREIGN KEY (instagram_account_id)
        REFERENCES instagram_accounts(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_instagram_log_comment
        FOREIGN KEY (instagram_comment_id)
        REFERENCES instagram_comments(id)
        ON DELETE SET NULL
);

CREATE INDEX idx_instagram_logs_user
    ON instagram_automation_logs(user_id);

CREATE INDEX idx_instagram_logs_account
    ON instagram_automation_logs(instagram_account_id);

CREATE INDEX idx_instagram_logs_comment
    ON instagram_automation_logs(instagram_comment_id);

CREATE INDEX idx_instagram_logs_status
    ON instagram_automation_logs(status);

CREATE INDEX idx_instagram_logs_created
    ON instagram_automation_logs(created_at);


-- ============================================================
-- Usage / rate-limit tracking
-- ============================================================

CREATE TABLE instagram_usage (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL,

    usage_date DATE NOT NULL,

    hour_bucket TIMESTAMP NOT NULL,

    comments_received INTEGER NOT NULL DEFAULT 0,
    comments_processed INTEGER NOT NULL DEFAULT 0,
    comments_ignored INTEGER NOT NULL DEFAULT 0,

    replies_sent INTEGER NOT NULL DEFAULT 0,
    likes_sent INTEGER NOT NULL DEFAULT 0,

    gemini_requests INTEGER NOT NULL DEFAULT 0,
    gemini_failures INTEGER NOT NULL DEFAULT 0,

    meta_api_failures INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_instagram_usage_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_instagram_usage_user_hour
        UNIQUE (user_id, hour_bucket)
);

CREATE INDEX idx_instagram_usage_user_date
    ON instagram_usage(user_id, usage_date);

CREATE INDEX idx_instagram_usage_hour
    ON instagram_usage(hour_bucket);