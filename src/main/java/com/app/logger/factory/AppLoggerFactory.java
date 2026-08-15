package com.app.logger.factory;

import org.springframework.stereotype.Component;

import com.app.logger.AppLogger;
import com.app.logger.core.InternalLoggerService;

@Component
public class AppLoggerFactory {

	private final InternalLoggerService internalLogger;

	public AppLoggerFactory(InternalLoggerService internalLogger) {
		this.internalLogger = internalLogger;
	}

	public AppLogger getLogger(Class<?> clazz) {
		return new AppLogger(clazz.getSimpleName(), internalLogger);
	}

	public AppLogger getLogger(String name) {
		return new AppLogger(name, internalLogger);
	}
}