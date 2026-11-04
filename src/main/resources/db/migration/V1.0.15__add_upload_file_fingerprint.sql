-- Persist a client-side file fingerprint so a resumable upload cannot
-- accidentally be continued with a different local file of the same name/size.
ALTER TABLE upload_jobs
    ADD COLUMN IF NOT EXISTS file_fingerprint VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_upload_jobs_user_fingerprint
    ON upload_jobs (user_id, file_fingerprint);
