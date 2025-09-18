package com.exe.skillverse_backend.shared.config;

import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.exe.skillverse_backend.auth_service.entity.Role;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.RoleRepository;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.mentor_service.entity.MentorProfile;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.mentor_service.entity.ApplicationStatus;
import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.UserStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MentorProfileRepository mentorProfileRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;

    @Override
    public void run(String... args) throws Exception {
        initializeRoles();
        initializeUsers();
        // initializeProfiles(); // Temporarily disabled - profiles can be created via
        // API
    }

    private void initializeRoles() {
        try {
            // Create USER role if it doesn't exist
            if (!roleRepository.existsByName("USER")) {
                Role userRole = new Role();
                userRole.setName("USER");
                roleRepository.save(userRole);
                log.info("✅ Created USER role");
            }

            // Create ADMIN role if it doesn't exist
            if (!roleRepository.existsByName("ADMIN")) {
                Role adminRole = new Role();
                adminRole.setName("ADMIN");
                roleRepository.save(adminRole);
                log.info("✅ Created ADMIN role");
            }

            // Create MENTOR role if it doesn't exist
            if (!roleRepository.existsByName("MENTOR")) {
                Role mentorRole = new Role();
                mentorRole.setName("MENTOR");
                roleRepository.save(mentorRole);
                log.info("✅ Created MENTOR role");
            }

            // Create RECRUITER role if it doesn't exist
            if (!roleRepository.existsByName("RECRUITER")) {
                Role recruiterRole = new Role();
                recruiterRole.setName("RECRUITER");
                roleRepository.save(recruiterRole);
                log.info("✅ Created RECRUITER role");
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

            // Create admin user (admin role with ADMIN primary role)
            createUserIfNotExists("exeadmin@gmail.com", "Password123!", Set.of(adminRole), "Admin User",
                    PrimaryRole.ADMIN, UserStatus.ACTIVE);

            // Create mentor user (ACTIVE for testing - normally would be INACTIVE until
            // approved)
            createUserIfNotExists("exementor@gmail.com", "Password123!", Set.of(mentorRole), "Mentor User",
                    PrimaryRole.MENTOR, UserStatus.ACTIVE);

            // Create recruiter user (ACTIVE for testing - normally would be INACTIVE until
            // approved)
            createUserIfNotExists("exerecruiter@gmail.com", "Password123!", Set.of(recruiterRole), "Recruiter User",
                    PrimaryRole.RECRUITER, UserStatus.ACTIVE);

            // Create regular user (ACTIVE after email verification)
            createUserIfNotExists("exeuser@gmail.com", "Password123!", Set.of(userRole), "Regular User",
                    PrimaryRole.USER, UserStatus.ACTIVE);

            log.info("🎉 All test users initialized successfully");

        } catch (Exception e) {
            log.error("❌ Error initializing users: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize users", e);
        }
    }

    private void createUserIfNotExists(String email, String password, Set<Role> roles, String description,
            PrimaryRole primaryRole, UserStatus userStatus) {
        if (!userRepository.existsByEmail(email)) {
            User user = User.builder()
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .status(userStatus)
                    .isEmailVerified(true)
                    .roles(roles)
                    .primaryRole(primaryRole)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            userRepository.save(user);
            log.info("✅ Created {} with email: {} and primary role: {}", description, email, primaryRole);
        } else {
            log.info("✅ {} already exists: {}", description, email);
        }
    }

    @Transactional
    private void initializeProfiles() {
        try {
            // Create mentor profile for test mentor user (APPROVED status so they can login
            // immediately)
            User mentorUser = userRepository.findByEmail("exementor@gmail.com").orElse(null);
            if (mentorUser != null && !mentorProfileRepository.existsByUserId(mentorUser.getId())) {
                MentorProfile mentorProfile = MentorProfile.builder()
                        .user(mentorUser)
                        .fullName("Test Mentor")
                        .email("exementor@gmail.com")
                        .linkedinProfile("https://linkedin.com/in/test-mentor")
                        .mainExpertiseAreas("Java, Spring Boot, Microservices")
                        .yearsOfExperience(5)
                        .personalProfile("Experienced software developer with 5+ years in backend development")
                        .cvPortfolioUrl("https://portfolio.test-mentor.com")
                        .certificatesUrl("https://certificates.test-mentor.com")
                        .applicationStatus(ApplicationStatus.APPROVED) // APPROVED so they can login
                        .applicationDate(LocalDateTime.now())
                        .approvalDate(LocalDateTime.now()) // Set approval date
                        .build();

                mentorProfileRepository.save(mentorProfile);
                log.info("✅ Created APPROVED MentorProfile for test mentor user");
            }

            // Create recruiter profile for test recruiter user (APPROVED status so they can
            // login immediately)
            User recruiterUser = userRepository.findByEmail("exerecruiter@gmail.com").orElse(null);
            if (recruiterUser != null && !recruiterProfileRepository.existsByUserId(recruiterUser.getId())) {
                RecruiterProfile recruiterProfile = RecruiterProfile.builder()
                        .user(recruiterUser)
                        .companyName("Test Company Inc")
                        .companyWebsite("https://www.testcompany.com")
                        .companyAddress("123 Tech Street, District 1, Ho Chi Minh City, Vietnam")
                        .taxCodeOrBusinessRegistrationNumber("0123456789")
                        .companyDocumentsUrl("https://storage.testcompany.com/business-license.pdf")
                        .applicationStatus(ApplicationStatus.APPROVED) // APPROVED so they can login
                        .applicationDate(LocalDateTime.now())
                        .approvalDate(LocalDateTime.now()) // Set approval date
                        .build();

                recruiterProfileRepository.save(recruiterProfile);
                log.info("✅ Created APPROVED RecruiterProfile for test recruiter user");
            }

            log.info("🎉 All test profiles initialized successfully");

        } catch (Exception e) {
            log.error("❌ Error initializing profiles: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize profiles", e);
        }
    }
}