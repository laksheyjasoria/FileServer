-- ============================================================
-- Chunked file transfer - Phase 1
-- ============================================================

-- ------------------------------------------------------------
-- MasterFile <-> UploadJob association
-- ------------------------------------------------------------

ALTER TABLE master_files
    ADD COLUMN upload_job_id VARCHAR(255);

CREATE INDEX idx_master_files_upload_job_id
    ON master_files(upload_job_id);


-- ------------------------------------------------------------
-- UploadJob authoritative transfer metadata
-- ------------------------------------------------------------

ALTER TABLE upload_jobs
    ADD COLUMN chunk_size INTEGER;

ALTER TABLE upload_jobs
    ADD COLUMN uploaded_bytes BIGINT DEFAULT 0;

ALTER TABLE upload_jobs
    ADD COLUMN updated_at TIMESTAMP;


-- Existing records are not active chunked transfers.
UPDATE upload_jobs
SET uploaded_bytes = 0
WHERE uploaded_bytes IS NULL;

UPDATE upload_jobs
SET updated_at = created_at
WHERE updated_at IS NULL;


-- ------------------------------------------------------------
-- UploadChunk state and timestamps
-- ------------------------------------------------------------

ALTER TABLE upload_chunks
    ADD COLUMN status VARCHAR(20) DEFAULT 'COMPLETED';

ALTER TABLE upload_chunks
    ADD COLUMN created_at TIMESTAMP;

ALTER TABLE upload_chunks
    ADD COLUMN updated_at TIMESTAMP;


UPDATE upload_chunks
SET status = 'COMPLETED'
WHERE status IS NULL;


-- ------------------------------------------------------------
-- Prevent duplicate logical chunks
-- ------------------------------------------------------------

ALTER TABLE upload_chunks
    ADD CONSTRAINT uk_upload_chunks_job_index
    UNIQUE (upload_job_id, chunk_index);