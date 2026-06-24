package com.app.logger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.app.logger.core.InternalLoggerService;

public class AppLogger {

	private final Logger logger;
	private final InternalLoggerService internal;
	private final String service;

	public AppLogger(
			String applicationName,
			Class<?> clazz,
			InternalLoggerService internal) {

		this.logger = LoggerFactory.getLogger(applicationName);
		this.internal = internal;
		this.service = clazz.getSimpleName();
	}

	public void info(String msg, Object... args) {

		String f = format(msg, args);

		logger.info("service={} msg={}", service, f);

		if (internal != null) {
			internal.info("service=" + service + " msg=" + f);
		}
	}

	public void warn(String msg, Object... args) {

		String f = format(msg, args);

		logger.warn("service={} msg={}", service, f);

		if (internal != null) {
			internal.warn("service=" + service + " msg=" + f);
		}
	}

	public void error(String msg, Object... args) {

		String f = format(msg, args);

		logger.error("service={} msg={}", service, f);

		if (internal != null) {
			internal.error("service=" + service + " msg=" + f, null);
		}
	}

	public void error(String msg, Throwable ex, Object... args) {

		String f = format(msg, args);

		logger.error("service={} msg={}", service, f, ex);

		if (internal != null) {
			internal.error("service=" + service + " msg=" + f, ex);
		}
	}

	private String format(String msg, Object... args) {
		return org.slf4j.helpers.MessageFormatter
				.arrayFormat(msg, args)
				.getMessage();
	}
}