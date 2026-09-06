package com.app.upload.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChunkSizeCalculator {

    private static final long MB = 1024L * 1024L;

    private final long minChunkSize;
    private final long defaultChunkSize;
    private final long maxChunkSize;

    public ChunkSizeCalculator(
            @Value("${app.upload.chunk.min-size-mb:5}") long minSizeMb,
            @Value("${app.upload.chunk.default-size-mb:20}") long defaultSizeMb,
            @Value("${app.upload.chunk.max-size-mb:20}") long maxSizeMb) {

        if (minSizeMb <= 0 || defaultSizeMb <= 0 || maxSizeMb <= 0) {
            throw new IllegalArgumentException(
                    "Upload chunk sizes must be greater than zero.");
        }

        if (minSizeMb > defaultSizeMb) {
            throw new IllegalArgumentException(
                    "Minimum chunk size cannot exceed default chunk size.");
        }

        if (defaultSizeMb > maxSizeMb) {
            throw new IllegalArgumentException(
                    "Default chunk size cannot exceed maximum chunk size.");
        }

        this.minChunkSize = minSizeMb * MB;
        this.defaultChunkSize = defaultSizeMb * MB;
        this.maxChunkSize = maxSizeMb * MB;
    }

    public long calculateChunkSize(long totalSize) {

        if (totalSize < 0) {
            throw new IllegalArgumentException(
                    "File size cannot be negative.");
        }

        if (totalSize == 0) {
            return minChunkSize;
        }

        /*
         * Files that fit inside the configured maximum are kept
         * as a single logical chunk.
         */
        if (totalSize <= maxChunkSize) {
            return Math.max(minChunkSize, totalSize);
        }

        /*
         * For large files the standard Telegram Bot API requires
         * chunks that can subsequently be retrieved through getFile.
         *
         * The configured default is normally 20 MB and the maximum
         * is also capped at 20 MB.
         */
        return Math.min(defaultChunkSize, maxChunkSize);
    }

    public int calculateTotalChunks(long totalSize, long chunkSize) {

        if (totalSize < 0) {
            throw new IllegalArgumentException(
                    "File size cannot be negative.");
        }

        if (chunkSize <= 0) {
            throw new IllegalArgumentException(
                    "Chunk size must be greater than zero.");
        }

        if (totalSize == 0) {
            return 1;
        }

        long chunks = (totalSize + chunkSize - 1) / chunkSize;

        if (chunks > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "File requires too many chunks.");
        }

        return (int) chunks;
    }

    public long getMaxChunkSize() {
        return maxChunkSize;
    }
}