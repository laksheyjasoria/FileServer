package com.app.upload.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import com.app.core.exception.UploadNotFoundException;
import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;
import com.app.upload.entity.UploadChunk;
import com.app.upload.entity.UploadChunkStatus;
import com.app.upload.entity.UploadJob;
import com.app.upload.entity.UploadStatus;
import com.app.upload.repository.UploadChunkRepository;
import com.app.upload.repository.UploadJobRepository;

/**
 * Owns the short database transactions used to claim and finalize individual
 * chunks. Telegram I/O deliberately does not happen inside these transactions.
 */
@Service
public class ChunkUploadStateService {

    private final UploadJobRepository jobRepository;
    private final UploadChunkRepository chunkRepository;
    private final MasterFileRepository masterFileRepository;
    private final long staleUploadingMinutes;

    public ChunkUploadStateService(
            UploadJobRepository jobRepository,
            UploadChunkRepository chunkRepository,
            MasterFileRepository masterFileRepository,
            @Value("${app.upload.chunk.stale-timeout-minutes:30}") long staleUploadingMinutes) {
        this.jobRepository = jobRepository;
        this.chunkRepository = chunkRepository;
        this.masterFileRepository = masterFileRepository;

        if (staleUploadingMinutes <= 0) {
            throw new IllegalArgumentException("Stale uploading timeout must be greater than zero.");
        }

        this.staleUploadingMinutes = staleUploadingMinutes;
    }

    /**
     * Claims a logical chunk for Telegram upload.
     *
     * @return false when the chunk was already completed.
     */
    @Transactional
    public boolean claimChunk(
            String uploadId,
            int chunkIndex,
            long actualSize,
            String userId) {

        UploadJob job = getJob(uploadId, userId);
        validateUploadState(job);
        validateChunkIndex(job, chunkIndex);

        long expectedSize = expectedChunkSize(job, chunkIndex);
        if (actualSize != expectedSize) {
            throw new IllegalArgumentException(
                    "Invalid chunk size. Expected " + expectedSize
                            + " bytes but received " + actualSize + " bytes.");
        }

        UploadChunk existing = chunkRepository
                .findByUploadJobIdAndChunkIndex(uploadId, chunkIndex)
                .orElse(null);

        if (existing != null) {
            if (existing.getStatus() == UploadChunkStatus.COMPLETED) {
                return false;
            }

            if (existing.getStatus() == UploadChunkStatus.UPLOADING) {
                LocalDateTime updatedAt = existing.getUpdatedAt();

                if (updatedAt == null
                        || updatedAt.isAfter(LocalDateTime.now().minusMinutes(staleUploadingMinutes))) {
                    throw new IllegalStateException(
                            "Chunk " + chunkIndex + " is already being uploaded.");
                }

                /*
                 * A previous process died after claiming this chunk. Reclaim it.
                 */
                existing.setStatus(UploadChunkStatus.UPLOADING);
                existing.setTelegramFileId(null);
            }

            existing.setStatus(UploadChunkStatus.UPLOADING);
            existing.setTelegramFileId(null);
            existing.setSize(actualSize);
            chunkRepository.save(existing);
            return true;
        }

        UploadChunk chunk = new UploadChunk();
        chunk.setUploadJobId(uploadId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setSize(actualSize);
        chunk.setStatus(UploadChunkStatus.UPLOADING);

        try {
            chunkRepository.saveAndFlush(chunk);
            return true;
        } catch (DataIntegrityViolationException ex) {
            /*
             * Another application instance won the unique (job,index) race.
             * The caller re-reads the row and reports its actual state.
             */
            throw ex;
        }
    }

    @Transactional
    public void markFailed(String uploadId, int chunkIndex, String userId) {

        UploadJob job = getJob(uploadId, userId);

        UploadChunk chunk = chunkRepository
                .findByUploadJobIdAndChunkIndex(uploadId, chunkIndex)
                .orElse(null);

        if (chunk == null || chunk.getStatus() == UploadChunkStatus.COMPLETED) {
            return;
        }

        chunk.setStatus(UploadChunkStatus.FAILED);
        chunkRepository.save(chunk);

        if (job.getStatus() == UploadStatus.IN_PROGRESS) {
            jobRepository.save(job);
        }
    }

    @Transactional
    public void completeChunk(
            String uploadId,
            int chunkIndex,
            long size,
            String telegramFileId,
            String userId) {

        UploadJob job = getJob(uploadId, userId);

        UploadChunk chunk = chunkRepository
                .findByUploadJobIdAndChunkIndex(uploadId, chunkIndex)
                .orElseThrow(UploadNotFoundException::new);

        if (chunk.getStatus() == UploadChunkStatus.COMPLETED) {
            return;
        }

        /*
         * If cancellation won the race while Telegram was receiving the chunk,
         * do not resurrect the upload. The Telegram object may remain orphaned
         * because the Bot API does not provide a delete-by-file-id operation.
         */
        if (job.getStatus() == UploadStatus.CANCELLED) {
            chunk.setStatus(UploadChunkStatus.FAILED);
            chunkRepository.save(chunk);
            throw new IllegalStateException("Upload has been cancelled.");
        }

        chunk.setTelegramFileId(telegramFileId);
        chunk.setSize(size);
        chunk.setStatus(UploadChunkStatus.COMPLETED);
        chunkRepository.save(chunk);

        recalculateProgress(job);

        if (job.getUploadedChunks() == job.getTotalChunks()) {
            if (!Long.valueOf(job.getUploadedBytes()).equals(job.getTotalSize())) {
                job.setStatus(UploadStatus.FAILED);
                jobRepository.save(job);
                throw new IllegalStateException("Uploaded bytes do not match total file size.");
            }

            job.setStatus(UploadStatus.COMPLETED);

            MasterFile masterFile = masterFileRepository
                    .findByUploadJobId(job.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "MasterFile not found for upload: " + job.getId()));

            masterFile.setActive(true);
            masterFileRepository.save(masterFile);
        }

        jobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public List<Integer> completedChunkIndexes(String uploadId, String userId) {
        getJob(uploadId, userId);

        return chunkRepository.findByUploadJobIdOrderByChunkIndexAsc(uploadId).stream()
                .filter(chunk -> chunk.getStatus() == UploadChunkStatus.COMPLETED)
                .map(UploadChunk::getChunkIndex)
                .toList();
    }

    private void recalculateProgress(UploadJob job) {
        List<UploadChunk> chunks =
                chunkRepository.findByUploadJobIdOrderByChunkIndexAsc(job.getId());

        long uploadedBytes = chunks.stream()
                .filter(chunk -> chunk.getStatus() == UploadChunkStatus.COMPLETED)
                .map(UploadChunk::getSize)
                .filter(size -> size != null)
                .mapToLong(Long::longValue)
                .sum();

        int uploadedChunks = (int) chunks.stream()
                .filter(chunk -> chunk.getStatus() == UploadChunkStatus.COMPLETED)
                .count();

        job.setUploadedBytes(uploadedBytes);
        job.setUploadedChunks(uploadedChunks);
    }

    private UploadJob getJob(String uploadId, String userId) {
        return jobRepository.findByIdAndUserId(uploadId, userId)
                .orElseThrow(UploadNotFoundException::new);
    }

    private void validateUploadState(UploadJob job) {
        if (job.getStatus() == UploadStatus.CANCELLED) {
            throw new IllegalStateException("Upload has been cancelled.");
        }
        if (job.getStatus() == UploadStatus.COMPLETED) {
            throw new IllegalStateException("Upload has already completed.");
        }
        if (job.getStatus() == UploadStatus.PAUSED) {
            throw new IllegalStateException("Upload is paused.");
        }
        if (job.getStatus() == UploadStatus.FAILED) {
            throw new IllegalStateException("Upload has failed. Resume or retry the upload.");
        }
    }

    private void validateChunkIndex(UploadJob job, int chunkIndex) {
        if (chunkIndex >= job.getTotalChunks()) {
            throw new IllegalArgumentException(
                    "Chunk index " + chunkIndex + " is outside the valid range 0.."
                            + (job.getTotalChunks() - 1));
        }
    }

    private long expectedChunkSize(UploadJob job, int chunkIndex) {
        long chunkSize = job.getChunkSize();
        long start = (long) chunkIndex * chunkSize;
        long remaining = job.getTotalSize() - start;
        return Math.min(chunkSize, remaining);
    }
}
