package com.app.telegram;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TelegramConnectionManager {

	private static final Logger log = LoggerFactory.getLogger(TelegramConnectionManager.class);

	private final TelegramClient telegramClient;

	private final Map<String, TelegramConnection> connections = new ConcurrentHashMap<>();

	public TelegramConnectionManager(TelegramClient telegramClient) {

		this.telegramClient = telegramClient;
	}

	public boolean register(String name, String botToken, String chatId) {

		if (name == null || name.isBlank()) {
			return false;
		}

		TelegramConnection connection = new TelegramConnection(botToken, chatId);

		connections.put(name, connection);

		boolean connected = validate(connection);

		connection.setConnected(connected);

		if (connected) {

			log.info("Telegram connection '{}' established.", name);

		} else {

			log.warn("Telegram connection '{}' could not be established. " + "It will be retried automatically.", name);
		}

		return connected;
	}

	public boolean isConnected(String name) {

		TelegramConnection connection = connections.get(name);

		return connection != null && connection.isConnected();
	}

	public TelegramConnection getConnection(String name) {

		TelegramConnection connection = connections.get(name);

		if (connection == null || !connection.isConnected()) {

			return null;
		}

		return connection;
	}

	public void markDisconnected(String name) {

		TelegramConnection connection = connections.get(name);

		if (connection != null && connection.isConnected()) {

			connection.setConnected(false);

			log.warn("Telegram connection '{}' marked as disconnected.", name);
		}
	}

	private boolean validate(TelegramConnection connection) {

		return telegramClient.validate(connection.getBotToken(), connection.getChatId());
	}

	/**
	 * Retry only connections which are currently disconnected.
	 *
	 * Every 5 minutes.
	 */
	@Scheduled(fixedDelay = 300000)
	public void reconnectDisconnectedConnections() {

		for (Map.Entry<String, TelegramConnection> entry : connections.entrySet()) {

			String name = entry.getKey();

			TelegramConnection connection = entry.getValue();

			if (connection.isConnected()) {
				continue;
			}

			log.info("Attempting to reconnect Telegram connection '{}'.", name);

			boolean connected = validate(connection);

			if (connected) {

				connection.setConnected(true);

				log.info("Telegram connection '{}' reconnected successfully.", name);

			} else {

				log.warn("Telegram connection '{}' is still unavailable.", name);
			}
		}
	}
}