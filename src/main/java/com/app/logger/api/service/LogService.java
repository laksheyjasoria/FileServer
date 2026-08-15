package com.app.logger.api.service;

import com.app.logger.api.entity.LoggerEntity;
import com.app.logger.service.TelegramLoggerService;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class LogService {

	private final LoggerService loggerService;
	private final TelegramLoggerService telegram;

	public LogService(LoggerService loggerService, TelegramLoggerService telegram) {
		this.loggerService = loggerService;
		this.telegram = telegram;
	}

	// ================= PUBLIC LOG =================
	@Async
	public void log(String loggerId, String level, String message) {

		LoggerEntity logger = loggerService.get(loggerId);

		// Normalize level
		level = level.toUpperCase();

		// Check enable flags
		if ("INFO".equals(level) && !logger.isInfoEnabled())
			return;
		if ("WARN".equals(level) && !logger.isWarnEnabled())
			return;
		if ("DEBUG".equals(level) && !logger.isDebugEnabled())
			return; // ✅ debug check

		// If level is not recognized, default to INFO (optional)
		// But we'll just ignore unknown levels (or treat as INFO)
		if (!"INFO".equals(level) && !"WARN".equals(level) && !"DEBUG".equals(level)) {
			// Optionally log a warning? For now, just ignore.
			return;
		}

		// Send to Telegram
		telegram.send(format(level, message, logger.getName()));
	}

	// ================= ERROR (ALWAYS LOG) =================
	@Async
	public void error(String loggerId, String message) {

		LoggerEntity logger = loggerService.get(loggerId);

		telegram.send(format("ERROR", message, logger.getName()));
	}

	// ================= FORMAT =================
	private String format(String level, String msg, String name) {

		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

		StringBuilder sb = new StringBuilder();

		sb.append("<b>🚀 ").append(name).append("</b>\n\n").append("<b>Time:</b> ").append(timestamp).append("\n")
				.append("<b>Level:</b> ").append(level).append("\n").append("<b>Message:</b> ").append(msg);

		return sb.toString();
	}
}