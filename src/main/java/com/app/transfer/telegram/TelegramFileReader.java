package com.app.transfer.telegram;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.app.core.exception.StorageException;
import com.app.telegram.TelegramClient;
import com.app.telegram.TelegramConnection;
import com.app.telegram.TelegramConnectionManager;

@Service
public class TelegramFileReader {
    private static final Logger logger = LoggerFactory.getLogger(TelegramFileReader.class);
    private static final String CONNECTION_NAME = "storage";
    private final TelegramClient telegramClient;
    private final TelegramConnectionManager connectionManager;

    public TelegramFileReader(TelegramClient telegramClient, TelegramConnectionManager connectionManager) {
        this.telegramClient = telegramClient;
        this.connectionManager = connectionManager;
    }

    public byte[] read(String telegramFileId) {
        if (telegramFileId == null || telegramFileId.isBlank()) throw new StorageException("Telegram file identifier cannot be empty.");
        TelegramConnection connection = getConnection();
        try {
            byte[] data = telegramClient.download(connection, telegramFileId);
            if (data == null) throw new StorageException("Telegram returned no file data.");
            return data;
        } catch (StorageException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.warn("Unable to read Telegram file chunk.", ex);
            throw new StorageException("Unable to read file chunk from Telegram.", ex);
        }
    }

    /**
     * Streams at most {@code maxBytes} starting at {@code skipBytes}. It asks
     * Telegram's file endpoint for a range first. If the endpoint ignores Range
     * and returns 200, the method falls back to skipping through a fixed-size
     * buffer; memory use remains bounded, though latency can be proportional to
     * the requested offset.
     */
    public long stream(String telegramFileId, long skipBytes, long maxBytes, OutputStream outputStream) throws IOException {
        if (telegramFileId == null || telegramFileId.isBlank()) {
            throw new StorageException("Telegram file identifier cannot be empty.");
        }
        if (skipBytes < 0 || maxBytes < 0) {
            throw new IllegalArgumentException("Stream offsets cannot be negative.");
        }
        if (maxBytes == 0) {
            return 0L;
        }

        TelegramConnection connection = getConnection();
        final long[] written = {0L};
        try {
            telegramClient.executeFileDownloadRange(connection, telegramFileId, skipBytes, maxBytes, response -> {
                try (InputStream input = response.getBody()) {
                    if (input == null) {
                        throw new IOException("Telegram returned an empty response body.");
                    }
                    long remaining = maxBytes;
                    // If upstream honored Range, its body starts at skipBytes.
                    // If not, it returned the full file and we must skip locally.
                    if (response.getStatusCode().value() != 206) {
                        skipFully(input, skipBytes);
                    } else {
                        String contentRange = response.getHeaders().getFirst("Content-Range");
                        if (contentRange != null && !contentRange.startsWith("bytes " + skipBytes + "-")) {
                            throw new IOException("Telegram returned an unexpected Content-Range.");
                        }
                    }
                    byte[] buffer = new byte[64 * 1024];
                    while (remaining > 0) {
                        int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                        if (count < 0) {
                            break;
                        }
                        if (count == 0) {
                            continue;
                        }
                        outputStream.write(buffer, 0, count);
                        written[0] += count;
                        remaining -= count;
                    }
                    if (remaining != 0) {
                        throw new IOException("Telegram stream ended early. Expected " + maxBytes
                                + " bytes but received " + written[0] + ".");
                    }
                    outputStream.flush();
                    return null;
                }
            });
            return written[0];
        } catch (StorageException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.warn("Unable to stream Telegram file chunk (offset={}, length={}).", skipBytes, maxBytes, ex);
            throw new StorageException("Unable to stream file chunk from Telegram.", ex);
        }
    }

    private TelegramConnection getConnection() {
        TelegramConnection connection = connectionManager.getConnection(CONNECTION_NAME);
        if (connection == null) throw new StorageException("Telegram storage is unavailable.");
        return connection;
    }

    private void skipFully(InputStream input, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped > 0) remaining -= skipped;
            else if (input.read() < 0) throw new IOException("Telegram file ended before requested range.");
            else remaining--;
        }
    }
}
