package com.app.transfer.telegram;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import com.app.core.exception.StorageException;
import com.app.telegram.TelegramClient;
import com.app.telegram.TelegramConnection;
import com.app.telegram.TelegramConnectionManager;

@Service
public class TelegramFileReader {

    private static final Logger logger = LoggerFactory.getLogger(TelegramFileReader.class);
    private static final String CONNECTION_NAME = "storage";
    private static final int BUFFER_SIZE = 64 * 1024;

    private final TelegramClient telegramClient;
    private final TelegramConnectionManager connectionManager;

    public TelegramFileReader(TelegramClient telegramClient, TelegramConnectionManager connectionManager) {
        this.telegramClient = telegramClient;
        this.connectionManager = connectionManager;
    }

    public byte[] read(String telegramFileId) {
        if (isBlank(telegramFileId)) {
            throw new StorageException("Telegram file identifier cannot be empty.");
        }
        TelegramConnection connection = getConnection();
        try {
            byte[] data = telegramClient.download(connection, telegramFileId);
            if (data == null) {
                throw new StorageException("Telegram returned no file data.");
            }
            return data;
        } catch (StorageException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.warn("Unable to read Telegram file chunk: {}", safeMessage(ex));
            throw new StorageException("Unable to read file chunk from Telegram.", ex);
        }
    }

    /**
     * Streams at most maxBytes starting at skipBytes. Telegram's file endpoint
     * may honor Range (206) or return the whole file (200). In either case the
     * response body is fully consumed inside RestTemplate's ResponseExtractor;
     * no live upstream stream escapes into Spring MVC's asynchronous response.
     */
    public long stream(
            String telegramFileId,
            long skipBytes,
            long maxBytes,
            OutputStream outputStream) throws IOException {
        if (isBlank(telegramFileId)) {
            throw new StorageException("Telegram file identifier cannot be empty.");
        }
        if (skipBytes < 0 || maxBytes < 0) {
            throw new IllegalArgumentException("Stream offsets cannot be negative.");
        }
        if (outputStream == null) {
            throw new IllegalArgumentException("Output stream cannot be null.");
        }
        if (maxBytes == 0) {
            return 0L;
        }

        TelegramConnection connection = getConnection();
        long[] written = {0L};

        try {
            telegramClient.executeFileDownloadRange(connection, telegramFileId, skipBytes, maxBytes, response -> {
                try (InputStream input = response.getBody()) {
                    if (input == null) {
                        throw new IOException("Telegram returned an empty response body.");
                    }

                    int status = response.getStatusCode().value();
                    if (status == 206) {
                        validateContentRange(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE), skipBytes);
                    } else if (status == 200) {
                        skipFully(input, skipBytes);
                    } else {
                        throw new IOException("Unexpected Telegram file response status: " + status);
                    }

                    byte[] buffer = new byte[BUFFER_SIZE];
                    long remaining = maxBytes;
                    while (remaining > 0) {
                        int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                        if (count < 0) {
                            break;
                        }
                        if (count == 0) {
                            continue;
                        }
                        try {
                            outputStream.write(buffer, 0, count);
                        } catch (IOException clientDisconnect) {
                            throw new ClientDisconnectedException(clientDisconnect);
                        }
                        written[0] += count;
                        remaining -= count;
                    }

                    if (remaining != 0) {
                        throw new IOException("Telegram stream ended early. Expected " + maxBytes
                                + " bytes but received " + written[0] + ".");
                    }

                    try {
                        outputStream.flush();
                    } catch (IOException clientDisconnect) {
                        throw new ClientDisconnectedException(clientDisconnect);
                    }
                    return null;
                }
            });
            return written[0];
        } catch (ClientDisconnectedException ex) {
            // Seeking, closing a tab or cancelling a fetch is a client-side event.
            // Do not mark the shared Telegram connection as disconnected.
            logger.debug("Client cancelled Telegram stream (offset={}, length={}).", skipBytes, maxBytes);
            throw ex;
        } catch (StorageException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.warn("Telegram range stream failed (offset={}, length={}): {}",
                    skipBytes, maxBytes, safeMessage(ex));
            throw new StorageException("Unable to stream file chunk from Telegram.", ex);
        }
    }

    private TelegramConnection getConnection() {
        TelegramConnection connection = connectionManager.getConnection(CONNECTION_NAME);
        if (connection == null) {
            throw new StorageException("Telegram storage is unavailable.");
        }
        return connection;
    }

    private void validateContentRange(String contentRange, long expectedStart) throws IOException {
        if (contentRange == null || contentRange.isBlank()) {
            // Some compatible Bot API servers omit Content-Range even for 206.
            return;
        }
        String expectedPrefix = "bytes " + expectedStart + "-";
        if (!contentRange.startsWith(expectedPrefix)) {
            throw new IOException("Telegram returned an unexpected Content-Range.");
        }
    }

    private void skipFully(InputStream input, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
            } else {
                if (input.read() < 0) {
                    throw new IOException("Telegram file ended before requested range.");
                }
                remaining--;
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private static final class ClientDisconnectedException extends RuntimeException {
        private ClientDisconnectedException(IOException cause) {
            super("HTTP client disconnected while streaming.", cause);
        }
    }
}