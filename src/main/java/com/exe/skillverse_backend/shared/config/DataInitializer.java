package com.exe.skillverse_backend.shared.config;

import com.exe.skillverse_backend.auth_service.entity.Role;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.entity.UserStatus;
import com.exe.skillverse_backend.auth_service.repository.RoleRepository;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        initializeRoles();
        initializeUsers();
    }

    private void initializeRoles() {
        try {
            // Create USER role if it doesn't exist
            if (!roleRepository.existsByName("USER")) {
                Role userRole = new Role();
                userRole.setName("USER");
                roleRepository.save(userRole);
                log.info("✅ Created USER role");
            } else {
                log.info("✅ USER role already exists");
            }

            // Create ADMIN role if it doesn't exist
            if (!roleRepository.existsByName("ADMIN")) {
                Role adminRole = new Role();
                adminRole.setName("ADMIN");
                roleRepository.save(adminRole);
                log.info("✅ Created ADMIN role");
            } else {
                log.info("✅ ADMIN role already exists");
            }

            // Create MENTOR role if it doesn't exist
            if (!roleRepository.existsByName("MENTOR")) {
                Role mentorRole = new Role();
                mentorRole.setName("MENTOR");
                roleRepository.save(mentorRole);
                log.info("✅ Created MENTOR role");
            } else {
                log.info("✅ MENTOR role already exists");
            }

            // Create RECRUITER role if it doesn't exist
            if (!roleRepository.existsByName("RECRUITER")) {
                Role recruiterRole = new Role();
                recruiterRole.setName("RECRUITER");
                roleRepository.save(recruiterRole);
                log.info("✅ Created RECRUITER role");
            } else {
                log.info("✅ RECRUITER role already exists");
            }

            log.info("🎉 All roles initialized successfully");

        } catch (Exception e) {
            log.error("❌ Error initializing roles: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize roles", e);
        }
    }

    private void initializeUsers() {
        try {
            // Get roles first
            Role userRole = roleRepository.findByName("USER")
                    .orElseThrow(() -> new RuntimeException("USER role not found"));
            Role adminRole = roleRepository.findByName("ADMIN")
                    .orElseThrow(() -> new RuntimeException("ADMIN role not found"));
            Role mentorRole = roleRepository.findByName("MENTOR")
                    .orElseThrow(() -> new RuntimeException("MENTOR role not found"));
            Role recruiterRole = roleRepository.findByName("RECRUITER")
                    .orElseThrow(() -> new RuntimeException("RECRUITER role not found"));

            // Create admin user
            createUserIfNotExists("exeadmin@gmail.com", "12345678", Set.of(adminRole), "Admin User");

            // Create mentor user
            createUserIfNotExists("exementor@gmail.com", "12345678", Set.of(mentorRole), "Mentor User");

            // Create recruiter user
            createUserIfNotExists("exerecruiter@gmail.com", "12345678", Set.of(recruiterRole), "Recruiter User");

            // Create regular user
            createUserIfNotExists("exeuser@gmail.com", "12345678", Set.of(userRole), "Regular User");

            log.info("🎉 All test users initialized successfully");

        } catch (Exception e) {
            log.error("❌ Error initializing users: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize users", e);
        }
    }

    private void createUserIfNotExists(String email, String password, Set<Role> roles, String description) {
        if (!userRepository.existsByEmail(email)) {
            User user = User.builder()
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .status(UserStatus.ACTIVE)
                    .isEmailVerified(true)
                    .roles(roles)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            userRepository.save(user);
            log.info("✅ Created {} with email: {}", description, email);
        } else {
            log.info("✅ {} already exists with email: {}", description, email);
        }
    }
}