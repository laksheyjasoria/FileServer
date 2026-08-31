-- =============================================
-- V1.0.4 – Phase 2: Privacy, Friends, Sharing
-- =============================================

-- 1. Add columns to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS auto_approve_friends BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS friend_request_privacy VARCHAR(20) DEFAULT 'EVERYONE';
ALTER TABLE users ADD COLUMN IF NOT EXISTS searchable_by VARCHAR(20) DEFAULT 'EVERYONE';
ALTER TABLE users ADD COLUMN IF NOT EXISTS incoming_share_privacy VARCHAR(20) DEFAULT 'EVERYONE';

-- 2. Friends table
CREATE TABLE IF NOT EXISTS friends (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    friend_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_friendship UNIQUE (user_id, friend_id)
);

CREATE INDEX IF NOT EXISTS idx_friends_user_id ON friends(user_id);
CREATE INDEX IF NOT EXISTS idx_friends_friend_id ON friends(friend_id);
CREATE INDEX IF NOT EXISTS idx_friends_status ON friends(status);

-- 3. Indexes for user search (for performance)
CREATE INDEX IF NOT EXISTS idx_users_email_lower ON users(LOWER(email));
CREATE INDEX IF NOT EXISTS idx_users_name_lower ON users(LOWER(name));