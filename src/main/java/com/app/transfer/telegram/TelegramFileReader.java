package com.app.transfer.telegram;

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

	/**
	 * Reads one logical chunk from Telegram.
	 *
	 * The telegramFileId is an internal server-side identifier. It must never be
	 * returned to the frontend.
	 *
	 * @param telegramFileId Telegram file_id stored in UploadChunk
	 * @return complete bytes of the Telegram chunk
	 */
	public byte[] read(String telegramFileId) {

		if (telegramFileId == null || telegramFileId.isBlank()) {

			throw new StorageException("Telegram file identifier cannot be empty.");
		}

		TelegramConnection connection = connectionManager.getConnection(CONNECTION_NAME);

		if (connection == null) {

			throw new StorageException("Telegram storage is unavailable.");
		}

		try {

			byte[] data = telegramClient.download(connection, telegramFileId);

			if (data == null) {

				throw new StorageException("Telegram returned no file data.");
			}

			return data;

		} catch (StorageException ex) {

			throw ex;

		} catch (Exception ex) {

			throw new StorageException("Unable to read file chunk from Telegram.");
		}
	}
}