package com.app.config;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

/**
 * Reports whether the external .env property source was loaded and whether
 * the application's essential configuration values are present.
 *
 * This class deliberately logs only presence/absence, never secret values.
 */
@Component
public class EnvironmentValidation implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(EnvironmentValidation.class);

    private final ConfigurableEnvironment environment;

    public EnvironmentValidation(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("========== ENVIRONMENT VALIDATION ==========");

        boolean envFileLoaded = false;
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (propertySource.getName().toLowerCase().contains(".env")) {
                envFileLoaded = true;
                break;
            }
        }

        log.info(".env property source: {}",
                envFileLoaded ? "LOADED" : "NOT DETECTED");

        logPresence("APP_NAME", "APP_NAME");
        logPresence("SPRING_DATASOURCE_URL", "spring.datasource.url");
        logPresence("SPRING_DATASOURCE_USERNAME", "spring.datasource.username");
        logPresence("SPRING_DATASOURCE_PASSWORD", "spring.datasource.password");
        logPresence("APP_SECURITY_JWT_SECRET", "app.security.jwt.secret");
        logPresence("APP_SECURITY_MASTER_KEY", "app.security.master.key");

        // Support both the current application.yml nesting and the alternate
        // top-level telegram.storage layout used in some local configurations.
        logPresence("TELEGRAM_STORAGE_BOT_TOKEN",
                firstConfigured("app.telegram.storage.bot-token",
                        "telegram.storage.bot-token"));
        logPresence("TELEGRAM_STORAGE_CHAT_ID",
                firstConfigured("app.telegram.storage.chat-id",
                        "telegram.storage.chat-id"));

        String jwtSecret = environment.getProperty("app.security.jwt.secret", "");
        log.info("JWT secret length: {} bytes",
                jwtSecret.getBytes(StandardCharsets.UTF_8).length);

        log.info("Active profiles: {}", Arrays.toString(environment.getActiveProfiles()));
        log.info("========== ENVIRONMENT VALIDATION COMPLETE ==========");
    }

    private void logPresence(String label, String propertyName) {
        String value = environment.getProperty(propertyName);
        log.info("{}: {}", label,
                value != null && !value.isBlank() ? "CONFIGURED" : "MISSING");
    }

    private String firstConfigured(String... propertyNames) {
        for (String propertyName : propertyNames) {
            String value = environment.getProperty(propertyName);
            if (value != null && !value.isBlank()) {
                return propertyName;
            }
        }
        return propertyNames[0];
    }
}
