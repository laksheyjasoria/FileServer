//// package com.app.logger.service;
//
//// import java.util.HashMap;
//// import java.util.Map;
//
//// import org.slf4j.Logger;
//// import org.slf4j.LoggerFactory;
//// import org.springframework.stereotype.Service;
//// import org.springframework.web.client.RestTemplate;
//
//// import com.app.config.TelegramLoggerProperties;
//
//// @Service
//// public class TelegramLoggerService {
//
//// 	private final TelegramLoggerProperties props;
//// 	private final RestTemplate rest = new RestTemplate();
//
//// 	public TelegramLoggerService(
//// 			TelegramLoggerProperties props) {
//
//// 		this.props = props;
//// 	}
//
//// 	public void send(String message) {
//
//// 		if (!props.isEnabled()) {
//// 			return;
//// 		}
//
//// 		if (props.getBotToken() == null || props.getBotToken().isBlank() || props.getChatId() == null
//// 				|| props.getChatId().isBlank()) {
//// 			return;
//// 		}
//
//// 		log.info("Telegram logger resolved: botTokenPrefix={}, chatId={}",
//// 				props.getBotToken().substring(0, Math.min(8, props.getBotToken().length())),
//// 				props.getChatId());
//
//// 		try {
//
//// 			String url = "https://api.telegram.org/bot"
//// 					+ props.getBotToken()
//// 					+ "/sendMessage";
//
//// 			Map<String, Object> body = new HashMap<>();
//
//// 			body.put("chat_id",
//// 					props.getChatId());
//
//// 			body.put("text",
//// 					message);
//
//// 			body.put("parse_mode",
//// 					"HTML");
//
//// 			rest.postForObject(
//// 					url,
//// 					body,
//// 					String.class);
//
//// 		} catch (Exception ignored) {
//// 		}
//// 	}
//// }
//
//package com.app.logger.service;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.http.HttpEntity;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.MediaType;
//import org.springframework.stereotype.Service;
//import org.springframework.web.client.RestTemplate;
//
//import com.app.config.TelegramLoggerProperties;
//
//@Service
//public class TelegramLoggerService {
//
//	private static final Logger log = LoggerFactory.getLogger(TelegramLoggerService.class);
//
//	private final TelegramLoggerProperties props;
//	private final RestTemplate restTemplate;
//
////	public TelegramLoggerService(TelegramLoggerProperties props) {
////		this.props = props;
////		this.restTemplate = new RestTemplate();
////	}
//	
//	public TelegramLoggerService(TelegramLoggerProperties props) {
//	    this.props = props;
//	    this.restTemplate = new RestTemplate();
//
//	    String token = props.getBotToken();
//
//	    System.out.printf(
//	        "TELEGRAM CONFIG -> enabled={}, tokenLength={}, tokenPrefix={}, chatId={}",
//	        props.isEnabled(),
//	        token == null ? 0 : token.length(),
//	        token == null ? "NULL"
//	                : token.substring(0, Math.min(10, token.length())),
//	        props.getChatId()
//	    );
//	}
//
//	public void send(String message) {
//
//		if (props == null) {
//			log.warn("TelegramLoggerProperties is null.");
//			return;
//		}
//
//		if (!props.isEnabled()) {
//			log.debug("Telegram logger is disabled.");
//			return;
//		}
//
//		if (props.getBotToken() == null || props.getBotToken().isBlank()) {
//			log.error("Telegram logger bot token is missing.");
//			return;
//		}
//
//		if (props.getChatId() == null || props.getChatId().isBlank()) {
//			log.error("Telegram logger chat id is missing.");
//			return;
//		}
//		
//		System.out.printf(
//			    "TELEGRAM URL = https://api.telegram.org/bot{}...",
//			    props.getBotToken() == null
//			        ? "NULL"
//			        : props.getBotToken().substring(
//			            0,
//			            Math.min(10, props.getBotToken().length())
//			        )
//			);
//
//		System.out.printf("TELEGRAM CHAT ID = {}", props.getChatId());
//
//		try {
//
//			String url = "https://api.telegram.org/bot"
//					+ props.getBotToken()
//					+ "/sendMessage";
//
//			HttpHeaders headers = new HttpHeaders();
//			headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
//
//			String payload = "chat_id=" + props.getChatId()
//					+ "&text=" + message
//					+ "&parse_mode=HTML";
//
//			HttpEntity<String> request = new HttpEntity<>(payload, headers);
//
//			restTemplate.postForEntity(url, request, String.class);
//
//			log.debug("Telegram log sent successfully.");
//
//		} catch (Exception ex) {
//
//			log.error("Failed to send Telegram log.", ex);
//		}
//	}
//}

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

	public void send(String message) {

		if (props == null || !props.isEnabled()) {

			return;
		}

		if (message == null || message.isBlank()) {
			return;
		}

		TelegramConnection connection = connectionManager.getConnection(CONNECTION_NAME);

		if (connection == null) {

			/*
			 * Do not call Telegram here.
			 *
			 * ConnectionManager will retry automatically.
			 */
			return;
		}

		try {

			telegramClient.sendMessage(connection, message);

			log.debug("Telegram log sent successfully.");

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
		}
	}

	public boolean isConnected() {

		return connectionManager.isConnected(CONNECTION_NAME);
	}
}