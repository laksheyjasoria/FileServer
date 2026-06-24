package com.app.logger.factory;

import org.springframework.stereotype.Component;

import com.app.config.AppProperties;
import com.app.logger.AppLogger;
import com.app.logger.core.InternalLoggerService;

@Component
public class AppLoggerFactory {

	private final InternalLoggerService internal;
	private final AppProperties appProperties;

	public AppLoggerFactory(
			InternalLoggerService internal,
			AppProperties appProperties) {

		this.internal = internal;
		this.appProperties = appProperties;
	}

	public AppLogger getLogger(Class<?> clazz) {

		return new AppLogger(
				appProperties.getName(),
				clazz,
				internal);
	}
}