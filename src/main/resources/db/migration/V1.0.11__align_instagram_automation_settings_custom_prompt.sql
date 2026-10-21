-- Align instagram_automation_settings with the current JPA entity.
-- V1.0.10 is already applied, so this change is introduced as V1.0.11.

ALTER TABLE instagram_automation_settings
    ADD COLUMN IF NOT EXISTS custom_prompt TEXT;