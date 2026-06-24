package com.app.logger.core;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.app.config.AppProperties;
import com.app.config.LoggerLevelProperties;
import com.app.logger.service.TelegramLoggerService;

@Service
public class InternalLoggerService {

	private final TelegramLoggerService telegram;
	private final LoggerLevelProperties level;
	private final AppProperties app;

	public InternalLoggerService(
			TelegramLoggerService telegram,
			LoggerLevelProperties level,
			AppProperties app) {

		this.telegram = telegram;
		this.level = level;
		this.app = app;
	}

	@Async
	public void info(String msg) {

		System.out.println("[INFO] " + msg);

		if (level.isInfo()) {
			telegram.send(format("INFO", msg, null));
		}
	}

	@Async
	public void warn(String msg) {

		System.out.println("[WARN] " + msg);

		if (level.isWarn()) {
			telegram.send(format("WARN", msg, null));
		}
	}

	@Async
	public void error(String msg, Throwable ex) {

		System.err.println("[ERROR] " + msg);

		if (level.isError()) {
			telegram.send(format("ERROR", msg, ex));
		}
	}

	private String format(
			String levelName,
			String msg,
			Throwable ex) {

		StringBuilder sb = new StringBuilder();

		sb.append("<b>🔥 ")
		  .append(app.getName())
		  .append("</b>\n\n");

		sb.append("Level: ")
		  .append(levelName)
		  .append("\n");

		sb.append("Message: ")
		  .append(msg);

		if (ex != null) {

			sb.append("\n\nException: ")
			  .append(ex.getClass().getSimpleName());

			if (ex.getMessage() != null) {
				sb.append("\n")
				  .append(ex.getMessage());
			}
		}

		return sb.toString();
	}
}