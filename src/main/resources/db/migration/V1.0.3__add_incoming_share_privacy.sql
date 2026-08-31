-- 1. Add the toggle column to users
ALTER TABLE users ADD COLUMN IF NOT EXISTS incoming_share_list_type VARCHAR(20) DEFAULT 'EXCLUDE';

-- 2. Create the list table (who is allowed/excluded)
CREATE TABLE IF NOT EXISTS incoming_share_allowed_users (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    allowed_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, allowed_user_id)
);

-- 3. Add indexes for performance
CREATE INDEX idx_incoming_share_allowed_user_id ON incoming_share_allowed_users(user_id);
CREATE INDEX idx_incoming_share_allowed_allowed_id ON incoming_share_allowed_users(allowed_user_id);