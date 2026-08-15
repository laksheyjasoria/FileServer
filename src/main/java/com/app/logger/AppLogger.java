package com.app.logger;

import com.app.logger.core.InternalLoggerService;

/**
 * Per‑class logger – just delegates to the common InternalLoggerService.
 * No level checks here – all checks are centralised.
 */
public class AppLogger {

    private final String loggerName;
    private final InternalLoggerService internalLogger;

    public AppLogger(String loggerName, InternalLoggerService internalLogger) {
        this.loggerName = loggerName;
        this.internalLogger = internalLogger;
    }

    public void info(String message) {
        internalLogger.info(loggerName, message);
    }
    public void warn(String message) {
        internalLogger.warn(loggerName, message);
    }
    public void error(String message) {
        internalLogger.error(loggerName, message);
    }
    public void error(String message, Throwable throwable) {
        internalLogger.error(loggerName, message, throwable);
    }
    public void debug(String message) {
        internalLogger.debug(loggerName, message);
    }

    // Formatted overloads (optional)
    public void info(String format, Object... args) {
        info(String.format(format, args));
    }
    public void warn(String format, Object... args) {
        warn(String.format(format, args));
    }
    public void error(String format, Object... args) {
        error(String.format(format, args));
    }
    public void debug(String format, Object... args) {
        debug(String.format(format, args));
    }
    public void error(String format, Throwable t, Object... args) {
        error(String.format(format, args), t);
    }
}