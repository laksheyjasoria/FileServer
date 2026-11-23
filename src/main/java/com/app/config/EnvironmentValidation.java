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
 * Validates essential configuration after Spring Boot has loaded ConfigData
 * (including .env), but before the application context is created.
 * Secret values are never logged.
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

        boolean envLoaded = false;
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source.getName().toLowerCase().contains(".env")) {
                envLoaded = true;
                break;
            }
        }
        report(".env property source", envLoaded);
        if (!envLoaded) failures.add(".env file was not loaded");

        requireValue(environment, failures, "spring.application.name", "APP_NAME");
        requireValue(environment, failures, "spring.datasource.url", "SPRING_DATASOURCE_URL");
        requireValue(environment, failures, "spring.datasource.username", "SPRING_DATASOURCE_USERNAME");
        requireSecret(environment, failures, "SPRING_DATASOURCE_PASSWORD", "SPRING_DATASOURCE_PASSWORD");

        String jwt = environment.getProperty("APP_SECURITY_JWT_SECRET");
        boolean jwtValid = jwt != null
                && jwt.getBytes(StandardCharsets.UTF_8).length >= 32
                && !isPlaceholder(jwt);
        report("JWT secret (minimum 256 bits)", jwtValid);
        if (!jwtValid) failures.add("APP_SECURITY_JWT_SECRET must be a real secret of at least 32 UTF-8 bytes");

        requireSecret(environment, failures, "APP_SECURITY_MASTER_KEY", "APP_SECURITY_MASTER_KEY");
        requireSecret(environment, failures, "APP_ADMIN_PASSWORD", "APP_ADMIN_PASSWORD");

        if ("telegram".equalsIgnoreCase(
                environment.getProperty("app.storage.provider", "telegram"))) {
            requireSecret(environment, failures, "app.telegram.storage.bot-token",
                    "TELEGRAM_STORAGE_BOT_TOKEN");
            requireSecret(environment, failures, "app.telegram.storage.chat-id",
                    "TELEGRAM_STORAGE_CHAT_ID");
        }

        if (environment.getProperty("telegram.logger.enabled", Boolean.class, false)) {
            requireSecret(environment, failures, "TELEGRAM_LOGGER_BOT_TOKEN", "TELEGRAM_LOGGER_BOT_TOKEN");
            requireSecret(environment, failures, "TELEGRAM_LOGGER_CHAT_ID", "TELEGRAM_LOGGER_CHAT_ID");
        } else {
            report("Telegram logger credentials (feature disabled)", true);
        }

        if (environment.getProperty("app.email.enabled", Boolean.class, false)) {
            requireValue(environment, failures, "spring.mail.host", "SPRING_MAIL_HOST");
            requireValue(environment, failures, "SPRING_MAIL_USERNAME", "SPRING_MAIL_USERNAME");
            requireSecret(environment, failures, "SPRING_MAIL_PASSWORD", "SPRING_MAIL_PASSWORD");
        } else {
            report("SMTP credentials (email disabled)", true);
        }

        String googleId = environment.getProperty(
                "spring.security.oauth2.client.registration.google.client-id");
        String googleSecret = environment.getProperty(
                "spring.security.oauth2.client.registration.google.client-secret");
        boolean googleComplete = hasText(googleId) && hasText(googleSecret);
        boolean googleAbsent = !hasText(googleId) && !hasText(googleSecret);
        report("Google OAuth credentials", googleComplete || googleAbsent);
        if (!googleComplete && !googleAbsent) {
            failures.add("Set both GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET, or leave both blank");
        }

        if (failures.isEmpty()) {
            System.out.println("RESULT: PASS - required configuration is present.");
        } else {
            System.err.println("RESULT: FAIL - " + failures.size() + " configuration check(s) failed:");
            failures.forEach(failure -> System.err.println("  - " + failure));
            throw new IllegalStateException(
                    "FileServer configuration validation failed. Secret values were intentionally hidden.");
        }
        System.out.println("========================================================");
    }

    private void requireValue(ConfigurableEnvironment env, List<String> failures,
                              String property, String variable) {
        boolean configured = hasText(env.getProperty(property));
        report(variable, configured);
        if (!configured) failures.add(variable + " is missing or blank");
    }

    private void requireSecret(ConfigurableEnvironment env, List<String> failures,
                               String property, String variable) {
        String value = env.getProperty(property);
        boolean configured = hasText(value) && !isPlaceholder(value);
        report(variable, configured);
        if (!configured) failures.add(variable + " is missing, blank, or still contains a placeholder");
    }

    private boolean isPlaceholder(String value) {
        String normalized = value.trim().toLowerCase();
        return normalized.equals("change_me")
                || normalized.equals("change-me")
                || normalized.equals("changeme")
                || normalized.equals("your_secret")
                || normalized.equals("your-secret")
                || normalized.startsWith("your_")
                || normalized.startsWith("replace_me");
    }

    private void report(String label, boolean passed) {
        System.out.println("[" + (passed ? "PASS" : "FAIL") + "] " + label);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
