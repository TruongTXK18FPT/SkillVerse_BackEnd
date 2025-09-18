package com.exe.skillverse_backend.auth_service.service;

import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.Role;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.entity.UserStatus;
import com.exe.skillverse_backend.auth_service.repository.RoleRepository;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.shared.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for creating User entities for other services
 * This allows other services to create users without directly accessing
 * auth_service repositories
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserCreationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final AuditService auditService;

    /**
     * Create a new user for mentor registration
     */
    @Transactional
    public User createUserForMentor(String email, String password, String fullName) {
        return createUser(email, password, fullName, PrimaryRole.MENTOR, "MENTOR", false);
    }

    /**
     * Create a new user for business/recruiter registration
     */
    @Transactional
    public User createUserForRecruiter(String email, String password, String fullName) {
        return createUser(email, password, fullName, PrimaryRole.RECRUITER, "RECRUITER", false);
    }

    /**
     * Create a new user for regular user registration
     */
    @Transactional
    public User createUserForUser(String email, String password, String fullName) {
        return createUser(email, password, fullName, PrimaryRole.USER, "USER");
    }

    /**
     * Check if email already exists
     */
    public boolean emailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * Generate OTP for user email verification
     */
    public String generateOtpForUser(String email) {
        return emailVerificationService.generateOtpForUser(email);
    }

    /**
     * Private method to create user with specific role
     */
    private User createUser(String email, String password, String fullName, PrimaryRole primaryRole, String roleName,
            boolean generateOtp) {
        log.info("Creating user for {}: {}", primaryRole, email);

        // Check if user already exists
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists: " + email);
        }

        // Find the role
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> {
                    log.error("{} role not found in database", roleName);
                    return new IllegalStateException(roleName + " role not found in database");
                });

        // Create user
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .firstName(extractFirstName(fullName))
                .lastName(extractLastName(fullName))
                .primaryRole(primaryRole)
                .status(UserStatus.INACTIVE)
                .isEmailVerified(false)
                .build();

        // Assign role
        user.getRoles().add(role);

        user = userRepository.save(user);
        log.info("Created user with ID: {} for role: {}", user.getId(), primaryRole);

        // Generate OTP for email verification only if requested
        if (generateOtp) {
            emailVerificationService.generateOtpForUser(email);
            log.info("Generated OTP for user: {}", email);
        }

        // Log action
        auditService.logAction(user.getId(), primaryRole + "_USER_CREATED", "USER", user.getId().toString(),
                "User created for " + primaryRole + " registration: " + email);

        return user;
    }

    /**
     * Private method to create user with specific role (with OTP generation by
     * default)
     */
    private User createUser(String email, String password, String fullName, PrimaryRole primaryRole, String roleName) {
        return createUser(email, password, fullName, primaryRole, roleName, true);
    }

    /**
     * Extract first name from full name
     */
    private String extractFirstName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "";
        }
        String[] parts = fullName.trim().split("\\s+");
        return parts[0];
    }

    /**
     * Extract last name from full name
     */
    private String extractLastName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length > 1) {
            return String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length));
        }
        return "";
    }
}