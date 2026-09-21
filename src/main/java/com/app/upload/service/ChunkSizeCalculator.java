package com.app.upload.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChunkSizeCalculator {

    private static final long MB = 1024L * 1024L;

    private final long minChunkSize;
    private final long defaultChunkSize;
    private final long maxChunkSize;
    private final long targetChunkCount;

    public ChunkSizeCalculator(
            @Value("${app.upload.chunk.min-size-mb:5}") long minSizeMb,
            @Value("${app.upload.chunk.default-size-mb:10}") long defaultSizeMb,
            @Value("${app.upload.chunk.max-size-mb:20}") long maxSizeMb,
            @Value("${app.upload.chunk.target-count:50}") long targetCount) {

        if (minSizeMb <= 0 || defaultSizeMb <= 0 || maxSizeMb <= 0 || targetCount <= 0) {
            throw new IllegalArgumentException("Upload chunk configuration must be greater than zero.");
        }

        if (minSizeMb > defaultSizeMb) {
            throw new IllegalArgumentException("Minimum chunk size cannot exceed default chunk size.");
        }

        if (defaultSizeMb > maxSizeMb) {
            throw new IllegalArgumentException("Default chunk size cannot exceed maximum chunk size.");
        }

        this.minChunkSize = minSizeMb * MB;
        this.defaultChunkSize = defaultSizeMb * MB;
        this.maxChunkSize = maxSizeMb * MB;
        this.targetChunkCount = targetCount;
    }

    /**
     * Calculates a server-authoritative adaptive chunk size.
     *
     * Small files are kept as a single chunk up to the configured default size.
     * Larger files target a bounded number of logical chunks while respecting the
     * Telegram Bot API download ceiling used by this application.
     */
    public long calculateChunkSize(long totalSize) {

        if (totalSize < 0) {
            throw new IllegalArgumentException("File size cannot be negative.");
        }

        if (totalSize == 0) {
            return minChunkSize;
        }

        if (totalSize <= defaultChunkSize) {
            return Math.max(minChunkSize, totalSize);
        }

        long ideal = divideCeiling(totalSize, targetChunkCount);

        // Round up to a whole MiB so browser/server chunk boundaries stay simple.
        long rounded = divideCeiling(ideal, MB) * MB;

        return Math.min(maxChunkSize, Math.max(minChunkSize, rounded));
    }

    public int calculateTotalChunks(long totalSize, long chunkSize) {

        if (totalSize < 0) {
            throw new IllegalArgumentException("File size cannot be negative.");
        }

        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be greater than zero.");
        }

        if (totalSize == 0) {
            return 1;
        }

        long chunks = divideCeiling(totalSize, chunkSize);

        if (chunks > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("File requires too many chunks.");
        }

        return (int) chunks;
    }

    public long getMaxChunkSize() {
        return maxChunkSize;
    }

    private long divideCeiling(long value, long divisor) {
        return (value / divisor) + (value % divisor == 0 ? 0 : 1);
    }
}
