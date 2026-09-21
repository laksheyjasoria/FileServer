package com.app.upload.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.app.core.exception.UploadNotFoundException;
import com.app.storage.factory.StorageFactory;
import com.app.upload.entity.UploadChunk;
import com.app.upload.repository.UploadChunkRepository;

/**
 * Coordinates browser chunk validation with Telegram storage.
 *
 * Database state is committed before and after Telegram I/O so a slow Telegram
 * request does not hold an application transaction open for the entire transfer.
 */
@Service
public class ChunkService {

    private final ChunkUploadStateService stateService;
    private final UploadChunkRepository chunkRepository;
    private final StorageFactory storageFactory;

    /*
     * Protects duplicate requests inside a single application instance.
     * The database unique constraint remains the cross-instance authority.
     */
    private final ConcurrentMap<String, Object> localChunkLocks = new ConcurrentHashMap<>();

    public ChunkService(
            ChunkUploadStateService stateService,
            UploadChunkRepository chunkRepository,
            StorageFactory storageFactory) {
        this.stateService = stateService;
        this.chunkRepository = chunkRepository;
        this.storageFactory = storageFactory;
    }

    public void uploadChunk(
            String uploadId,
            Integer chunkIndex,
            MultipartFile file,
            String userId) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Chunk file cannot be empty.");
        }

        if (chunkIndex == null || chunkIndex < 0) {
            throw new IllegalArgumentException("Chunk index must be zero or greater.");
        }

        String lockKey = uploadId + ":" + chunkIndex;
        Object lock = localChunkLocks.computeIfAbsent(lockKey, ignored -> new Object());

        try {
            synchronized (lock) {
                uploadChunkInternal(uploadId, chunkIndex, file, userId);
            }
        } finally {
            localChunkLocks.remove(lockKey, lock);
        }
    }

    private void uploadChunkInternal(
            String uploadId,
            int chunkIndex,
            MultipartFile file,
            String userId) {

        boolean claimed;

        try {
            claimed = stateService.claimChunk(
                    uploadId,
                    chunkIndex,
                    file.getSize(),
                    userId);
        } catch (DataIntegrityViolationException ex) {
            /*
             * Another instance inserted the same logical chunk first.
             */
            UploadChunk existing = chunkRepository
                    .findByUploadJobIdAndChunkIndex(uploadId, chunkIndex)
                    .orElseThrow(UploadNotFoundException::new);

            if (existing.getStatus() == com.app.upload.entity.UploadChunkStatus.COMPLETED) {
                return;
            }

            throw new IllegalStateException(
                    "Chunk " + chunkIndex + " is already being uploaded.");
        }

        if (!claimed) {
            return;
        }

        String telegramFileId = null;

        try {
            telegramFileId = storageFactory.get().upload(file);

            stateService.completeChunk(
                    uploadId,
                    chunkIndex,
                    file.getSize(),
                    telegramFileId,
                    userId);

        } catch (RuntimeException ex) {
            try {
                stateService.markFailed(uploadId, chunkIndex, userId);
            } catch (RuntimeException stateError) {
                ex.addSuppressed(stateError);
            }

            throw ex;
        }
    }
}
