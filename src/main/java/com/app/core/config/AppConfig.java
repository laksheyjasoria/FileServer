package com.app.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.app.config.LoggerLevelProperties;
import com.app.config.MasterKeyProperties;
import com.app.config.TelegramLoggerProperties;

@Configuration
@EnableConfigurationProperties({ TelegramLoggerProperties.class, LoggerLevelProperties.class,
		MasterKeyProperties.class })
public class AppConfig {
}