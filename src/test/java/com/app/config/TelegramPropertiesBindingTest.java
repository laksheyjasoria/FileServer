package com.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class TelegramPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfig.class)
                    .withPropertyValues(
                            "app.telegram.storage.bot-token=storage-bot-token",
                            "app.telegram.storage.chat-id=123456789",
                            "telegram.logger.enabled=true",
                            "telegram.logger.bot-token=logger-bot-token",
                            "telegram.logger.chat-id=987654321");

    @Test
    void shouldBindStorageAndLoggerTelegramPropertiesFromTheirOwnPrefixes() {

        contextRunner.run(context -> {

            assertTrue(
                    context.getBean(
                            TelegramStorageProperties.class) != null);

            assertTrue(
                    context.getBean(
                            TelegramLoggerProperties.class) != null);

            TelegramStorageProperties storage =
                    context.getBean(
                            TelegramStorageProperties.class);

            TelegramLoggerProperties logger =
                    context.getBean(
                            TelegramLoggerProperties.class);

            assertEquals(
                    "storage-bot-token",
                    storage.getBotToken());

            assertEquals(
                    "123456789",
                    storage.getChatId());

            assertTrue(
                    logger.isEnabled());

            assertEquals(
                    "logger-bot-token",
                    logger.getBotToken());

            assertEquals(
                    "987654321",
                    logger.getChatId());
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            TelegramStorageProperties.class,
            TelegramLoggerProperties.class
    })
    static class TestConfig {
    }
}