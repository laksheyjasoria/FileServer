package com.app.storage.factory;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.app.config.TelegramStorageProperties;
import com.app.core.config.AppProperties;
import com.app.core.exception.StorageException;
import com.app.storage.service.LocalFileStorageService;
import com.app.storage.service.TelegramStorageService;
import com.app.telegram.TelegramClient;
import com.app.telegram.TelegramConnectionManager;

class StorageFactoryTest {

    @Test
    void shouldRejectTelegramStorageWhenCredentialsAreMissing() {

        AppProperties appProperties =
                new AppProperties();

        appProperties
                .getStorage()
                .setProvider("telegram");

        TelegramStorageProperties telegramProps =
                new TelegramStorageProperties();

        telegramProps.setBotToken("");
        telegramProps.setChatId("");

        TelegramClient telegramClient =
                mock(TelegramClient.class);

        TelegramConnectionManager connectionManager =
                mock(TelegramConnectionManager.class);

        when(connectionManager.isConnected("storage"))
                .thenReturn(false);

        TelegramStorageService telegramStorage =
                new TelegramStorageService(
                        telegramProps,
                        telegramClient,
                        connectionManager);

        LocalFileStorageService local =
                new LocalFileStorageService();

        StorageFactory factory =
                new StorageFactory(
                        appProperties,
                        telegramStorage,
                        local);

        assertThrows(
                StorageException.class,
                factory::get);
    }
}