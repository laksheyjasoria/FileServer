package com.app.logger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.app.config.TelegramLoggerProperties;
import com.app.telegram.TelegramClient;
import com.app.telegram.TelegramConnection;
import com.app.telegram.TelegramConnectionManager;

import jakarta.annotation.PostConstruct;

@Service
public class TelegramLoggerService {

	private static final Logger log = LoggerFactory.getLogger(TelegramLoggerService.class);

	private static final String CONNECTION_NAME = "logger";

	private final TelegramLoggerProperties props;
	private final TelegramClient telegramClient;
	private final TelegramConnectionManager connectionManager;

	public TelegramLoggerService(TelegramLoggerProperties props, TelegramClient telegramClient,
			TelegramConnectionManager connectionManager) {

		this.props = props;
		this.telegramClient = telegramClient;
		this.connectionManager = connectionManager;
	}

	@PostConstruct
	public void initialize() {

		if (props == null) {

			log.warn("Telegram logger properties are not available.");

			return;
		}

		if (!props.isEnabled()) {

			log.info("Telegram logger is disabled.");

			return;
		}

		connectionManager.register(CONNECTION_NAME, props.getBotToken(), props.getChatId());
	}

	public boolean send(String message) {

		if (props == null || !props.isEnabled()) {

			return true;
		}

		if (message == null || message.isBlank()) {
			return true;
		}

		TelegramConnection connection = connectionManager.getConnection(CONNECTION_NAME);

		if (connection == null) {

			/*
			 * Do not call Telegram here.
			 *
			 * ConnectionManager will retry automatically.
			 */
			return false;
		}

		try {

			telegramClient.sendMessage(connection, message);

			log.debug("Telegram log sent successfully.");
			return true;

		} catch (Exception ex) {

			/*
			 * Mark disconnected.
			 *
			 * ConnectionManager will handle recovery.
			 */
			connectionManager.markDisconnected(CONNECTION_NAME);

			/*
			 * Do not send this exception through TelegramLoggerService, otherwise we could
			 * create a logging loop.
			 */
			System.err.println("Telegram logger send failed. " + "Connection marked unavailable.");
			return false;
		}

	}

	public boolean isConnected() {

		return connectionManager.isConnected(CONNECTION_NAME);
	}
}