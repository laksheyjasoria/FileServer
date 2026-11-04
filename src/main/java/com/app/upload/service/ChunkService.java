package com.app.upload.service;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
            String userId,
            String expectedSha256) {

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
                verifyChecksum(file, expectedSha256);
                uploadChunkInternal(uploadId, chunkIndex, file, userId);
            }
        } finally {
            localChunkLocks.remove(lockKey, lock);
        }
    }

    private void verifyChecksum(MultipartFile file, String expectedSha256) {
        if (expectedSha256 == null || !expectedSha256.matches("(?i)^[a-f0-9]{64}$")) {
            throw new IllegalArgumentException("A valid SHA-256 chunk checksum is required.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = file.getInputStream()) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!actual.equalsIgnoreCase(expectedSha256)) {
                throw new IllegalArgumentException("Chunk checksum mismatch. Please retry this chunk.");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to verify uploaded chunk checksum.", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable in this runtime.", ex);
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
