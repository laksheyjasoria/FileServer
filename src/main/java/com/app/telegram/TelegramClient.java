package com.app.telegram;

import java.util.Map;

import org.springframework.core.io.AbstractResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(30000); // 30 seconds to establish connection
		factory.setReadTimeout(120000); // 2 minutes to read the response (adjust as needed)
		// Keep multipart chunk uploads streaming rather than buffering the entire request body.
		factory.setBufferRequestBody(false);
		this.restTemplate = new RestTemplate(factory);
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

			ex.printStackTrace();

			System.out.println(ex.getMessage());
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

		// Stream the multipart body from MultipartFile instead of copying the entire
		// chunk into a second byte[] (important when several 20-50 MB chunks upload in parallel).
		body.add("document", new AbstractResource() {
			@Override
			public java.io.InputStream getInputStream() throws java.io.IOException {
				return file.getInputStream();
			}

			@Override
			public long contentLength() {
				return file.getSize();
			}

			@Override
			public String getFilename() {
				return file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
			}

			@Override
			public String getDescription() {
				return "Telegram upload " + getFilename();
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

	/**
	 * Executes a Telegram file download and asks the upstream HTTP endpoint for a
	 * byte range. Telegram Bot API deployments are not guaranteed to honor Range,
	 * so callers must inspect the response status and retain a bounded-stream
	 * fallback when the server returns 200 instead of 206.
	 */
	public <T> T executeFileDownloadRange(TelegramConnection connection, String fileId, long start, long length,
			org.springframework.web.client.ResponseExtractor<T> extractor) {
		if (start < 0 || length < 0) {
			throw new IllegalArgumentException("File range values cannot be negative.");
		}
		if (length == 0) {
			throw new IllegalArgumentException("File range length must be greater than zero.");
		}
		String filePath = resolveFilePath(connection, fileId);
		String downloadUrl = FILE_API + connection.getBotToken() + "/" + filePath;
		long end;
		try {
			end = Math.addExact(start, length - 1);
		} catch (ArithmeticException ex) {
			throw new IllegalArgumentException("Requested file range is too large.", ex);
		}
		return restTemplate.execute(downloadUrl, org.springframework.http.HttpMethod.GET, request ->
				request.getHeaders().set("Range", "bytes=" + start + "-" + end), extractor);
	}

	private String resolveFilePath(TelegramConnection connection, String fileId) {
		if (connection == null || fileId == null || fileId.isBlank()) {
			throw new IllegalArgumentException("Telegram connection and file ID are required.");
		}
		String getFileUrl = BOT_API + connection.getBotToken() + "/getFile?file_id=" +
				org.springframework.web.util.UriUtils.encodeQueryParam(fileId, java.nio.charset.StandardCharsets.UTF_8);
		ResponseEntity<Map> response = restTemplate.getForEntity(getFileUrl, Map.class);
		Map<?, ?> body = response.getBody();
		if (!response.getStatusCode().is2xxSuccessful() || body == null || !Boolean.TRUE.equals(body.get("ok"))) {
			throw new IllegalStateException("Unable to obtain Telegram file.");
		}
		Map<?, ?> result = (Map<?, ?>) body.get("result");
		String path = result == null ? null : (String) result.get("file_path");
		if (path == null || path.isBlank()) {
			throw new IllegalStateException("Telegram file path missing.");
		}
		return path;
	}

	public <T> T executeFileDownload(TelegramConnection connection, String fileId,
			org.springframework.http.HttpMethod method,
			org.springframework.web.client.ResponseExtractor<T> extractor) {
		String getFileUrl = BOT_API + connection.getBotToken() + "/getFile?file_id=" + fileId;
		ResponseEntity<Map> response = restTemplate.getForEntity(getFileUrl, Map.class);
		Map<?, ?> body = response.getBody();
		if (!response.getStatusCode().is2xxSuccessful() || body == null || !Boolean.TRUE.equals(body.get("ok"))) {
			throw new IllegalStateException("Unable to obtain Telegram file.");
		}
		Map<?, ?> result = (Map<?, ?>) body.get("result");
		String filePath = result == null ? null : (String) result.get("file_path");
		if (filePath == null || filePath.isBlank()) throw new IllegalStateException("Telegram file path missing.");
		String downloadUrl = FILE_API + connection.getBotToken() + "/" + filePath;
		return restTemplate.execute(downloadUrl, method, null, extractor);
	}

	private boolean isSuccessful(ResponseEntity<Map> response) {

		if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {

			return false;
		}

		return Boolean.TRUE.equals(response.getBody().get("ok"));
	}
}