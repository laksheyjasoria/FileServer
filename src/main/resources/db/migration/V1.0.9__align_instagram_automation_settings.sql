-- Align instagram_automation_settings with the current JPA entity.
-- V1.0.8 is already applied, so this change is introduced as V1.0.9.

ALTER TABLE instagram_automation_settings
    ADD COLUMN IF NOT EXISTS auto_like BOOLEAN NOT NULL DEFAULT FALSE;