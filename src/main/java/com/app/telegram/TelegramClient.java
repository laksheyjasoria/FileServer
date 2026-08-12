package com.app.telegram;

import java.util.Map;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Component
public class TelegramClient {

	private static final String BOT_API = "https://api.telegram.org/bot";

	private static final String FILE_API = "https://api.telegram.org/file/bot";

	private final RestTemplate restTemplate;

	public TelegramClient() {
		this.restTemplate = new RestTemplate();
	}

	public boolean validate(String botToken, String chatId) {

		if (botToken == null || botToken.isBlank() || chatId == null || chatId.isBlank()) {

			return false;
		}

		try {

			/*
			 * Validate bot token.
			 */
			String getMeUrl = BOT_API + botToken + "/getMe";

			ResponseEntity<Map> getMeResponse = restTemplate.getForEntity(getMeUrl, Map.class);

			if (!isSuccessful(getMeResponse)) {
				return false;
			}

			/*
			 * Validate chat ID.
			 */
			String getChatUrl = BOT_API + botToken + "/getChat";

			HttpHeaders headers = new HttpHeaders();

			headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

			MultiValueMap<String, String> form = new LinkedMultiValueMap<>();

			form.add("chat_id", chatId);

			HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

			ResponseEntity<Map> getChatResponse = restTemplate.postForEntity(getChatUrl, request, Map.class);

			return isSuccessful(getChatResponse);

		} catch (Exception ex) {

			return false;
		}
	}

	public void sendMessage(TelegramConnection connection, String message) {

		String url = BOT_API + connection.getBotToken() + "/sendMessage";

		HttpHeaders headers = new HttpHeaders();

		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();

		form.add("chat_id", connection.getChatId());

		form.add("text", message);
		
		form.add("parse_mode", "HTML");

		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

		restTemplate.postForEntity(url, request, Map.class);
	}

	public String sendDocument(TelegramConnection connection, MultipartFile file) throws Exception {

		String url = BOT_API + connection.getBotToken() + "/sendDocument";

		HttpHeaders headers = new HttpHeaders();

		headers.setContentType(MediaType.MULTIPART_FORM_DATA);

		LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

		body.add("chat_id", connection.getChatId());

		body.add("document", new ByteArrayResource(file.getBytes()) {

			@Override
			public String getFilename() {
				return file.getOriginalFilename();
			}
		});

		HttpEntity<LinkedMultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

		ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

		Map<?, ?> bodyMap = response.getBody();

		if (!response.getStatusCode().is2xxSuccessful() || bodyMap == null || !Boolean.TRUE.equals(bodyMap.get("ok"))) {

			throw new IllegalStateException("Telegram sendDocument failed.");
		}

		Map<?, ?> result = (Map<?, ?>) bodyMap.get("result");

		if (result == null) {
			throw new IllegalStateException("Telegram result missing.");
		}

		Map<?, ?> document = (Map<?, ?>) result.get("document");

		if (document == null) {
			throw new IllegalStateException("Telegram document missing.");
		}

		String fileId = (String) document.get("file_id");

		if (fileId == null || fileId.isBlank()) {
			throw new IllegalStateException("Telegram file_id missing.");
		}

		return fileId;
	}

	public byte[] download(TelegramConnection connection, String fileId) {

		String getFileUrl = BOT_API + connection.getBotToken() + "/getFile?file_id=" + fileId;

		ResponseEntity<Map> response = restTemplate.getForEntity(getFileUrl, Map.class);

		Map<?, ?> responseBody = response.getBody();

		if (!response.getStatusCode().is2xxSuccessful() || responseBody == null
				|| !Boolean.TRUE.equals(responseBody.get("ok"))) {

			throw new IllegalStateException("Unable to obtain Telegram file.");
		}

		Map<?, ?> result = (Map<?, ?>) responseBody.get("result");

		if (result == null) {
			throw new IllegalStateException("Telegram file result missing.");
		}

		String filePath = (String) result.get("file_path");

		if (filePath == null || filePath.isBlank()) {
			throw new IllegalStateException("Telegram file path missing.");
		}

		String downloadUrl = FILE_API + connection.getBotToken() + "/" + filePath;

		return restTemplate.getForObject(downloadUrl, byte[].class);
	}

	private boolean isSuccessful(ResponseEntity<Map> response) {

		if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {

			return false;
		}

		return Boolean.TRUE.equals(response.getBody().get("ok"));
	}
}