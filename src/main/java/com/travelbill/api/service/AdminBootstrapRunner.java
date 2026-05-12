package com.travelbill.api.service;

import com.travelbill.api.domain.AppUser;
import com.travelbill.api.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(20)
public class AdminBootstrapRunner implements ApplicationRunner {
    private final AppUserRepository userRepository;
    private final PasswordService passwordService;
    private final String adminUsername;
    private final String adminPassword;

    public AdminBootstrapRunner(
            AppUserRepository userRepository,
            PasswordService passwordService,
            @Value("${app.admin.username:admin}") String adminUsername,
            @Value("${app.admin.password:admin}") String adminPassword
    ) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        userRepository.findByUsername(adminUsername).ifPresentOrElse(user -> {
        }, () -> {
            AppUser admin = new AppUser();
            admin.setUsername(adminUsername);
            admin.setPasswordHash(passwordService.hash(adminPassword));
            admin.setDisplayName(adminUsername);
            admin.setRole("ADMIN");
            userRepository.save(admin);
        });
    }
}
