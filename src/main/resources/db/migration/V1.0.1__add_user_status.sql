-- Add status column to users table
ALTER TABLE users ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN deactivated_at TIMESTAMP;
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP;

-- Update existing users to ACTIVE
UPDATE users SET status = 'ACTIVE' WHERE status IS NULL;

-- Add index for status
CREATE INDEX idx_users_status ON users(status);

-- Optional: drop old is_active if you were using it, but keep for backward compatibility
-- ALTER TABLE users DROP COLUMN is_active;