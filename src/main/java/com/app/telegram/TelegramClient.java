package com.app.telegram;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.AbstractResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramClient.class);
    private static final String BOT_API = "https://api.telegram.org/bot";
    private static final String FILE_API = "https://api.telegram.org/file/bot";
    private static final long FILE_PATH_CACHE_TTL_MS = 50L * 60L * 1000L;

    private static final List<String> MEDIA_FIELDS = List.of("document", "video", "audio", "animation", "voice", "video_note");

    private final ConcurrentMap<FilePathCacheKey, CachedFilePath> filePathCache = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private record FilePathCacheKey(String botToken, String fileId) {}
    private record CachedFilePath(String path, long expiresAt) {}

    public TelegramClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000);
        factory.setReadTimeout(120_000);
        factory.setBufferRequestBody(false);
        this.restTemplate = new RestTemplate(factory);
    }

    TelegramClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean validate(String botToken, String chatId) {
        if (isBlank(botToken) || isBlank(chatId)) {
            return false;
        }
        try {
            ResponseEntity<Map> me = restTemplate.getForEntity(botApiUrl(botToken, "getMe"), Map.class);
            if (!isSuccessful(me)) {
                return false;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("chat_id", chatId);
            ResponseEntity<Map> chat = restTemplate.postForEntity(
                    botApiUrl(botToken, "getChat"), new HttpEntity<>(form, headers), Map.class);
            return isSuccessful(chat);
        } catch (Exception ex) {
            // Never print request URLs here: Telegram bot tokens are part of the URL.
            log.warn("Telegram connection validation failed: {}", safeExceptionMessage(ex));
            return false;
        }
    }

    public void sendMessage(TelegramConnection connection, String message) {
        requireConnection(connection);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("chat_id", connection.getChatId());
        form.add("text", message);
        form.add("parse_mode", "HTML");
        ResponseEntity<Map> response = restTemplate.postForEntity(
                botApiUrl(connection.getBotToken(), "sendMessage"), new HttpEntity<>(form, headers), Map.class);
        requireOk(response, "sendMessage");
    }

    public String sendDocument(TelegramConnection connection, MultipartFile file) {
        requireConnection(connection);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Telegram upload file cannot be empty.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("chat_id", connection.getChatId());
        body.add("document", new AbstractResource() {
            @Override
            public InputStream getInputStream() throws java.io.IOException {
                return file.getInputStream();
            }

            @Override
            public long contentLength() {
                return file.getSize();
            }

            @Override
            public String getFilename() {
                String filename = file.getOriginalFilename();
                return isBlank(filename) ? "upload.bin" : filename;
            }

            @Override
            public String getDescription() {
                return "Telegram multipart upload";
            }
        });

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    botApiUrl(connection.getBotToken(), "sendDocument"),
                    new HttpEntity<>(body, headers),
                    Map.class);
            Map<?, ?> responseBody = response == null ? null : response.getBody();
            if (responseBody == null) {
                throw new TelegramApiException("Telegram returned an empty sendDocument response.", null, null);
            }
            if (!Boolean.TRUE.equals(responseBody.get("ok"))) {
                throw apiException(responseBody, response.getStatusCode().value(), "sendDocument");
            }

            Object resultValue = responseBody.get("result");
            if (!(resultValue instanceof Map<?, ?> result)) {
                throw unexpectedResponse("sendDocument", "result is missing or is not an object", responseBody);
            }

            String fileId = extractUploadedFileId(result);
            if (isBlank(fileId)) {
                // Telegram normally returns Message.document.file_id. Some compatible
                // Bot API implementations can return a different media field.
                throw unexpectedResponse("sendDocument",
                        "no file_id found in the returned message media", result);
            }
            return fileId;
        } catch (RestClientResponseException ex) {
            throw apiException(ex, "sendDocument");
        }
    }

    private String extractUploadedFileId(Map<?, ?> message) {
        for (String field : MEDIA_FIELDS) {
            Object mediaValue = message.get(field);
            if (mediaValue instanceof Map<?, ?> media) {
                Object fileId = media.get("file_id");
                if (fileId instanceof String value && !value.isBlank()) {
                    return value;
                }
            }
            if ("photo".equals(field) && mediaValue instanceof List<?> photos) {
                for (int i = photos.size() - 1; i >= 0; i--) {
                    Object photoValue = photos.get(i);
                    if (photoValue instanceof Map<?, ?> photo
                            && photo.get("file_id") instanceof String value && !value.isBlank()) {
                        return value;
                    }
                }
            }
        }

        Object photoValue = message.get("photo");
        if (photoValue instanceof List<?> photos) {
            for (int i = photos.size() - 1; i >= 0; i--) {
                Object item = photos.get(i);
                if (item instanceof Map<?, ?> photo
                        && photo.get("file_id") instanceof String value && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    public byte[] download(TelegramConnection connection, String fileId) {
        requireConnection(connection);
        String filePath = resolveFilePath(connection, fileId);
        byte[] data = restTemplate.getForObject(downloadUrl(filePath, connection), byte[].class);
        if (data == null) {
            throw new IllegalStateException("Telegram returned an empty file download.");
        }
        return data;
    }

    public <T> T executeFileDownloadRange(
            TelegramConnection connection,
            String fileId,
            long start,
            long length,
            org.springframework.web.client.ResponseExtractor<T> extractor) {
        requireConnection(connection);
        if (start < 0 || length <= 0) {
            throw new IllegalArgumentException("File range start must be non-negative and length must be positive.");
        }
        long end;
        try {
            end = Math.addExact(start, length - 1);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Requested file range is too large.", ex);
        }

        FilePathCacheKey key = new FilePathCacheKey(connection.getBotToken(), fileId);
        String filePath = resolveFilePath(connection, fileId);
        try {
            return executeRange(downloadUrl(filePath, connection), start, end, extractor);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound stalePath) {
            filePathCache.remove(key);
            String refreshedPath = resolveFilePath(connection, fileId);
            return executeRange(downloadUrl(refreshedPath, connection), start, end, extractor);
        }
    }

    private <T> T executeRange(
            String url,
            long start,
            long end,
            org.springframework.web.client.ResponseExtractor<T> extractor) {
        // The response extractor must consume the response body before returning.
        // Never return the InputStream to a StreamingResponseBody/async servlet thread.
        return restTemplate.execute(url, HttpMethod.GET,
                request -> request.getHeaders().set(HttpHeaders.RANGE, "bytes=" + start + "-" + end),
                extractor);
    }

    public <T> T executeFileDownload(
            TelegramConnection connection,
            String fileId,
            HttpMethod method,
            org.springframework.web.client.ResponseExtractor<T> extractor) {
        requireConnection(connection);
        String filePath = resolveFilePath(connection, fileId);
        return restTemplate.execute(downloadUrl(filePath, connection), method, null, extractor);
    }

    private String resolveFilePath(TelegramConnection connection, String fileId) {
        if (isBlank(fileId)) {
            throw new IllegalArgumentException("Telegram file ID is required.");
        }
        FilePathCacheKey key = new FilePathCacheKey(connection.getBotToken(), fileId);
        long now = System.currentTimeMillis();
        CachedFilePath cached = filePathCache.get(key);
        if (cached != null) {
            if (cached.expiresAt() > now) {
                return cached.path();
            }
            filePathCache.remove(key, cached);
        }

        String url = botApiUrl(connection.getBotToken(), "getFile")
                + "?file_id=" + UriUtils.encodeQueryParam(fileId, StandardCharsets.UTF_8);
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<?, ?> body = response == null ? null : response.getBody();
            if (body == null || !Boolean.TRUE.equals(body.get("ok"))) {
                throw body == null
                        ? new IllegalStateException("Telegram returned an empty getFile response.")
                        : apiException(body, response.getStatusCode().value(), "getFile");
            }
            Object resultValue = body.get("result");
            if (!(resultValue instanceof Map<?, ?> result)
                    || !(result.get("file_path") instanceof String path)
                    || path.isBlank()) {
                throw unexpectedResponse("getFile", "file_path is missing", body);
            }
            filePathCache.put(key, new CachedFilePath(path, now + FILE_PATH_CACHE_TTL_MS));
            return path;
        } catch (RestClientResponseException ex) {
            throw apiException(ex, "getFile");
        }
    }

    private String downloadUrl(String filePath, TelegramConnection connection) {
        return FILE_API + connection.getBotToken() + "/" + filePath;
    }

    private String botApiUrl(String token, String method) {
        return BOT_API + token + "/" + method;
    }

    private void requireConnection(TelegramConnection connection) {
        if (connection == null || isBlank(connection.getBotToken()) || isBlank(connection.getChatId())) {
            throw new IllegalStateException("Telegram connection is not configured.");
        }
    }

    private void requireOk(ResponseEntity<Map> response, String method) {
        Map<?, ?> body = response == null ? null : response.getBody();
        if (body == null) {
            throw new TelegramApiException("Telegram " + method + " returned an empty response.", null, null);
        }
        if (!Boolean.TRUE.equals(body.get("ok"))) {
            throw apiException(body, response.getStatusCode().value(), method);
        }
    }

    private TelegramApiException apiException(RestClientResponseException ex, String method) {
        Map<?, ?> body = parseJsonObject(ex.getResponseBodyAsString());
        Integer errorCode = number(body == null ? null : body.get("error_code"));
        String description = string(body == null ? null : body.get("description"));
        Integer retryAfter = retryAfterSeconds(body);
        String message = "Telegram " + method + " failed (HTTP " + ex.getStatusCode().value()
                + (errorCode == null ? "" : ", error " + errorCode)
                + (isBlank(description) ? ")." : "): " + description)
                + (retryAfter == null ? "" : " Retry after " + retryAfter + " seconds.");
        return new TelegramApiException(message, ex.getStatusCode().value(), errorCode, retryAfter);
    }

    private TelegramApiException apiException(Map<?, ?> body, int httpStatus, String method) {
        Integer errorCode = number(body.get("error_code"));
        String description = string(body.get("description"));
        Integer retryAfter = retryAfterSeconds(body);
        String message = "Telegram " + method + " failed (HTTP " + httpStatus
                + (errorCode == null ? "" : ", error " + errorCode)
                + (isBlank(description) ? ")." : "): " + description)
                + (retryAfter == null ? "" : " Retry after " + retryAfter + " seconds.");
        return new TelegramApiException(message, httpStatus, errorCode, retryAfter);
    }

    private TelegramApiException unexpectedResponse(String method, String reason, Map<?, ?> response) {
        // Only log structural information. Never log the complete response, token or file IDs.
        log.error("Unexpected Telegram {} response: {}; response keys={}",
                method, reason, response == null ? "[]" : response.keySet());
        return new TelegramApiException("Telegram " + method + " returned an unexpected response: " + reason + ".",
                null, null);
    }

    private Map<?, ?> parseJsonObject(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isSuccessful(ResponseEntity<Map> response) {
        return response != null && response.getStatusCode().is2xxSuccessful()
                && response.getBody() != null && Boolean.TRUE.equals(response.getBody().get("ok"));
    }

    private Integer retryAfterSeconds(Map<?, ?> body) {
        if (body == null || !(body.get("parameters") instanceof Map<?, ?> parameters)) {
            return null;
        }
        return number(parameters.get("retry_after"));
    }

    private static Integer number(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static String string(Object value) {
        return value instanceof String text ? text : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safeExceptionMessage(Exception ex) {
        // Exception messages from HTTP clients may contain the request URL, which
        // includes the bot token. Log only the exception type during validation.
        return ex.getClass().getSimpleName();
    }

    public static final class TelegramApiException extends RuntimeException {
        private final Integer httpStatus;
        private final Integer telegramErrorCode;
        private final Integer retryAfterSeconds;

        public TelegramApiException(String message, Integer httpStatus, Integer telegramErrorCode) {
            this(message, httpStatus, telegramErrorCode, null);
        }

        public TelegramApiException(
                String message, Integer httpStatus, Integer telegramErrorCode, Integer retryAfterSeconds) {
            super(message);
            this.httpStatus = httpStatus;
            this.telegramErrorCode = telegramErrorCode;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public Integer getHttpStatus() {
            return httpStatus;
        }

        public Integer getTelegramErrorCode() {
            return telegramErrorCode;
        }

        public Integer getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}