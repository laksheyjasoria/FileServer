package com.app.core.bootstrap;

import java.time.LocalDateTime;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.app.core.config.AppProperties;
import com.app.identity.entity.User;
import com.app.identity.enums.AuthProvider;
import com.app.identity.enums.Role;
import com.app.identity.repository.UserRepository;
import com.app.logger.AppLogger;
import com.app.logger.factory.AppLoggerFactory;

@Component
public class ApplicationInitializer {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;
    private final AppLogger log;

    public ApplicationInitializer(UserRepository userRepo,
                                  PasswordEncoder passwordEncoder,
                                  AppProperties appProperties,
                                  AppLoggerFactory loggerFactory) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
        this.log = loggerFactory.getLogger(ApplicationInitializer.class);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeApplication() {
        initializeDefaultAdmin();
        log.info("FileServer application initialized successfully");
    }

    private void initializeDefaultAdmin() {
        String adminEmail = appProperties.getAdmin().getEmail();
        String adminPassword = appProperties.getAdmin().getPassword();
        String adminName = appProperties.getAdmin().getName();

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
        log.info("Default admin user created: {}", adminEmail);
    }
}