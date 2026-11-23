package com.app.drive.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.app.upload.service.ChunkService;
import com.app.upload.service.ChunkUploadStateService;
import com.app.upload.util.ByteArrayMultipartFile;

/**
 * Sends a staged Master Drive upload to the configured storage provider in
 * bounded chunks. The staged source is temporary and is always deleted after
 * the background task finishes.
 */
@Service
public class MasterDriveAsyncUploadService {

    private final ChunkService chunkService;
    private final ChunkUploadStateService chunkStateService;

    public MasterDriveAsyncUploadService(
            ChunkService chunkService,
            ChunkUploadStateService chunkStateService) {
        this.chunkService = chunkService;
        this.chunkStateService = chunkStateService;
    }

    @Async("masterDriveUploadExecutor")
    public void processUpload(
            String uploadId,
            String userId,
            String fileName,
            String contentType,
            long totalSize,
            int chunkSize,
            Path stagedFile) {

        try (InputStream input = Files.newInputStream(stagedFile)) {
            long remaining = totalSize;
            int chunkIndex = 0;

            while (remaining > 0) {
                int expectedSize = (int) Math.min((long) chunkSize, remaining);
                byte[] chunkBytes = input.readNBytes(expectedSize);
                if (chunkBytes.length != expectedSize) {
                    throw new IOException("Staged upload ended before the expected file size.");
                }

                MultipartFile chunk = new ByteArrayMultipartFile(
                        "file", fileName, contentType, chunkBytes);
                chunkService.uploadChunk(uploadId, chunkIndex, chunk, userId);

                remaining -= expectedSize;
                chunkIndex++;
            }

            if (input.read() != -1) {
                throw new IOException("Staged upload contains more bytes than its recorded size.");
            }
        } catch (IOException | RuntimeException ex) {
            try {
                chunkStateService.markUploadFailed(uploadId, userId);
            } catch (RuntimeException stateError) {
                ex.addSuppressed(stateError);
            }
        } finally {
            try {
                Files.deleteIfExists(stagedFile);
            } catch (IOException ignored) {
                // The upload status is already authoritative; a leftover temp
                // file is not exposed as a completed Master Drive file.
            }
        }
    }
}
