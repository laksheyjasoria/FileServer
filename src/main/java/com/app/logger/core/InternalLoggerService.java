package com.app.logger.core;

import com.app.config.AppProperties;
import com.app.config.LoggerLevelProperties;
import com.app.logger.service.TelegramLoggerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class InternalLoggerService {

    private static final Logger consoleLogger = LoggerFactory.getLogger(InternalLoggerService.class);

    private final TelegramLoggerService telegram;
    private final LoggerLevelProperties levelProps;
    private final AppProperties appProps;

    // ANSI color codes for console
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_CYAN = "\u001B[36m";

    public InternalLoggerService(TelegramLoggerService telegram,
                                 LoggerLevelProperties levelProps,
                                 AppProperties appProps) {
        this.telegram = telegram;
        this.levelProps = levelProps;
        this.appProps = appProps;
    }

    @Async
    public void log(String loggerName, String level, String message, Throwable throwable) {
        // 1. Check if level is globally enabled
        boolean enabled = switch (level.toUpperCase()) {
            case "INFO" -> levelProps.isInfo();
            case "WARN" -> levelProps.isWarn();
            case "ERROR" -> levelProps.isError();
            case "DEBUG" -> levelProps.isDebug();
            default -> false;
        };

        if (!enabled) {
            return;
        }

        // 2. Format the message (includes emoji for Telegram)
        String formatted = format(level, message, throwable, loggerName);

        // 3. Send via TelegramLoggerService
        boolean sent = telegram.send(formatted);

        // 4. If Telegram fails, fallback to console with ANSI colors
        if (!sent) {
            consoleLogger.error("Telegram send failed – logging to console.");
            String colored = formatConsole(level, loggerName, message, throwable);
            System.err.println(colored);
            if (throwable != null) {
                throwable.printStackTrace(System.err);
            }
        }
    }

    private String format(String level, String message, Throwable throwable, String loggerName) {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // Emoji per level
        String emoji = switch (level.toUpperCase()) {
            case "INFO" -> "ℹ️";
            case "WARN" -> "⚠️";
            case "ERROR" -> "❌";
            case "DEBUG" -> "🐞";
            default -> "📌";
        };

        StringBuilder sb = new StringBuilder();
        sb.append("<b>🔥 ").append(appProps.getName()).append("</b>\n\n")
          .append("<b>Logger:</b> ").append(loggerName).append("\n")
          .append("<b>Time:</b> ").append(timestamp).append("\n")
          .append("<b>Level:</b> ").append(emoji).append(" ").append(level).append("\n")
          .append("<b>Message:</b> ").append(message);

        if (throwable != null) {
            sb.append("\n\n<b>Exception:</b> ")
              .append(throwable.getClass().getSimpleName());
            if (throwable.getMessage() != null) {
                sb.append("\n").append(throwable.getMessage());
            }
        }
        return sb.toString();
    }

    private String formatConsole(String level, String loggerName, String message, Throwable throwable) {
        String color = switch (level.toUpperCase()) {
            case "INFO" -> ANSI_BLUE;
            case "WARN" -> ANSI_YELLOW;
            case "ERROR" -> ANSI_RED;
            case "DEBUG" -> ANSI_GREEN;
            default -> ANSI_CYAN;
        };

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        StringBuilder sb = new StringBuilder();
        sb.append(color)
          .append("[")
          .append(level)
          .append("] ")
          .append(ANSI_RESET)
          .append("[")
          .append(loggerName)
          .append("] ")
          .append(timestamp)
          .append(" – ")
          .append(message);

        if (throwable != null) {
            sb.append("\n")
              .append(color)
              .append("Exception: ")
              .append(throwable.getClass().getSimpleName())
              .append(ANSI_RESET);
            if (throwable.getMessage() != null) {
                sb.append(": ").append(throwable.getMessage());
            }
        }
        return sb.toString();
    }

    // Convenience methods
    @Async
    public void info(String loggerName, String message) {
        log(loggerName, "INFO", message, null);
    }
    @Async
    public void warn(String loggerName, String message) {
        log(loggerName, "WARN", message, null);
    }
    @Async
    public void error(String loggerName, String message) {
        log(loggerName, "ERROR", message, null);
    }
    @Async
    public void error(String loggerName, String message, Throwable throwable) {
        log(loggerName, "ERROR", message, throwable);
    }
    @Async
    public void debug(String loggerName, String message) {
        log(loggerName, "DEBUG", message, null);
    }
}