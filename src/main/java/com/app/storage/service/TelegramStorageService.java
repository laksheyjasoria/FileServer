package com.app.storage.service;

import java.io.IOException;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.app.config.TelegramStorageProperties;
import com.app.core.exception.StorageException;
import com.app.core.util.ByteArrayMultipartFile;
import com.app.telegram.TelegramClient;
import com.app.telegram.TelegramConnection;
import com.app.telegram.TelegramConnectionManager;
import com.app.transfer.telegram.TelegramFileReader;

import jakarta.annotation.PostConstruct;

@Service
public class TelegramStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(TelegramStorageService.class);
    private static final String CONNECTION_NAME = "storage";

    private final TelegramStorageProperties props;
    private final TelegramClient telegramClient;
    private final TelegramConnectionManager connectionManager;
    private final TelegramFileReader telegramFileReader;

    public TelegramStorageService(
            TelegramStorageProperties props,
            TelegramClient telegramClient,
            TelegramConnectionManager connectionManager,
            TelegramFileReader telegramFileReader) {
        this.props = props;
        this.telegramClient = telegramClient;
        this.connectionManager = connectionManager;
        this.telegramFileReader = telegramFileReader;
    }

    @PostConstruct
    public void initialize() {
        if (props == null) {
            log.warn("Telegram storage properties are not available.");
            return;
        }
        connectionManager.register(CONNECTION_NAME, props.getBotToken(), props.getChatId());
    }

    public boolean isConfigured() {
        return connectionManager.isConnected(CONNECTION_NAME);
    }

    @Override
    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new StorageException("Upload file cannot be null or empty.");
        }

        TelegramConnection connection = requireConnection();
        try {
            log.info("Uploading file '{}' ({} bytes) to Telegram",
                    safeFilename(file.getOriginalFilename()), file.getSize());
            String fileId = telegramClient.sendDocument(connection, file);
            log.info("Telegram upload completed ({} bytes).", file.getSize());
            return fileId;
        } catch (StorageException ex) {
            throw ex;
        } catch (Exception ex) {
            // Only a confirmed invalid bot token is grounds to invalidate the shared
            // connection. Rate limits, chat errors, parsing errors and transient network
            // failures must not break other in-flight chunk uploads.
            if (ex instanceof TelegramClient.TelegramApiException apiException
                    && Integer.valueOf(401).equals(apiException.getHttpStatus())) {
                connectionManager.markDisconnected(CONNECTION_NAME);
            }
            log.error("Telegram upload failed for '{}' ({} bytes): {}",
                    safeFilename(file.getOriginalFilename()), file.getSize(), safeMessage(ex));
            throw new StorageException("Telegram upload failed. " + safeMessage(ex), ex);
        }
    }

    @Override
    public String upload(byte[] data, String fileName) {
        if (data == null || data.length == 0) {
            throw new StorageException("Upload data cannot be null or empty.");
        }
        MultipartFile file = new ByteArrayMultipartFile(
                fileName, fileName, "application/octet-stream", data);
        return upload(file);
    }

    @Override
    public long stream(String fileId, long start, long length, OutputStream outputStream) throws IOException {
        if (fileId == null || fileId.isBlank()) {
            throw new StorageException("Telegram file ID cannot be empty.");
        }
        requireConnection();
        return telegramFileReader.stream(fileId, start, length, outputStream);
    }

    @Override
    public byte[] download(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            throw new StorageException("Telegram file ID cannot be empty.");
        }
        TelegramConnection connection = requireConnection();
        try {
            byte[] data = telegramClient.download(connection, fileId);
            if (data == null) {
                throw new StorageException("Telegram returned empty file data.");
            }
            return data;
        } catch (StorageException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Telegram download failed: {}", safeMessage(ex));
            throw new StorageException("Telegram download failed. " + safeMessage(ex), ex);
        }
    }

    private TelegramConnection requireConnection() {
        TelegramConnection connection = connectionManager.getConnection(CONNECTION_NAME);
        if (connection == null) {
            throw new StorageException(
                    "Telegram storage is unavailable. Check the bot token, chat ID and Telegram connectivity.");
        }
        return connection;
    }

    private String safeFilename(String filename) {
        return filename == null || filename.isBlank() ? "unnamed" : filename.replaceAll("[\\r\\n]", "_");
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        if (message.toLowerCase().contains("api.telegram.org/")) {
            return ex.getClass().getSimpleName();
        }
        return message;
    }
}