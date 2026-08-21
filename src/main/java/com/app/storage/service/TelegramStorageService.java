//// package com.app.storage.service;
//
//// import java.io.IOException;
//// import java.util.Map;
//
//// import org.slf4j.Logger;
//// import org.slf4j.LoggerFactory;
//// import org.springframework.http.HttpEntity;
//// import org.springframework.http.HttpHeaders;
//// import org.springframework.http.MediaType;
//// import org.springframework.http.ResponseEntity;
//// import org.springframework.stereotype.Service;
//// import org.springframework.web.client.RestTemplate;
//// import org.springframework.web.client.RestClientResponseException;
//// import org.springframework.web.multipart.MultipartFile;
//
//// import com.app.config.TelegramStorageProperties;
//// import com.app.core.exception.StorageException;
//
//// @Service
//// public class TelegramStorageService implements StorageService {
//
//// 	private static final Logger log = LoggerFactory.getLogger(TelegramStorageService.class);
//
//// 	private final TelegramStorageProperties props;
//// 	private final RestTemplate restTemplate = new RestTemplate();
//
//// 	public TelegramStorageService(TelegramStorageProperties props) {
//// 		this.props = props;
//// 	}
//
//// 	public boolean isConfigured() {
//// 		boolean botTokenPresent = props != null && props.getBotToken() != null && !props.getBotToken().isBlank();
//// 		boolean chatIdPresent = props != null && props.getChatId() != null && !props.getChatId().isBlank();
//
//// 		log.info("Telegram storage config loaded: botTokenPresent={}, chatIdPresent={}, botTokenPrefix={}, chatId={}",
//// 				botTokenPresent,
//// 				chatIdPresent,
//// 				botTokenPresent ? props.getBotToken().substring(0, Math.min(8, props.getBotToken().length()))
//// 						: "<missing>",
//// 				chatIdPresent ? props.getChatId() : "<missing>");
//
//// 		return botTokenPresent && chatIdPresent;
//// 	}
//
//// 	// ================= UPLOAD =================
//// 	@Override
//// 	public String upload(MultipartFile file) {
//
//// 		try {
//// 			if (props.getBotToken() == null || props.getBotToken().isBlank()
//// 					|| props.getChatId() == null || props.getChatId().isBlank()) {
//// 				throw new StorageException(
//// 						"Telegram storage is not configured. Set TELEGRAM_STORAGE_BOT_TOKEN and TELEGRAM_STORAGE_CHAT_ID.");
//// 			}
//
//// 			String url = "https://api.telegram.org/bot" + props.getBotToken() + "/sendDocument";
//
//// 			HttpHeaders headers = new HttpHeaders();
//// 			headers.setContentType(MediaType.MULTIPART_FORM_DATA);
//
//// 			var body = new org.springframework.util.LinkedMultiValueMap<String, Object>();
//
//// 			body.add("chat_id", props.getChatId());
//// 			body.add("document", new org.springframework.core.io.ByteArrayResource(file.getBytes()) {
//// 				@Override
//// 				public String getFilename() {
//// 					return file.getOriginalFilename();
//// 				}
//// 			});
//
//// 			HttpEntity<?> request = new HttpEntity<>(body, headers);
//
//// 			ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
//
//// 			// 🔥 Extract file_id
//// 			if (response.getBody() == null || !Boolean.TRUE.equals(response.getBody().get("ok"))) {
//// 				throw new StorageException("Telegram rejected the upload request. Verify the bot token and chat ID.");
//// 			}
//// 			Map result = (Map) response.getBody().get("result");
//// 			Map document = (Map) result.get("document");
//
//// 			return (String) document.get("file_id");
//
//// 		} catch (RestClientResponseException e) {
//// 			throw new StorageException("Telegram rejected the upload request (HTTP " + e.getStatusCode().value()
//// 					+ "). Verify TELEGRAM_STORAGE_BOT_TOKEN and TELEGRAM_STORAGE_CHAT_ID.");
//// 		} catch (IOException e) {
//// 			throw new StorageException("Telegram upload failed");
//// 		} catch (StorageException e) {
//// 			throw e;
//// 		} catch (Exception e) {
//// 			throw new StorageException(
//// 					"Telegram upload failed. Verify the storage configuration and network connection.");
//// 		}
//// 	}
//
//// 	// ================= DOWNLOAD =================
//// 	@Override
//// 	public byte[] download(String fileId) {
//
//// 		try {
//
//// 			// Step 1: get file path
//// 			String getFileUrl = "https://api.telegram.org/bot" + props.getBotToken() + "/getFile?file_id=" + fileId;
//
//// 			Map response = restTemplate.getForObject(getFileUrl, Map.class);
//
//// 			Map result = (Map) response.get("result");
//// 			String filePath = (String) result.get("file_path");
//
//// 			// Step 2: download file
//// 			String fileUrl = "https://api.telegram.org/file/bot" + props.getBotToken() + "/" + filePath;
//
//// 			return restTemplate.getForObject(fileUrl, byte[].class);
//
//// 		} catch (Exception e) {
//// 			throw new StorageException("Telegram download failed");
//// 		}
//// 	}
//// }
//
//package com.app.storage.service;
//
//import java.io.IOException;
//import java.util.Map;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.core.io.ByteArrayResource;
//import org.springframework.http.HttpEntity;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.MediaType;
//import org.springframework.http.ResponseEntity;
//import org.springframework.stereotype.Service;
//import org.springframework.util.LinkedMultiValueMap;
//import org.springframework.web.client.RestClientResponseException;
//import org.springframework.web.client.RestTemplate;
//import org.springframework.web.multipart.MultipartFile;
//
//import com.app.config.TelegramStorageProperties;
//import com.app.core.exception.StorageException;
//
//@Service
//public class TelegramStorageService implements StorageService {
//
//	private static final Logger log = LoggerFactory.getLogger(TelegramStorageService.class);
//
//	private final TelegramStorageProperties props;
//	private final RestTemplate restTemplate;
//
//	public TelegramStorageService(TelegramStorageProperties props) {
//		this.props = props;
//		this.restTemplate = new RestTemplate();
//	}
//
//	public boolean isConfigured() {
//
//		boolean configured = props != null
//				&& props.getBotToken() != null
//				&& !props.getBotToken().isBlank()
//				&& props.getChatId() != null
//				&& !props.getChatId().isBlank();
//
//		log.info("Telegram Storage Configured = {}", configured);
//
//		return configured;
//	}
//
//	@Override
//	public String upload(MultipartFile file) {
//
//		if (!isConfigured()) {
//			throw new StorageException(
//					"Telegram storage is not configured.");
//		}
//
//		try {
//
//			String url = "https://api.telegram.org/bot"
//					+ props.getBotToken()
//					+ "/sendDocument";
//
//			HttpHeaders headers = new HttpHeaders();
//			headers.setContentType(MediaType.MULTIPART_FORM_DATA);
//
//			LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
//
//			body.add("chat_id", props.getChatId());
//
//			body.add("document",
//					new ByteArrayResource(file.getBytes()) {
//
//						@Override
//						public String getFilename() {
//							return file.getOriginalFilename();
//						}
//					});
//
//			HttpEntity<LinkedMultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
//
//			log.info("Uploading file '{}' ({} bytes) to Telegram",
//					file.getOriginalFilename(),
//					file.getSize());
//
//			ResponseEntity<Map> response = restTemplate.postForEntity(
//					url,
//					request,
//					Map.class);
//
//			if (!response.getStatusCode().is2xxSuccessful()) {
//
//				throw new StorageException(
//						"Telegram HTTP Error : "
//								+ response.getStatusCode());
//			}
//
//			Map<?, ?> bodyMap = response.getBody();
//
//			if (bodyMap == null) {
//				throw new StorageException(
//						"Telegram returned empty response.");
//			}
//
//			Boolean ok = (Boolean) bodyMap.get("ok");
//
//			if (ok == null || !ok) {
//
//				throw new StorageException(
//						"Telegram Error : "
//								+ bodyMap);
//			}
//
//			Map<?, ?> result = (Map<?, ?>) bodyMap.get("result");
//
//			if (result == null) {
//
//				throw new StorageException(
//						"Telegram result missing.");
//			}
//
//			Map<?, ?> document = (Map<?, ?>) result.get("document");
//
//			if (document == null) {
//
//				throw new StorageException(
//						"Telegram document missing.");
//			}
//
//			String fileId = (String) document.get("file_id");
//
//			if (fileId == null || fileId.isBlank()) {
//
//				throw new StorageException(
//						"Telegram file_id missing.");
//			}
//
//			log.info("Telegram Upload Successful. fileId={}",
//					fileId);
//
//			return fileId;
//
//		} catch (RestClientResponseException ex) {
//
//			log.error("Telegram API Error : {} {}",
//					ex.getStatusCode(),
//					ex.getResponseBodyAsString(),
//					ex);
//
//			throw new StorageException(
//					"Telegram API Error : "
//							+ ex.getResponseBodyAsString());
//
//		} catch (IOException ex) {
//
//			log.error("Unable to read multipart file.", ex);
//
//			throw new StorageException(
//					"Unable to read upload file.");
//
//		} catch (StorageException ex) {
//
//			log.error("Storage Error", ex);
//
//			throw ex;
//
//		} catch (Exception ex) {
//
//			log.error("Unexpected Telegram Upload Error", ex);
//
//			throw new StorageException(
//					"Telegram upload failed : "
//							+ ex.getMessage());
//		}
//	}
//
//	@Override
//	public byte[] download(String fileId) {
//
//		if (!isConfigured()) {
//			throw new StorageException(
//					"Telegram storage is not configured.");
//		}
//
//		try {
//
//			String getFileUrl = "https://api.telegram.org/bot"
//					+ props.getBotToken()
//					+ "/getFile?file_id="
//					+ fileId;
//
//			Map<?, ?> response = restTemplate.getForObject(
//					getFileUrl,
//					Map.class);
//
//			if (response == null
//					|| !Boolean.TRUE.equals(response.get("ok"))) {
//
//				throw new StorageException(
//						"Unable to obtain Telegram file.");
//			}
//
//			Map<?, ?> result = (Map<?, ?>) response.get("result");
//
//			String filePath = (String) result.get("file_path");
//
//			if (filePath == null) {
//
//				throw new StorageException(
//						"Telegram file path missing.");
//			}
//
//			String downloadUrl = "https://api.telegram.org/file/bot"
//					+ props.getBotToken()
//					+ "/"
//					+ filePath;
//
//			return restTemplate.getForObject(
//					downloadUrl,
//					byte[].class);
//
//		} catch (Exception ex) {
//
//			log.error("Telegram Download Failed", ex);
//
//			throw new StorageException(
//					"Telegram download failed : "
//							+ ex.getMessage());
//		}
//	}
//}

package com.app.storage.service;

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

import jakarta.annotation.PostConstruct;

@Service
public class TelegramStorageService implements StorageService {

	private static final Logger log = LoggerFactory.getLogger(TelegramStorageService.class);

	private static final String CONNECTION_NAME = "storage";

	private final TelegramStorageProperties props;
	private final TelegramClient telegramClient;
	private final TelegramConnectionManager connectionManager;

	public TelegramStorageService(TelegramStorageProperties props, TelegramClient telegramClient,
			TelegramConnectionManager connectionManager) {

		this.props = props;
		this.telegramClient = telegramClient;
		this.connectionManager = connectionManager;
	}

	@PostConstruct
	public void initialize() {

		if (props == null) {

			log.warn("Telegram storage properties are not available.");

			return;
		}

		connectionManager.register(CONNECTION_NAME, props.getBotToken(), props.getChatId());
	}

	/**
	 * Returns cached connection state.
	 *
	 * No Telegram API call is performed here.
	 */
	public boolean isConfigured() {

		return connectionManager.isConnected(CONNECTION_NAME);
	}

	@Override
	public String upload(MultipartFile file) {

		if (file == null) {

			throw new StorageException("Upload file cannot be null.");
		}

		TelegramConnection connection = connectionManager.getConnection(CONNECTION_NAME);

		if (connection == null) {

			throw new StorageException("Telegram storage is unavailable.");
		}

		try {

			log.info("Uploading file '{}' ({} bytes) to Telegram", file.getOriginalFilename(), file.getSize());

			String fileId = telegramClient.sendDocument(connection, file);

			log.info("Telegram Upload Successful. fileId={}", fileId);

			return fileId;

		} catch (Exception ex) {

			connectionManager.markDisconnected(CONNECTION_NAME);

			log.error("Telegram upload failed.", ex);

			throw new StorageException("Telegram upload failed : " + ex.getMessage());
		}
	}

	// 👇 NEW OVERLOADED METHOD
	@Override
	public String upload(byte[] data, String fileName) {

		if (data == null || data.length == 0) {

			throw new StorageException("Upload data cannot be null or empty.");
		}

		TelegramConnection connection = connectionManager.getConnection(CONNECTION_NAME);

		if (connection == null) {

			throw new StorageException("Telegram storage is unavailable.");
		}

		try {

			log.info("Uploading file '{}' ({} bytes) to Telegram", fileName, data.length);

			MultipartFile file = new ByteArrayMultipartFile(fileName, fileName, "application/octet-stream", data);

			String fileId = telegramClient.sendDocument(connection, file);

			log.info("Telegram Upload Successful. fileId={}", fileId);

			return fileId;

		} catch (Exception ex) {

			connectionManager.markDisconnected(CONNECTION_NAME);

			log.error("Telegram upload failed.", ex);

			throw new StorageException("Telegram upload failed : " + ex.getMessage());
		}
	}

	@Override
	public byte[] download(String fileId) {

		if (fileId == null || fileId.isBlank()) {

			throw new StorageException("Telegram file ID cannot be empty.");
		}

		TelegramConnection connection = connectionManager.getConnection(CONNECTION_NAME);

		if (connection == null) {

			throw new StorageException("Telegram storage is unavailable.");
		}

		try {

			byte[] data = telegramClient.download(connection, fileId);

			if (data == null) {

				throw new StorageException("Telegram returned empty file data.");
			}

			return data;

		} catch (StorageException ex) {

			throw ex;

		} catch (Exception ex) {

			connectionManager.markDisconnected(CONNECTION_NAME);

			log.error("Telegram Download Failed", ex);

			throw new StorageException("Telegram download failed : " + ex.getMessage());
		}
	}
}