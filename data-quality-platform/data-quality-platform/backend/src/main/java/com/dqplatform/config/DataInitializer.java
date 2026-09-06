package com.dqplatform.config;

import com.dqplatform.entity.*;
import com.dqplatform.repository.AppUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {
    @Bean
    CommandLineRunner seed(AppUserRepository repo, PasswordEncoder encoder) {
        return args -> {
            create(repo, encoder, "admin", "Admin@123", Role.ADMIN);
            create(repo, encoder, "entry", "Entry@123", Role.DATA_ENTRY);
            create(repo, encoder, "approver", "Approver@123", Role.APPROVER);
            create(repo, encoder, "viewer", "Viewer@123", Role.VIEWER);
        };
    }

    private void create(AppUserRepository repo, PasswordEncoder encoder,
                        String username, String password, Role role) {
        if (repo.findByUsername(username).isEmpty()) {
            repo.save(AppUser.builder().username(username)
                    .password(encoder.encode(password)).role(role).active(true).build());
        }
    }
}
