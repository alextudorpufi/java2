package com.example.demo.runner;

import com.example.demo.entity.*;
import com.example.demo.repository.AppUserRepository;
import com.example.demo.service.AccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@org.springframework.context.annotation.Profile("!keycloak")
public class AdminBootstrap implements CommandLineRunner {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public AdminBootstrap(AppUserRepository users, PasswordEncoder encoder,
                          @Value("${APP_ADMIN_USERNAME:admin}") String username,
                          @Value("${APP_ADMIN_PASSWORD:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (password.isBlank()) return;
        var existing = users.findByUsername(username);
        if (existing.isPresent()) {
            if (!existing.get().getRoles().contains(Role.ADMIN)) {
                throw new IllegalStateException("Bootstrap username belongs to a non-admin account");
            }
            return;
        }
        AccountService.validatePassword(password);
        users.save(new AppUser(username, encoder.encode(password), Role.ADMIN, true));
    }
}
