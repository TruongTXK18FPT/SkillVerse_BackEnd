package com.exe.skillverse_backend.admin_service.service.impl;

import com.exe.skillverse_backend.admin_service.dto.request.ResetPasswordRequest;
import com.exe.skillverse_backend.admin_service.dto.request.UpdateUserProfileRequest;
import com.exe.skillverse_backend.admin_service.dto.request.UpdateUserRoleRequest;
import com.exe.skillverse_backend.admin_service.dto.request.UpdateUserStatusRequest;
import com.exe.skillverse_backend.admin_service.dto.response.AdminUserDetailResponse;
import com.exe.skillverse_backend.admin_service.dto.response.AdminUserListResponse;
import com.exe.skillverse_backend.admin_service.dto.response.AdminUserResponse;
import com.exe.skillverse_backend.admin_service.service.AdminUserService;
import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.entity.UserStatus;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.entity.Certificate;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of AdminUserService for managing users
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserProfileService userProfileService;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public AdminUserListResponse getAllUsers(PrimaryRole role, UserStatus status, String search) {
        log.info("Fetching users with filters - role: {}, status: {}, search: {}", role, status, search);

        List<User> users;

        // Apply filters
        if (search != null && !search.trim().isEmpty()) {
            users = userRepository.findUsersWithFilters(role, status, search.trim());
        } else if (role != null && status != null) {
            users = userRepository.findByPrimaryRoleAndStatus(role, status);
        } else if (role != null) {
            users = userRepository.findByPrimaryRole(role);
        } else if (status != null) {
            users = userRepository.findByStatus(status);
        } else {
            users = userRepository.findAll();
        }

        // Hide ADMIN accounts by default when no explicit role filter is provided
        if (role == null) {
            users = users.stream()
                    .filter(u -> u.getPrimaryRole() != PrimaryRole.ADMIN)
                    .collect(Collectors.toList());
        }

        // Convert to DTOs
        List<AdminUserResponse> userResponses = users.stream()
                .map(this::convertToAdminUserResponse)
                .collect(Collectors.toList());

        // Calculate statistics
        Long totalUsers = (long) users.size();
        Long totalMentors = users.stream().filter(u -> u.getPrimaryRole() == PrimaryRole.MENTOR).count();
        Long totalRecruiters = users.stream().filter(u -> u.getPrimaryRole() == PrimaryRole.RECRUITER).count();
        Long totalRegularUsers = users.stream().filter(u -> u.getPrimaryRole() == PrimaryRole.USER).count();
        Long totalActiveUsers = users.stream().filter(u -> u.getStatus() == UserStatus.ACTIVE).count();
        Long totalInactiveUsers = users.stream().filter(u -> u.getStatus() == UserStatus.INACTIVE).count();

        return AdminUserListResponse.builder()
                .users(userResponses)
                .totalUsers(totalUsers)
                .totalMentors(totalMentors)
                .totalRecruiters(totalRecruiters)
                .totalRegularUsers(totalRegularUsers)
                .totalActiveUsers(totalActiveUsers)
                .totalInactiveUsers(totalInactiveUsers)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserResponse getUserById(Long userId) {
        log.info("Fetching user details for userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        return convertToAdminUserResponse(user);
    }

    @Override
    @Transactional
    public AdminUserResponse updateUserStatus(UpdateUserStatusRequest request) {
        log.info("Updating user status - userId: {}, newStatus: {}", request.getUserId(), request.getStatus());

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found with id: " + request.getUserId()));

        user.setStatus(request.getStatus());
        user.setUpdatedAt(LocalDateTime.now());

        User updatedUser = userRepository.save(user);

        log.info("Successfully updated user status for userId: {}", request.getUserId());
        return convertToAdminUserResponse(updatedUser);
    }

    @Override
    @Transactional
    public AdminUserResponse updateUserRole(UpdateUserRoleRequest request) {
        log.info("Updating user role - userId: {}, newRole: {}", request.getUserId(), request.getPrimaryRole());

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found with id: " + request.getUserId()));

        user.setPrimaryRole(request.getPrimaryRole());
        user.setUpdatedAt(LocalDateTime.now());

        User updatedUser = userRepository.save(user);

        log.info("Successfully updated user role for userId: {}", request.getUserId());
        return convertToAdminUserResponse(updatedUser);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        log.info("Deleting user with userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        // Soft delete by setting status to INACTIVE
        user.setStatus(UserStatus.INACTIVE);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Successfully deleted user with userId: {}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetailById(Long userId) {
        log.info("Fetching detailed user information for userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        // Get recent courses (top 5)
        List<AdminUserDetailResponse.UserCourseInfo> recentCourses = new ArrayList<>();
        if (user.getEnrollments() != null) {
            recentCourses = user.getEnrollments().stream()
                    .sorted(Comparator.comparing(CourseEnrollment::getEnrollDate).reversed())
                    .limit(5)
                    .map(enrollment -> {
                        String thumbnailUrl = null;
                        if (enrollment.getCourse().getThumbnail() != null) {
                            thumbnailUrl = enrollment.getCourse().getThumbnail().getUrl();
                        }
                        return AdminUserDetailResponse.UserCourseInfo.builder()
                                .courseId(enrollment.getCourse().getId())
                                .courseTitle(enrollment.getCourse().getTitle())
                                .courseThumbnail(thumbnailUrl)
                                .enrolledAt(LocalDateTime.ofInstant(enrollment.getEnrollDate(),
                                        ZoneId.systemDefault()))
                                .progress(enrollment.getProgressPercent())
                                .build();
                    })
                    .collect(Collectors.toList());
        }

        // Get recent certificates (top 5)
        List<AdminUserDetailResponse.UserCertificateInfo> recentCertificates = new ArrayList<>();
        if (user.getCertificates() != null) {
            recentCertificates = user.getCertificates().stream()
                    .sorted(Comparator.comparing(Certificate::getIssuedAt).reversed())
                    .limit(5)
                    .map(cert -> AdminUserDetailResponse.UserCertificateInfo.builder()
                            .certificateId(cert.getId())
                            .courseName(cert.getCourse().getTitle())
                            .issuedAt(LocalDateTime.ofInstant(cert.getIssuedAt(), ZoneId.systemDefault()))
                            .certificateUrl("/api/certificates/" + cert.getSerial()) // Generate URL from serial
                            .build())
                    .collect(Collectors.toList());
        }

        String fullName = (user.getFirstName() != null ? user.getFirstName() : "") +
                " " +
                (user.getLastName() != null ? user.getLastName() : "");
        fullName = fullName.trim();
        if (fullName.isEmpty()) {
            fullName = user.getEmail().split("@")[0];
        }

        return AdminUserDetailResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(fullName)
                .phoneNumber(user.getPhoneNumber())
                .primaryRole(user.getPrimaryRole())
                .status(user.getStatus())
                .isEmailVerified(user.isEmailVerified())
                .authProvider(user.getAuthProvider())
                .googleLinked(user.isGoogleLinked())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastActive(user.getUpdatedAt())
                .avatarUrl(null) // TODO: Get from media service
                .bio(null) // TODO: Get from user profile
                .coursesCreated(user.getCourses() != null ? (long) user.getCourses().size() : 0L)
                .coursesEnrolled(user.getEnrollments() != null ? (long) user.getEnrollments().size() : 0L)
                .certificatesEarned(user.getCertificates() != null ? (long) user.getCertificates().size() : 0L)
                .totalSpent(0L) // TODO: Calculate from purchases
                .totalEarned(0L) // TODO: Calculate from earnings
                .loginCount(0) // TODO: Track login count
                .lastLoginAt(user.getUpdatedAt())
                .lastLoginIp(null) // TODO: Track IP
                .recentCourses(recentCourses)
                .recentCertificates(recentCertificates)
                .build();
    }

    @Override
    @Transactional
    public AdminUserResponse updateUserProfile(UpdateUserProfileRequest request) {
        log.info("Updating user profile - userId: {}", request.getUserId());

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found with id: " + request.getUserId()));

        StringBuilder changes = new StringBuilder("Profile updated: ");

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
            changes.append("firstName, ");
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
            changes.append("lastName, ");
        }
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            // Check if email already exists
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new RuntimeException("Email already exists: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
            changes.append("email, ");
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
            changes.append("phoneNumber, ");
        }

        user.setUpdatedAt(LocalDateTime.now());
        User updatedUser = userRepository.save(user);

        log.info("Successfully updated user profile for userId: {}", request.getUserId());
        return convertToAdminUserResponse(updatedUser);
    }

    @Override
    @Transactional
    public String resetUserPassword(ResetPasswordRequest request) {
        log.info("Resetting password for userId: {}", request.getUserId());

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found with id: " + request.getUserId()));

        // Encode and set new password
        String encodedPassword = passwordEncoder.encode(request.getNewPassword());
        user.setPassword(encodedPassword);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Successfully reset password for userId: {}", request.getUserId());
        return "Password reset successfully for user: " + user.getEmail();
    }

    /**
     * Convert User entity to AdminUserResponse DTO
     */
    private AdminUserResponse convertToAdminUserResponse(User user) {
        String fullName = (user.getFirstName() != null ? user.getFirstName() : "") +
                " " +
                (user.getLastName() != null ? user.getLastName() : "");
        fullName = fullName.trim();
        if (fullName.isEmpty()) {
            fullName = user.getEmail().split("@")[0]; // Use email prefix if no name
        }

        return AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(fullName)
                .phoneNumber(user.getPhoneNumber())
                .primaryRole(user.getPrimaryRole())
                .status(user.getStatus())
                .isEmailVerified(user.isEmailVerified())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastActive(user.getUpdatedAt()) // Use updatedAt as lastActive for now
                .avatarUrl(getUserAvatarUrl(user))
                .coursesCreated(user.getCourses() != null ? (long) user.getCourses().size() : 0L)
                .coursesEnrolled(user.getEnrollments() != null ? (long) user.getEnrollments().size() : 0L)
                .certificatesEarned(user.getCertificates() != null ? (long) user.getCertificates().size() : 0L)
                .build();
    }

    @Override
    @Transactional
    public void permanentlyDeleteUser(Long userId) {
        log.info("Permanently deleting user with userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        // Only allow permanent deletion for INACTIVE users
        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new RuntimeException(
                "Cannot permanently delete an ACTIVE user. Please deactivate the account first.");
        }

        try {
            // Use native SQL to delete all related data and the user itself
            // Delete in order of FK dependencies (children first, then parent)

            // Support Service
            entityManager.createNativeQuery("DELETE FROM ticket_messages WHERE sender_id = ?1")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM support_tickets WHERE user_id = ?1 OR assigned_to = ?1")
                    .setParameter(1, userId).executeUpdate();

            // AI Service
            entityManager.createNativeQuery("DELETE FROM chat_messages WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM roadmap_sessions WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();

            // Premium Service
            entityManager.createNativeQuery("DELETE FROM user_usage_tracking WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM subscription_cancellations WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM user_subscriptions WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();

            // Payment Service
            entityManager.createNativeQuery("DELETE FROM payment_transactions WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();

            // Wallet Service
            entityManager.createNativeQuery("DELETE FROM withdrawal_requests WHERE user_id = ?1 OR approved_by = ?1")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM wallet_transactions WHERE wallet_id IN (SELECT wallet_id FROM wallets WHERE user_id = ?1)")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM wallets WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();

            // Portfolio Service
            entityManager.createNativeQuery("DELETE FROM mentor_reviews WHERE user_id = ?1 OR mentor_id = ?1")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM generated_cvs WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM external_certificates WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM portfolio_projects WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM portfolio_extended_profiles WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();

            // Mentor Service
            entityManager.createNativeQuery("DELETE FROM mentor_profiles WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();

            // Business Service
            entityManager.createNativeQuery("DELETE FROM job_applications WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM recruiter_profiles WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();

            // Shared Service
            entityManager.createNativeQuery("DELETE FROM file_uploads WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM user_history WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM audit_logs WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM media WHERE uploaded_by = ?1")
                    .setParameter(1, userId).executeUpdate();

            // User Service
            entityManager.createNativeQuery("DELETE FROM user_skills WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM user_profiles WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();

            // Course Service
            entityManager.createNativeQuery("DELETE FROM lesson_progress WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM module_progress WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM certificates WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM course_purchase WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM course_enrollment WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM assignment_submissions WHERE user_id = ?1 OR graded_by = ?1")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM coding_submissions WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM courses WHERE author_id = ?1")
                    .setParameter(1, userId).executeUpdate();

            // Auth Service
            entityManager.createNativeQuery("DELETE FROM refresh_tokens WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM user_roles WHERE user_id = ?1")
                    .setParameter(1, userId).executeUpdate();

            // Finally delete the user itself
            entityManager.createNativeQuery("DELETE FROM users WHERE id = ?1")
                    .setParameter(1, userId).executeUpdate();

            log.info("Successfully permanently deleted user with userId: {} and all related data", userId);
        } catch (Exception e) {
            log.error("Error permanently deleting user with userId: {}", userId, e);
            throw new RuntimeException("Failed to permanently delete user: " + e.getMessage(), e);
        }
    }

    /**
     * Get user's avatar URL from their profile
     */
    private String getUserAvatarUrl(User user) {
        try {
            if (userProfileService.hasProfile(user.getId())) {
                var profile = userProfileService.getProfile(user.getId());
                String profileAvatar = profile.getAvatarMediaUrl();
                if (profileAvatar != null && !profileAvatar.isBlank()) {
                    return profileAvatar;
                }
            }

            String entityAvatar = user.getAvatarUrl();
            if (entityAvatar != null && !entityAvatar.isBlank()) {
                return entityAvatar;
            }
        } catch (Exception e) {
            log.warn("Failed to get avatar URL for user {}: {}", user.getId(), e.getMessage());
        }
        return null;
    }

}
