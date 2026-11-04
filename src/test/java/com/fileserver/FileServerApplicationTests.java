package com.fileserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import com.app.FileServerApplication;

@SpringBootTest(
        classes = FileServerApplication.class,
        properties = {
                "SPRING_DATASOURCE_PASSWORD=test-database-password",
                "APP_SECURITY_MASTER_KEY=test-master-key-with-at-least-32-chars",
                "APP_SECURITY_JWT_SECRET=test-jwt-signing-secret-with-at-least-32-chars",
                "TELEGRAM_STORAGE_BOT_TOKEN=test-telegram-bot-token",
                "TELEGRAM_STORAGE_CHAT_ID=123456789",
                "APP_ADMIN_PASSWORD=test-only-admin-password",
                "GOOGLE_CLIENT_ID=test-google-client-id",
                "GOOGLE_CLIENT_SECRET=test-google-client-secret"
        })
class FileServerApplicationTests {

	@Test
	void contextLoads() {
	}

}
