package com.exe.skillverse_backend.auth_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.Role;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.entity.UserStatus;
import com.exe.skillverse_backend.auth_service.repository.RoleRepository;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.auth_service.service.EmailVerificationService;
import com.exe.skillverse_backend.auth_service.service.UserCreationService;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.impl.NotificationServiceImpl;
import com.exe.skillverse_backend.premium_service.service.PremiumService;
import java.time.LocalDateTime;
import java.util.Arrays;
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
public class UserCreationServiceImpl implements UserCreationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final PremiumService premiumService;
    private final NotificationServiceImpl notificationService;

    /**
     * Create a new user for mentor registration
     */
    @Transactional
    public User createUserForMentor(String email, String password, String fullName) {
        return createUser(email, password, fullName, null, PrimaryRole.MENTOR, "MENTOR", true);
    }

    /**
     * Create a new user for business/recruiter registration
     */
    @Transactional
    public User createUserForRecruiter(String email, String password, String fullName, String phone) {
        return createUser(email, password, fullName, phone, PrimaryRole.RECRUITER, "RECRUITER", true);
    }

    /**
     * Create a new user for parent registration
     */
    @Transactional
    public User createUserForParent(String email, String password, String fullName, String phone) {
        return createUser(email, password, fullName, phone, PrimaryRole.PARENT, "PARENT", true);
    }

    /**
     * Create a new user for regular user registration
     */
    @Transactional
    public User createUserForUser(String email, String password, String fullName) {
        return createUser(email, password, fullName, null, PrimaryRole.USER, "USER", true);
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
     * Get OTP expiry time for user
     */
    public LocalDateTime getOtpExpiryTime(String email) {
        return emailVerificationService.getOtpExpiryTime(email);
    }

    /**
     * Private method to create user with specific role
     */
    private User createUser(String email, String password, String fullName, String phone, PrimaryRole primaryRole,
            String roleName, boolean generateOtp) {
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
                .phoneNumber(phone)
                .primaryRole(primaryRole)
                .status(UserStatus.INACTIVE)
                .isEmailVerified(false)
                .build();

        // Assign role
        user.getRoles().add(role);

        user = userRepository.save(user);
        log.info("Created user with ID: {} for role: {}", user.getId(), primaryRole);

        try {
            notificationService.createNotification(
                    user.getId(),
                    "Chào mừng đến với SkillVerse!",
                    "Chào mừng bạn gia nhập cộng đồng SkillVerse. Hãy bắt đầu hành trình học tập của bạn ngay hôm nay!",
                    NotificationType.WELCOME,
                    null);
        } catch (Exception e) {
            log.error("Failed to create welcome notification for user {}", user.getId(), e);
        }

        // Auto-assign FREE_TIER subscription to new users using PremiumService
        try {
            premiumService.assignFreeTierIfMissing(user.getId());
            log.info("✅ Auto-assigned FREE_TIER subscription to user: {} (ID: {})", user.getEmail(), user.getId());
        } catch (Exception e) {
            log.error("❌ Failed to assign FREE_TIER subscription to user: {}", user.getEmail(), e);
            // Don't throw exception - user creation should succeed even if subscription fails
        }

        // Generate OTP for email verification only if requested
        if (generateOtp) {
            emailVerificationService.generateOtpForUser(email);
            log.info("Generated OTP for user: {}", email);
        }
        return user;
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
            return String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
        }
        return "";
    }
}