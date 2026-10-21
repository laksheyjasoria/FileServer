-- ============================================================
-- V1.0.13__fix_instagram_usage_column_types.sql
-- Align instagram_usage counter columns with JPA entity (long -> bigint)
-- ============================================================

ALTER TABLE instagram_usage
    ALTER COLUMN comments_received TYPE BIGINT,
    ALTER COLUMN comments_processed TYPE BIGINT,
    ALTER COLUMN comments_ignored TYPE BIGINT,
    ALTER COLUMN replies_sent TYPE BIGINT,
    ALTER COLUMN likes_sent TYPE BIGINT,
    ALTER COLUMN gemini_requests TYPE BIGINT,
    ALTER COLUMN gemini_failures TYPE BIGINT,
    ALTER COLUMN meta_api_failures TYPE BIGINT;