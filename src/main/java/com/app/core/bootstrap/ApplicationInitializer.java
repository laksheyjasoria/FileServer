package com.app.core.bootstrap;

import java.time.LocalDateTime;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.app.core.config.AppProperties;
import com.app.identity.entity.AuthProvider;
import com.app.identity.entity.Role;
import com.app.identity.entity.User;
import com.app.identity.repository.UserRepository;
import com.app.logger.api.service.LoggerService;
import com.app.logger.api.service.LogService;

@Component
public class ApplicationInitializer {

    private final UserRepository userRepo;
    private final LoggerService loggerService;
    private final LogService logService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    public ApplicationInitializer(UserRepository userRepo, LoggerService loggerService, LogService logService,
            PasswordEncoder passwordEncoder, AppProperties appProperties) {
        this.userRepo = userRepo;
        this.loggerService = loggerService;
        this.logService = logService;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeApplication() {
        initializeDefaultAdmin();
        initializeFileServerLogger();
    }

    private void initializeDefaultAdmin() {
        String adminEmail = appProperties.getAdmin().getEmail();
        String adminPassword = appProperties.getAdmin().getPassword();
        String adminName = appProperties.getAdmin().getName();

        // Skip if no admin email configured
        if (adminEmail == null || adminEmail.isBlank()) {
            return;
        }

        if (userRepo.findByEmail(adminEmail).isPresent()) {
            return;
        }

        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setName(adminName);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setProvider(AuthProvider.LOCAL);
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        admin.setCreatedAt(LocalDateTime.now());

        userRepo.save(admin);
        System.out.println("✓ Default admin user created: " + adminEmail);
    }

    private void initializeFileServerLogger() {
        String loggerName = "FileServer";

        var logger = loggerService.create(loggerName);
        if (logger != null && logger.getId() != null) {
            // Log initialization message
            logService.log(logger.getId(), "INFO", "FileServer application initialized successfully");
            System.out.println("✓ FileServer logger initialized with ID: " + logger.getId());
        }
    }
}
