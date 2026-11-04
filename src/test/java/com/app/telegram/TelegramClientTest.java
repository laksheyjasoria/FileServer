package com.app.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class TelegramClientTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private TelegramClient telegramClient;
    private TelegramConnection connection;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        telegramClient = new TelegramClient(restTemplate);
        connection = new TelegramConnection("123456:TEST_TOKEN", "-100123456789");
    }

    @Test
    void sendDocumentExtractsDocumentFileId() {
        server.expect(requestTo("https://api.telegram.org/bot123456:TEST_TOKEN/sendDocument"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"ok":true,"result":{"message_id":1,"document":{"file_id":"telegram-document-id"}}}
                        """, MediaType.APPLICATION_JSON));

        String fileId = telegramClient.sendDocument(
                connection, new MockMultipartFile("file", "chunk.bin", "application/octet-stream", new byte[] {1, 2, 3}));

        assertThat(fileId).isEqualTo("telegram-document-id");
        server.verify();
    }

    @Test
    void sendDocumentAcceptsVideoMediaReturnedByCompatibleBotApi() {
        server.expect(requestTo("https://api.telegram.org/bot123456:TEST_TOKEN/sendDocument"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"ok":true,"result":{"message_id":2,"video":{"file_id":"telegram-video-id"}}}
                        """, MediaType.APPLICATION_JSON));

        String fileId = telegramClient.sendDocument(
                connection, new MockMultipartFile("file", "chunk.mp4", "video/mp4", new byte[] {1, 2, 3}));

        assertThat(fileId).isEqualTo("telegram-video-id");
        server.verify();
    }

    @Test
    void sendDocumentReportsUnexpectedResultWithoutTreatingItAsConnectionFailure() {
        server.expect(requestTo("https://api.telegram.org/bot123456:TEST_TOKEN/sendDocument"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"ok":true,"result":{"message_id":3,"chat":{"id":123}}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> telegramClient.sendDocument(
                connection, new MockMultipartFile("file", "chunk.bin", "application/octet-stream", new byte[] {1})))
                .isInstanceOf(TelegramClient.TelegramApiException.class)
                .hasMessageContaining("no file_id");

        server.verify();
    }

    @Test
    void sendDocumentPreservesRateLimitRetryAfter() {
        server.expect(requestTo("https://api.telegram.org/bot123456:TEST_TOKEN/sendDocument"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"ok":false,"error_code":429,"description":"Too Many Requests","parameters":{"retry_after":17}}
                                """));

        assertThatThrownBy(() -> telegramClient.sendDocument(
                connection, new MockMultipartFile("file", "chunk.bin", "application/octet-stream", new byte[] {1})))
                .isInstanceOf(TelegramClient.TelegramApiException.class)
                .satisfies(error -> {
                    TelegramClient.TelegramApiException apiError = (TelegramClient.TelegramApiException) error;
                    assertThat(apiError.getHttpStatus()).isEqualTo(429);
                    assertThat(apiError.getTelegramErrorCode()).isEqualTo(429);
                    assertThat(apiError.getRetryAfterSeconds()).isEqualTo(17);
                });

        server.verify();
    }
}