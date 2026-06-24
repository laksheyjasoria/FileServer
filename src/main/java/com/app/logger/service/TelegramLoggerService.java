package com.app.logger.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.app.config.TelegramLoggerProperties;

@Service
public class TelegramLoggerService {

	private final TelegramLoggerProperties props;
	private final RestTemplate rest = new RestTemplate();

	public TelegramLoggerService(
			TelegramLoggerProperties props) {

		this.props = props;
	}

	public void send(String message) {

		if (!props.isEnabled()) {
			return;
		}

		try {

			String url =
					"https://api.telegram.org/bot"
					+ props.getBotToken()
					+ "/sendMessage";

			Map<String, Object> body =
					new HashMap<>();

			body.put("chat_id",
					props.getChatId());

			body.put("text",
					message);

			body.put("parse_mode",
					"HTML");

			rest.postForObject(
					url,
					body,
					String.class);

		} catch (Exception ignored) {
		}
	}
}