package com.exe.skillverse_backend.auth_service.service;

import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.Role;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.entity.UserStatus;
import com.exe.skillverse_backend.auth_service.repository.RoleRepository;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import com.exe.skillverse_backend.premium_service.entity.UserSubscription;
import com.exe.skillverse_backend.premium_service.repository.PremiumPlanRepository;
import com.exe.skillverse_backend.premium_service.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;

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
    private final PremiumPlanRepository premiumPlanRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final NotificationService notificationService;

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
     * Get OTP expiry time for user
     */
    public LocalDateTime getOtpExpiryTime(String email) {
        return emailVerificationService.getOtpExpiryTime(email);
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

        try {
            notificationService.createNotification(
                    user.getId(),
                    "Chào mừng đến với SkillVerse!",
                    "Chào mừng bạn gia nhập cộng đồng SkillVerse. Hãy bắt đầu hành trình học tập của bạn ngay hôm nay!",
                    NotificationType.WELCOME,
                    null
            );
        } catch (Exception e) {
            log.error("Failed to create welcome notification for user {}", user.getId(), e);
        }

        // Auto-assign FREE_TIER subscription to new users
        assignFreeTierSubscription(user);

        // Generate OTP for email verification only if requested
        if (generateOtp) {
            emailVerificationService.generateOtpForUser(email);
            log.info("Generated OTP for user: {}", email);
        }
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
     * Auto-assign FREE_TIER subscription to new user
     */
    private void assignFreeTierSubscription(User user) {
        try {
            // Find FREE_TIER plan
            PremiumPlan freeTier = premiumPlanRepository
                    .findByPlanTypeAndIsActiveTrue(PremiumPlan.PlanType.FREE_TIER)
                    .orElseThrow(() -> new IllegalStateException("FREE_TIER plan not found"));

            // Create permanent subscription for FREE_TIER
            UserSubscription subscription = UserSubscription.builder()
                    .user(user)
                    .plan(freeTier)
                    .startDate(LocalDateTime.now())
                    .endDate(LocalDateTime.now().plusYears(100)) // Permanent
                    .isActive(true)
                    .autoRenew(false)
                    .isStudentSubscription(false)
                    .status(UserSubscription.SubscriptionStatus.ACTIVE)
                    .build();

            userSubscriptionRepository.save(subscription);
            log.info("✅ Auto-assigned FREE_TIER subscription to user: {} (ID: {})", user.getEmail(), user.getId());

        } catch (Exception e) {
            log.error("❌ Failed to assign FREE_TIER subscription to user: {}", user.getEmail(), e);
            // Don't throw exception - user creation should succeed even if subscription
            // fails
        }
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