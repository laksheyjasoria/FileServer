package com.app.transfer.telegram;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.springframework.stereotype.Service;

import com.app.core.exception.StorageException;
import com.app.telegram.TelegramClient;
import com.app.telegram.TelegramConnection;
import com.app.telegram.TelegramConnectionManager;

@Service
public class TelegramFileReader {
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
            throw new StorageException("Unable to read file chunk from Telegram.");
        }
    }

    public long stream(String telegramFileId, long skipBytes, long maxBytes, OutputStream outputStream) throws IOException {
        if (telegramFileId == null || telegramFileId.isBlank()) throw new StorageException("Telegram file identifier cannot be empty.");
        if (skipBytes < 0 || maxBytes < 0) throw new IllegalArgumentException("Stream offsets cannot be negative.");
        TelegramConnection connection = getConnection();
        final long[] written = {0L};
        try {
            telegramClient.executeFileDownload(connection, telegramFileId, org.springframework.http.HttpMethod.GET, response -> {
                try (InputStream input = response.getBody()) {
                    if (input == null) throw new IOException("Telegram returned an empty response body.");
                    skipFully(input, skipBytes);
                    byte[] buffer = new byte[64 * 1024];
                    long remaining = maxBytes;
                    while (remaining > 0) {
                        int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                        if (read < 0) break;
                        outputStream.write(buffer, 0, read);
                        written[0] += read;
                        remaining -= read;
                    }
                    outputStream.flush();
                    return null;
                }
            });
            return written[0];
        } catch (StorageException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new StorageException("Unable to stream file chunk from Telegram.");
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
