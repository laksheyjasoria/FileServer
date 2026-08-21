package com.app.storage.factory;

import org.springframework.stereotype.Component;

import com.app.core.config.AppProperties;
import com.app.core.exception.StorageException;
import com.app.storage.service.LocalFileStorageService;
import com.app.storage.service.StorageService;
import com.app.storage.service.TelegramStorageService;

@Component
public class StorageFactory {

	private final AppProperties appProperties;
	private final TelegramStorageService telegram;
	private final LocalFileStorageService local;

	public StorageFactory(AppProperties appProperties, TelegramStorageService telegram, LocalFileStorageService local) {
		this.appProperties = appProperties;
		this.telegram = telegram;
		this.local = local;
	}

	public StorageService get() {
		String provider = appProperties.getStorage().getProvider();

		if (provider == null || provider.isBlank()) {
			throw new StorageException("Storage provider is not configured in application.yml.");
		}

		switch (provider.toLowerCase()) {
			case "telegram":
				if (!telegram.isConfigured()) {
					throw new StorageException(
							"Telegram storage is not configured. Set TELEGRAM_STORAGE_BOT_TOKEN and TELEGRAM_STORAGE_CHAT_ID.");
				}
				return telegram;
			default:
				throw new StorageException(
						"Unsupported storage provider: " + provider + ". Supported provider: telegram");
		}
	}
}