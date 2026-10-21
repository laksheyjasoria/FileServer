package com.app.upload.service;

import java.io.IOException;
import java.io.OutputStream;

import org.springframework.stereotype.Service;

import com.app.core.exception.UploadNotFoundException;
import com.app.transfer.telegram.TelegramFileReader;
import com.app.upload.entity.UploadChunk;
import com.app.upload.repository.UploadChunkRepository;

@Service
public class UploadChunkReader {
    private final UploadChunkRepository chunkRepository;
    private final TelegramFileReader telegramFileReader;

    public UploadChunkReader(UploadChunkRepository chunkRepository, TelegramFileReader telegramFileReader) {
        this.chunkRepository = chunkRepository;
        this.telegramFileReader = telegramFileReader;
    }

    public byte[] read(String uploadId, int chunkIndex) {
        UploadChunk chunk = getCompletedChunk(uploadId, chunkIndex);
        byte[] data = telegramFileReader.read(chunk.getTelegramFileId());
        if (chunk.getSize() != null && chunk.getSize() != data.length) {
            throw new IllegalStateException("Telegram chunk size does not match database metadata.");
        }
        return data;
    }

    public long stream(String uploadId, int chunkIndex, long localStart, long length, OutputStream outputStream)
            throws IOException {
        UploadChunk chunk = getCompletedChunk(uploadId, chunkIndex);
        if (localStart < 0 || length < 0) throw new IllegalArgumentException("Chunk stream range cannot be negative.");
        if (chunk.getSize() != null && localStart + length > chunk.getSize()) {
            throw new IllegalArgumentException("Requested chunk range exceeds chunk size.");
        }
        return telegramFileReader.stream(chunk.getTelegramFileId(), localStart, length, outputStream);
    }

    private UploadChunk getCompletedChunk(String uploadId, int chunkIndex) {
        if (uploadId == null || uploadId.isBlank()) throw new IllegalArgumentException("Upload ID cannot be empty.");
        if (chunkIndex < 0) throw new IllegalArgumentException("Chunk index cannot be negative.");
        UploadChunk chunk = chunkRepository.findByUploadJobIdAndChunkIndex(uploadId, chunkIndex)
                .orElseThrow(UploadNotFoundException::new);
        if (chunk.getStatus() != com.app.upload.entity.UploadChunkStatus.COMPLETED) {
            throw new IllegalStateException("Upload chunk " + chunkIndex + " is not completed.");
        }
        if (chunk.getTelegramFileId() == null || chunk.getTelegramFileId().isBlank()) {
            throw new IllegalStateException("Upload chunk has no Telegram storage reference.");
        }
        return chunk;
    }
}
