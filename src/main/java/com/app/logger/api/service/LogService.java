package com.app.logger.api.service;

import com.app.logger.api.entity.LoggerEntity;
import com.app.logger.service.TelegramLoggerService;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class LogService {

    private final LoggerService loggerService;
    private final TelegramLoggerService telegram;

    public LogService(LoggerService loggerService,
                      TelegramLoggerService telegram) {
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
        if ("INFO".equals(level) && !logger.isInfoEnabled()) return;
        if ("WARN".equals(level) && !logger.isWarnEnabled()) return;

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

        StringBuilder sb = new StringBuilder();

        sb.append("<b>🚀 ").append(name).append("</b>\n\n")
          .append("<b>Level:</b> ").append(level).append("\n")
          .append("<b>Message:</b> ").append(msg);

        return sb.toString();
    }
}