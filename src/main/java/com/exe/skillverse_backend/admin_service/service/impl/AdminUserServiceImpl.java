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

import com.exe.skillverse_backend.admin_service.dto.request.AddRoleRequest;
import com.exe.skillverse_backend.auth_service.entity.Role;
import com.exe.skillverse_backend.auth_service.repository.RoleRepository;
import java.util.HashSet;
import java.util.Set;

/**
 * Implementation of AdminUserService for managing users
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserServiceImpl implements AdminUserService {

        private final UserRepository userRepository;
        private final RoleRepository roleRepository;
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

                // ✅ Use findByIdWithRoles since response includes roles
                User user = userRepository.findByIdWithRoles(userId)
                                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

                return convertToAdminUserResponse(user);
        }

        @Override
        @Transactional
        public AdminUserResponse updateUserStatus(UpdateUserStatusRequest request) {
                log.info("Updating user status - userId: {}, newStatus: {}", request.getUserId(), request.getStatus());

                User user = userRepository.findById(request.getUserId())
                                .orElseThrow(() -> new RuntimeException(
                                                "User not found with id: " + request.getUserId()));

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

                // ✅ Use findByIdWithRoles to ensure roles are loaded for synchronization
                User user = userRepository.findByIdWithRoles(request.getUserId())
                                .orElseThrow(() -> new RuntimeException(
                                                "User not found with id: " + request.getUserId()));

                PrimaryRole newPrimaryRole = request.getPrimaryRole();
                PrimaryRole oldPrimaryRole = user.getPrimaryRole();
                
                // ✅ SYNC: Update roles entity FIRST to ensure consistency
                // Only sync for main roles (USER, MENTOR, RECRUITER, ADMIN, PARENT)
                // Sub-admin roles are managed separately via addRolesToUser
                if (isMainRole(newPrimaryRole)) {
                        // Validate new role exists BEFORE making any changes
                        Role newRole = roleRepository.findByName(newPrimaryRole.name())
                                .orElseThrow(() -> new RuntimeException("Role not found: " + newPrimaryRole.name()));
                        
                        // Remove old main role if it was a main role
                        if (isMainRole(oldPrimaryRole)) {
                                roleRepository.findByName(oldPrimaryRole.name())
                                        .ifPresent(oldRole -> user.getRoles().remove(oldRole));
                        }
                        
                        // Add new main role
                        user.getRoles().add(newRole);
                        
                        log.info("Synchronized roles entity: removed {}, added {}", oldPrimaryRole, newPrimaryRole);
                }
                
                // Update PrimaryRole enum AFTER roles sync succeeds
                user.setPrimaryRole(newPrimaryRole);
                
                user.setUpdatedAt(LocalDateTime.now());
                User updatedUser = userRepository.save(user);

                log.info("Successfully updated user role for userId: {}", request.getUserId());
                return convertToAdminUserResponse(updatedUser);
        }
        
        /**
         * Check if role is a main role (not sub-admin)
         * Main roles: USER, MENTOR, RECRUITER, ADMIN, PARENT
         */
        private boolean isMainRole(PrimaryRole role) {
                return role == PrimaryRole.USER || 
                       role == PrimaryRole.MENTOR || 
                       role == PrimaryRole.RECRUITER || 
                       role == PrimaryRole.ADMIN || 
                       role == PrimaryRole.PARENT;
        }

        @Override
        @Transactional
        public AdminUserResponse addRolesToUser(AddRoleRequest request) {
                log.info("Adding roles to user - userId: {}, roles: {}", request.getUserId(), request.getRoles());

                // ✅ Use findByIdWithRoles to eagerly fetch roles collection for modification
                User user = userRepository.findByIdWithRoles(request.getUserId())
                                .orElseThrow(() -> new RuntimeException(
                                                "User not found with id: " + request.getUserId()));

                if (request.getRoles() != null && !request.getRoles().isEmpty()) {
                    for (String roleName : request.getRoles()) {
                        Role role = roleRepository.findByName(roleName)
                            .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));
                        user.getRoles().add(role);
                    }
                }

                user.setUpdatedAt(LocalDateTime.now());
                User updatedUser = userRepository.save(user);

                log.info("Successfully added roles for userId: {}", request.getUserId());
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

                // ✅ Use findByIdWithRoles since response includes roles
                User user = userRepository.findByIdWithRoles(userId)
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
                                                                .enrolledAt(LocalDateTime.ofInstant(
                                                                                enrollment.getEnrollDate(),
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
                                                        .issuedAt(LocalDateTime.ofInstant(cert.getIssuedAt(),
                                                                        ZoneId.systemDefault()))
                                                        .certificateUrl("/verify/certificate/" + cert.getSerial())
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
                                .roles(user.getRoles().stream().map(Role::getName).collect(Collectors.toList()))
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
                                .coursesEnrolled(user.getEnrollments() != null ? (long) user.getEnrollments().size()
                                                : 0L)
                                .certificatesEarned(
                                                user.getCertificates() != null ? (long) user.getCertificates().size()
                                                                : 0L)
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
                                .orElseThrow(() -> new RuntimeException(
                                                "User not found with id: " + request.getUserId()));

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
                                .orElseThrow(() -> new RuntimeException(
                                                "User not found with id: " + request.getUserId()));

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
                                .roles(user.getRoles().stream().map(Role::getName).collect(Collectors.toList()))
                                .status(user.getStatus())
                                .isEmailVerified(user.isEmailVerified())
                                .createdAt(user.getCreatedAt())
                                .updatedAt(user.getUpdatedAt())
                                .lastActive(user.getUpdatedAt()) // Use updatedAt as lastActive for now
                                .avatarUrl(getUserAvatarUrl(user))
                                .coursesCreated(user.getCourses() != null ? (long) user.getCourses().size() : 0L)
                                .coursesEnrolled(user.getEnrollments() != null ? (long) user.getEnrollments().size()
                                                : 0L)
                                .certificatesEarned(
                                                user.getCertificates() != null ? (long) user.getCertificates().size()
                                                                : 0L)
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
                                        "Cannot permanently delete an ACTIVE user. Ban/deactivate the account first.");
                }

                try {
                        // Use native SQL to delete all related data and the user itself
                        // Delete in order of FK dependencies (children first, then parent)

                        // Support Service
                        entityManager.createNativeQuery(
                                        "DELETE FROM ticket_messages WHERE sender_id = ?1 OR ticket_id IN (SELECT id FROM support_tickets WHERE user_id = ?1 OR assigned_to = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM support_tickets WHERE user_id = ?1 OR assigned_to = ?1")
                                        .setParameter(1, userId).executeUpdate();

                        // Notification Service
                        entityManager.createNativeQuery("DELETE FROM notifications WHERE user_id = ?1 OR sender_id = ?1")
                                        .setParameter(1, userId).executeUpdate();

                        // AI Service
                        entityManager.createNativeQuery("DELETE FROM chat_messages WHERE user_id = ?1")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM user_roadmap_progress WHERE roadmap_session_id IN (SELECT id FROM roadmap_sessions WHERE user_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM roadmap_sessions WHERE user_id = ?1")
                                        .setParameter(1, userId).executeUpdate();
                        // Study Service (Tasks & Sessions)
                        entityManager.createNativeQuery("DELETE FROM task_study_sessions WHERE session_id IN (SELECT id FROM study_sessions WHERE user_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM tasks WHERE user_id = ?1")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM task_columns WHERE user_id = ?1")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM dashboard_notes WHERE user_id = ?1")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM study_sessions WHERE user_id = ?1")
                                        .setParameter(1, userId).executeUpdate();

                        // Journey Service
                        entityManager.createNativeQuery(
                                        "DELETE FROM test_results WHERE journey_id IN (SELECT id FROM journeys WHERE user_id = ?1) " +
                                                        "OR assessment_test_id IN (SELECT id FROM assessment_tests WHERE journey_id IN (SELECT id FROM journeys WHERE user_id = ?1))")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM assessment_tests WHERE journey_id IN (SELECT id FROM journeys WHERE user_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM journey_progress WHERE user_id = ?1 OR journey_id IN (SELECT id FROM journeys WHERE user_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM journeys WHERE user_id = ?1")
                                        .setParameter(1, userId).executeUpdate();

                        // AI Service
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
                        entityManager.createNativeQuery(
                                        "DELETE FROM withdrawal_requests WHERE user_id = ?1 OR approved_by = ?1")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM wallet_transactions WHERE wallet_id IN (SELECT wallet_id FROM wallets WHERE user_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM wallets WHERE user_id = ?1")
                                        .setParameter(1, userId).executeUpdate();

                        // Portfolio Service
                        entityManager.createNativeQuery(
                                        "DELETE FROM mentor_reviews WHERE user_id = ?1 OR mentor_id = ?1")
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

                        // Business Service (legacy job posting)
                        entityManager.createNativeQuery(
                                        "DELETE FROM job_applications WHERE user_id = ?1 OR job_posting_id IN (SELECT id FROM job_postings WHERE recruiter_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM job_postings WHERE recruiter_id = ?1")
                                        .setParameter(1, userId).executeUpdate();

                        // Business Service (short-term jobs)
                        entityManager.createNativeQuery(
                                        "DELETE FROM job_reviews WHERE reviewer_id = ?1 OR reviewee_id = ?1 " +
                                                        "OR application_id IN (SELECT id FROM short_term_job_applications WHERE user_id = ?1 " +
                                                        "OR short_term_job_id IN (SELECT id FROM short_term_jobs WHERE recruiter_id = ?1))")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM revision_notes WHERE requested_by = ?1 " +
                                                        "OR application_id IN (SELECT id FROM short_term_job_applications WHERE user_id = ?1 " +
                                                        "OR short_term_job_id IN (SELECT id FROM short_term_jobs WHERE recruiter_id = ?1))")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM job_deliverables WHERE uploaded_by = ?1 " +
                                                        "OR application_id IN (SELECT id FROM short_term_job_applications WHERE user_id = ?1 " +
                                                        "OR short_term_job_id IN (SELECT id FROM short_term_jobs WHERE recruiter_id = ?1)) " +
                                                        "OR milestone_id IN (SELECT id FROM short_term_job_milestones WHERE short_term_job_id IN " +
                                                        "(SELECT id FROM short_term_jobs WHERE recruiter_id = ?1))")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM job_status_audit_logs WHERE changed_by = ?1 " +
                                                        "OR job_id IN (SELECT id FROM job_postings WHERE recruiter_id = ?1) " +
                                                        "OR short_term_job_id IN (SELECT id FROM short_term_jobs WHERE recruiter_id = ?1) " +
                                                        "OR application_id IN (SELECT id FROM short_term_job_applications WHERE user_id = ?1 " +
                                                        "OR short_term_job_id IN (SELECT id FROM short_term_jobs WHERE recruiter_id = ?1))")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM short_term_job_applications WHERE user_id = ?1 " +
                                                        "OR short_term_job_id IN (SELECT id FROM short_term_jobs WHERE recruiter_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM short_term_job_milestones WHERE short_term_job_id IN (SELECT id FROM short_term_jobs WHERE recruiter_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM short_term_jobs WHERE recruiter_id = ?1")
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

                        // Skin Service
                        entityManager.createNativeQuery("DELETE FROM user_skins WHERE user_id = ?1")
                                        .setParameter(1, userId).executeUpdate();

                        // User Service - clear profile references before deleting media
                        entityManager.createNativeQuery("UPDATE user_profiles SET avatar_media_id = NULL WHERE user_id = ?1")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM user_profiles WHERE user_id = ?1")
                                        .setParameter(1, userId).executeUpdate();

                        entityManager.createNativeQuery("DELETE FROM media WHERE uploaded_by = ?1")
                                        .setParameter(1, userId).executeUpdate();

                        // User Service (remaining)
                        entityManager.createNativeQuery("DELETE FROM user_skills WHERE user_id = ?1")
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
                        entityManager.createNativeQuery(
                                        "DELETE FROM assignment_submissions WHERE user_id = ?1 OR graded_by = ?1")
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

                        // Parent Service
                        entityManager.createNativeQuery("DELETE FROM parent_student_links WHERE parent_id = ?1 OR student_id = ?1")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM learning_reports WHERE parent_id = ?1 OR student_id = ?1")
                                        .setParameter(1, userId).executeUpdate();

                        // Student Learning Report Service
                        entityManager.createNativeQuery("DELETE FROM student_learning_reports WHERE student_id = ?1")
                                        .setParameter(1, userId).executeUpdate();

                        // Report Service
                        entityManager.createNativeQuery(
                                        "DELETE FROM report_evidences WHERE violation_report_id IN (SELECT id FROM violation_reports WHERE reporter_id = ?1 OR reported_user_id = ?1 OR assigned_admin_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM violation_reports WHERE reporter_id = ?1 OR reported_user_id = ?1 OR assigned_admin_id = ?1")
                                        .setParameter(1, userId).executeUpdate();

                        // Gamification Service
                        entityManager.createNativeQuery("DELETE FROM gamification_activity_logs WHERE user_id = ?1")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM gamification_coin_transactions WHERE user_id = ?1")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM gamification_game_sessions WHERE user_id = ?1")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM gamification_user_badges WHERE user_id = ?1")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM gamification_leaderboard_snapshots WHERE user_id = ?1")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM gamification_user_wallets WHERE user_id = ?1")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM daily_check_ins WHERE user_id = ?1")
                                        .setParameter(1, userId).executeUpdate();

                        // Course Service - Quiz Attempts
                        entityManager.createNativeQuery("DELETE FROM quiz_attempts WHERE user_id = ?1")
                                        .setParameter(1, userId).executeUpdate();

                        // Mentor Booking Service
                        entityManager.createNativeQuery("DELETE FROM booking_reviews WHERE student_id = ?1 OR mentor_id = ?1")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM mentor_bookings WHERE learner_id = ?1 OR mentor_id = ?1")
                                        .setParameter(1, userId).executeUpdate();

                        // Prechat Service
                        entityManager.createNativeQuery("DELETE FROM prechat_messages WHERE sender_id = ?1 OR mentor_id = ?1 OR learner_id = ?1")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM prechat_thread_state WHERE mentor_id = ?1 OR learner_id = ?1")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM prechat_blocks WHERE mentor_id = ?1 OR learner_id = ?1")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM prechat_reports WHERE mentor_id = ?1 OR learner_id = ?1 OR reporter_id = ?1")
                                        .setParameter(1, userId).executeUpdate();

                        // Mentor Service - Favorite Mentors
                        entityManager.createNativeQuery("DELETE FROM favorite_mentors WHERE student_id = ?1 OR mentor_id = ?1")
                                        .setParameter(1, userId).executeUpdate();

                        // Seminar Service
                        entityManager.createNativeQuery("DELETE FROM seminar_tickets WHERE CAST(user_id AS TEXT) = CAST(?1 AS TEXT)")
                                        .setParameter(1, userId).executeUpdate();

                        // Community Service
                        entityManager.createNativeQuery(
                                        "DELETE FROM post_likes WHERE user_id = ?1 OR post_id IN (SELECT id FROM posts WHERE user_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM post_dislikes WHERE user_id = ?1 OR post_id IN (SELECT id FROM posts WHERE user_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM saved_posts WHERE user_id = ?1 OR post_id IN (SELECT id FROM posts WHERE user_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM comments WHERE user_id = ?1 OR post_id IN (SELECT id FROM posts WHERE user_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM posts WHERE user_id = ?1")
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
