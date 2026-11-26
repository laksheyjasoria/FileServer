-- Align instagram_automation_settings with the current JPA entity.
-- V1.0.9 is already applied, so this change is introduced as V1.0.10.

ALTER TABLE instagram_automation_settings
    ADD COLUMN IF NOT EXISTS cooldown_seconds INTEGER NOT NULL DEFAULT 0;