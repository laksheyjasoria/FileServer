package com.app.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

/**
 * Validates essential configuration after ConfigData imports (including .env)
 * and before Spring creates the application context. Secret values are never logged.
 */
public class EnvironmentValidation implements EnvironmentPostProcessor, Ordered {

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {

        System.out.println("========== FILESERVER ENVIRONMENT VALIDATION ==========");
        List<String> failures = new ArrayList<>();

        boolean envLoaded = environment.getPropertySources().stream()
                .anyMatch(source -> source.getName().toLowerCase().contains(".env"));
        report(".env property source", envLoaded);
        if (!envLoaded) failures.add(".env file was not loaded");

        require(environment, failures, "spring.application.name", "APP_NAME");
        require(environment, failures, "spring.datasource.url", "SPRING_DATASOURCE_URL");
        require(environment, failures, "spring.datasource.username", "SPRING_DATASOURCE_USERNAME");
        require(environment, failures, "spring.datasource.password", "SPRING_DATASOURCE_PASSWORD");

        String jwt = environment.getProperty("app.security.jwt.secret");
        boolean jwtValid = jwt != null
                && jwt.getBytes(StandardCharsets.UTF_8).length >= 32;
        report("JWT secret (minimum 256 bits)", jwtValid);
        if (!jwtValid) failures.add("APP_SECURITY_JWT_SECRET must contain at least 32 UTF-8 bytes");

        require(environment, failures, "app.security.master.key", "APP_SECURITY_MASTER_KEY");
        require(environment, failures, "app.admin.password", "APP_ADMIN_PASSWORD");

        if ("telegram".equalsIgnoreCase(
                environment.getProperty("app.storage.provider", "telegram"))) {
            require(environment, failures, "app.telegram.storage.bot-token",
                    "TELEGRAM_STORAGE_BOT_TOKEN");
            require(environment, failures, "app.telegram.storage.chat-id",
                    "TELEGRAM_STORAGE_CHAT_ID");
        }

        if (environment.getProperty("telegram.logger.enabled", Boolean.class, false)) {
            require(environment, failures, "telegram.logger.bot-token", "TELEGRAM_LOGGER_BOT_TOKEN");
            require(environment, failures, "telegram.logger.chat-id", "TELEGRAM_LOGGER_CHAT_ID");
        } else {
            report("Telegram logger credentials (feature disabled)", true);
        }

        if (environment.getProperty("app.email.enabled", Boolean.class, false)) {
            require(environment, failures, "spring.mail.username", "SPRING_MAIL_USERNAME");
            require(environment, failures, "spring.mail.password", "SPRING_MAIL_PASSWORD");
        } else {
            report("SMTP credentials (email disabled)", true);
        }

        String googleId = environment.getProperty(
                "spring.security.oauth2.client.registration.google.client-id");
        String googleSecret = environment.getProperty(
                "spring.security.oauth2.client.registration.google.client-secret");
        boolean googleComplete = hasText(googleId) && hasText(googleSecret);
        report("Google OAuth credentials", googleComplete);
        if ((hasText(googleId) || hasText(googleSecret)) && !googleComplete) {
            failures.add("Set both GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET, or leave both blank");
        }

        if (failures.isEmpty()) {
            System.out.println("RESULT: PASS - required configuration is present.");
        } else {
            System.err.println("RESULT: FAIL - " + failures.size() + " configuration check(s) failed:");
            failures.forEach(failure -> System.err.println("  - " + failure));
            throw new IllegalStateException(
                    "FileServer configuration validation failed. Secret values were not logged.");
        }
        System.out.println("========================================================");
    }

    private void require(ConfigurableEnvironment env, List<String> failures,
                         String property, String variable) {
        boolean configured = hasText(env.getProperty(property));
        report(variable, configured);
        if (!configured) failures.add(variable + " is missing or blank");
    }

    private void report(String label, boolean passed) {
        System.out.println("[" + (passed ? "PASS" : "FAIL") + "] " + label);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
