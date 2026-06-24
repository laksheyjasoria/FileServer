package com.app.scheduler.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.app.logger.AppLogger;
import com.app.logger.factory.AppLoggerFactory;

@Component
public class SystemHealthJob {

    private final AppLogger log;

    public SystemHealthJob(AppLoggerFactory loggerFactory) {
        this.log = loggerFactory.getLogger(SystemHealthJob.class);
    }

    @Scheduled(fixedDelay = 600000)
    public void healthCheck() {

        log.info("System health check completed");
    }
}