package com.app.storage.service;

import java.io.IOException;
import java.io.OutputStream;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    String upload(MultipartFile file);
    
    String upload(byte[] data, String fileName);

    byte[] download(String fileId);

    /**
     * Streams a byte range without requiring callers to materialize the entire file.
     * Implementations should override this method when their backend supports streaming.
     */
    default long stream(String fileId, long start, long length, OutputStream outputStream) throws IOException {
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("File ID cannot be empty.");
        }
        if (start < 0 || length < 0 || outputStream == null) {
            throw new IllegalArgumentException("Invalid stream range or output stream.");
        }
        if (length == 0) return 0L;
        byte[] data = download(fileId);
        long endExclusive;
        try {
            endExclusive = Math.addExact(start, length);
        } catch (ArithmeticException ex) {
            throw new IOException("Requested range is too large.", ex);
        }
        if (data == null || start >= data.length || endExclusive > data.length) {
            throw new IOException("Requested range is outside the stored file.");
        }
        outputStream.write(data, Math.toIntExact(start), Math.toIntExact(length));
        return length;
    }
}