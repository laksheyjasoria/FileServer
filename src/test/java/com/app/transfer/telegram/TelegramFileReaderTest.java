package com.app.transfer.telegram;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.app.telegram.TelegramClient;
import com.app.telegram.TelegramConnection;
import com.app.telegram.TelegramConnectionManager;

class TelegramFileReaderTest {

	@Test
	void shouldReadChunkFromTelegram() {

		TelegramClient client = org.mockito.Mockito.mock(TelegramClient.class);

		TelegramConnectionManager manager = org.mockito.Mockito.mock(TelegramConnectionManager.class);

		TelegramConnection connection = new TelegramConnection("test-token", "test-chat");

		connection.setConnected(true);

		when(manager.getConnection("storage")).thenReturn(connection);

		byte[] expected = new byte[] { 1, 2, 3, 4, 5 };

		when(client.download(connection, "telegram-file-id")).thenReturn(expected);

		TelegramFileReader reader = new TelegramFileReader(client, manager);

		byte[] actual = reader.read("telegram-file-id");

		assertArrayEquals(expected, actual);
	}

	@Test
	void shouldRejectEmptyTelegramFileId() {

		TelegramClient client = org.mockito.Mockito.mock(TelegramClient.class);

		TelegramConnectionManager manager = org.mockito.Mockito.mock(TelegramConnectionManager.class);

		TelegramFileReader reader = new TelegramFileReader(client, manager);

		assertThrows(RuntimeException.class, () -> reader.read(""));
	}
}