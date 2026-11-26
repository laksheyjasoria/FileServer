-- ============================================================
-- Phase 2 - Chunked upload concurrency hardening
-- ============================================================

-- Prevent the same logical chunk from being stored twice.
--
-- Logical identity:
--     upload_job_id + chunk_index
--
-- This is the database-level protection against concurrent
-- duplicate chunk requests.
CREATE UNIQUE INDEX IF NOT EXISTS
    uk_upload_chunks_job_chunk_index
ON upload_chunks (upload_job_id, chunk_index);


-- Make sure existing upload jobs have valid progress values.
UPDATE upload_jobs
SET uploaded_chunks = 0
WHERE uploaded_chunks IS NULL;

UPDATE upload_jobs
SET uploaded_bytes = 0
WHERE uploaded_bytes IS NULL;


-- Make progress columns non-null.
ALTER TABLE upload_jobs
ALTER COLUMN uploaded_chunks SET DEFAULT 0;

ALTER TABLE upload_jobs
ALTER COLUMN uploaded_chunks SET NOT NULL;

ALTER TABLE upload_jobs
ALTER COLUMN uploaded_bytes SET DEFAULT 0;

ALTER TABLE upload_jobs
ALTER COLUMN uploaded_bytes SET NOT NULL;