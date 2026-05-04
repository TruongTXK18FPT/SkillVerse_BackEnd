package com.exe.skillverse_backend.admin_service.service.impl;

import com.exe.skillverse_backend.admin_service.dto.request.AddRoleRequest;
import com.exe.skillverse_backend.admin_service.dto.request.ResetPasswordRequest;
import com.exe.skillverse_backend.admin_service.dto.request.UpdateUserProfileRequest;
import com.exe.skillverse_backend.admin_service.dto.request.UpdateUserRoleRequest;
import com.exe.skillverse_backend.admin_service.dto.request.UpdateUserStatusRequest;
import com.exe.skillverse_backend.admin_service.dto.response.AdminUserDetailResponse;
import com.exe.skillverse_backend.admin_service.dto.response.AdminUserListResponse;
import com.exe.skillverse_backend.admin_service.dto.response.AdminUserResponse;
import com.exe.skillverse_backend.admin_service.service.AdminUserService;
import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.Role;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.entity.UserStatus;
import com.exe.skillverse_backend.auth_service.repository.RefreshTokenRepository;
import com.exe.skillverse_backend.auth_service.repository.RoleRepository;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.entity.Certificate;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.service.CertificateService;
import com.exe.skillverse_backend.course_service.service.CourseService;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.wallet_service.service.WithdrawalService;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.util.JwtUtils;
import jakarta.persistence.EntityManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of AdminUserService for managing users
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserServiceImpl implements AdminUserService {

        private final UserRepository userRepository;
        private final RoleRepository roleRepository;
        private final RefreshTokenRepository refreshTokenRepository;
        private final PasswordEncoder passwordEncoder;
        private final UserProfileService userProfileService;
        private final EntityManager entityManager;
        private final WalletService walletService;
        private final WithdrawalService withdrawalService;
        private final CourseService courseService;
        private final CertificateService certificateService;
        private final CourseRepository courseRepository;
        private final CourseEnrollmentRepository enrollmentRepository;
        private final NotificationService notificationService;
        private final MentorProfileRepository mentorProfileRepository;
        private final RecruiterProfileRepository recruiterProfileRepository;

        @Override
        @Transactional(readOnly = true)
        public AdminUserListResponse getAllUsers(PrimaryRole role, UserStatus status, String search) {
                log.info("Fetching users with filters - role: {}, status: {}, search: {}", role, status, search);

                // Check if current user is USER_ADMIN (not full ADMIN)
                // USER_ADMIN should never see ADMIN accounts regardless of filter
                boolean isUserAdminOnly = isUserAdminOnly();

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

                // Hide ADMIN accounts:
                // 1. When no explicit role filter is provided (default behavior)
                // 2. Always for USER_ADMIN (cannot see super admin accounts even with filter)
                if (role == null || isUserAdminOnly) {
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
                                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng với ID: " + userId));

                // USER_ADMIN không thể xem tài khoản ADMIN
                if (isUserAdminOnly() && user.getPrimaryRole() == PrimaryRole.ADMIN) {
                        throw new ForbiddenException("Từ chối truy cập: Không thể xem tài khoản quản trị viên cấp cao");
                }

                return convertToAdminUserResponse(user);
        }

        @Override
        @Transactional
        public AdminUserResponse updateUserStatus(UpdateUserStatusRequest request) {
                log.info("Updating user status - userId: {}, newStatus: {}", request.getUserId(), request.getStatus());

                // Self-change guard: Prevent user from changing their own status
                Long currentUserId = getCurrentUserId();
                if (currentUserId.equals(request.getUserId())) {
                        throw new ForbiddenException("Không thể tự thay đổi trạng thái tài khoản của chính mình");
                }

                User user = userRepository.findById(request.getUserId())
                                .orElseThrow(() -> new NotFoundException(
                                                "Không tìm thấy người dùng với ID: " + request.getUserId()));

                // ADMIN protection: Cannot ban/delete another ADMIN
                if (user.getPrimaryRole() == PrimaryRole.ADMIN && !currentUserId.equals(request.getUserId())) {
                        throw new ForbiddenException("Không thể thay đổi trạng thái của quản trị viên cấp cao");
                }

                // Defense-in-depth; strict ADMIN target guards normally prevent this path
                if (request.getStatus() == UserStatus.INACTIVE && user.getPrimaryRole() == PrimaryRole.ADMIN) {
                        Long activeAdminCount = userRepository.countByPrimaryRoleAndStatus(PrimaryRole.ADMIN, UserStatus.ACTIVE);
                        if (activeAdminCount <= 1) {
                                throw new BadRequestException("Không thể khóa tài khoản quản trị viên cuối cùng đang hoạt động");
                        }
                }

                user.setStatus(request.getStatus());
                user.setUpdatedAt(LocalDateTime.now());

                // ========== Ban/Unban Cascade ==========
                if (request.getStatus() == UserStatus.INACTIVE) {
                        log.info("Ban cascade for user {}", request.getUserId());

                        // Step 1: Auto-refund students who haven't completed
                        int refundedCount = walletService.refundStudentsForMentorBan(
                                        request.getUserId(), "Mentor account banned");
                        log.info("Refunded {} students for banned mentor {}", refundedCount, request.getUserId());

                        // Step 2: Suspend all PUBLIC courses
                        List<Course> publicCourses = courseRepository
                                        .findByAuthorIdAndStatus(request.getUserId(), CourseStatus.PUBLIC, Pageable.unpaged())
                                        .getContent();
                        for (Course course : publicCourses) {
                                courseService.suspendCourse(course.getId(), request.getUserId(), "Mentor account banned");
                        }
                        log.info("Suspended {} courses for banned mentor {}", publicCourses.size(), request.getUserId());

                        // Step 3: Revoke certificates
                        int revokedCount = certificateService.revokeByMentorId(
                                        request.getUserId(), "Mentor account banned", request.getUserId());
                        log.info("Revoked {} certificates for banned mentor {}", revokedCount, request.getUserId());

                        // Step 4: Cancel pending withdrawals
                        int cancelledCount = withdrawalService.cancelPendingByUserId(
                                        request.getUserId(), "Mentor account banned");
                        log.info("Cancelled {} pending withdrawals for banned mentor {}", cancelledCount, request.getUserId());

                        // Step 5: Lock wallet
                        walletService.suspendWallet(request.getUserId(), "Mentor account banned");
                        log.info("Wallet locked for banned mentor {}", request.getUserId());

                } else if (request.getStatus() == UserStatus.ACTIVE) {
                        log.info("Unban cascade for user {}", request.getUserId());

                        // Step 1: Unlock wallet
                        walletService.unlockWallet(request.getUserId());
                        log.info("Wallet unlocked for user {}", request.getUserId());

                        // Step 2: Restore all SUSPENDED courses to PUBLIC
                        int restoredCourses = courseService.restoreAllSuspendedCoursesByAuthor(request.getUserId());
                        log.info("Restored {} courses for unbanned mentor {}", restoredCourses, request.getUserId());

                        // Step 3: Restore revoked certificates
                        int restoredCerts = certificateService.restoreRevokedCertificatesByMentor(request.getUserId());
                        log.info("Restored {} certificates for unbanned mentor {}", restoredCerts, request.getUserId());

                        // Step 4: Notify mentor
                        notificationService.createNotification(
                                        request.getUserId(),
                                        "Tài khoản đã được khôi phục",
                                        "Tài khoản mentor của bạn đã được Admin kích hoạt trở lại. "
                                                        + "Các khóa học và ví của bạn đã hoạt động bình thường.",
                                        NotificationType.SYSTEM,
                                        request.getUserId().toString());
                        log.info("Notified unbanned mentor {}", request.getUserId());
                }
                // ========== End Cascade ==========

                User updatedUser = userRepository.save(user);

                log.info("Successfully updated user status for userId: {}", request.getUserId());
                return convertToAdminUserResponse(updatedUser);
        }

        @Override
        @Transactional
        public AdminUserResponse updateUserRole(UpdateUserRoleRequest request) {
                log.info("Updating user role - userId: {}, newRole: {}", request.getUserId(), request.getPrimaryRole());

                // Self-change guard: Prevent user from changing their own role
                Long currentUserId = getCurrentUserId();
                if (currentUserId.equals(request.getUserId())) {
                        throw new ForbiddenException("Không thể tự thay đổi vai trò của chính mình");
                }

                PrimaryRole newPrimaryRole = request.getPrimaryRole();

                // Null safety guard
                if (newPrimaryRole == null) {
                        throw new BadRequestException("Vai trò chính là bắt buộc");
                }

                // Validate: Chỉ cho phép main roles (USER, MENTOR, RECRUITER, PARENT, ADMIN)
                // Sub-admin roles phải được gán qua setSubAdminRoles
                if (!newPrimaryRole.isMainRole()) {
                        throw new BadRequestException("Vai trò không hợp lệ: " + newPrimaryRole + 
                                ". Chỉ các vai trò chính (USER, MENTOR, RECRUITER, PARENT, ADMIN) được phép. " +
                                "Các vai trò phụ trợ phải được gán qua endpoint vai trò phụ trợ.");
                }

                // ✅ Use findByIdWithRoles to ensure roles are loaded for synchronization
                User user = userRepository.findByIdWithRoles(request.getUserId())
                                .orElseThrow(() -> new NotFoundException(
                                                "Không tìm thấy người dùng với ID: " + request.getUserId()));

                // ADMIN protection: Cannot modify another ADMIN's role (unless self, which is already blocked)
                if (user.getPrimaryRole() == PrimaryRole.ADMIN && !currentUserId.equals(request.getUserId())) {
                        throw new ForbiddenException("Không thể thay đổi vai trò của quản trị viên cấp cao");
                }

                PrimaryRole oldPrimaryRole = user.getPrimaryRole();
                
                // Defense-in-depth; strict ADMIN target guards normally prevent this path
                if (oldPrimaryRole == PrimaryRole.ADMIN && request.getPrimaryRole() != PrimaryRole.ADMIN) {
                        Long activeAdminCount = userRepository.countByPrimaryRoleAndStatus(PrimaryRole.ADMIN, UserStatus.ACTIVE);
                        if (activeAdminCount <= 1) {
                                throw new BadRequestException("Không thể hạ cấp quản trị viên cuối cùng đang hoạt động");
                        }
                }
                
                // ✅ SYNC: Remove ALL existing main roles, then add new one
                // Rule: User has exactly one main role matching primaryRole
                Set<String> mainRoleNames = Set.of("USER", "MENTOR", "RECRUITER", "PARENT", "ADMIN");
                Set<Role> rolesToRemove = user.getRoles().stream()
                                .filter(r -> mainRoleNames.contains(r.getName()))
                                .collect(Collectors.toSet());
                user.getRoles().removeAll(rolesToRemove);
                log.info("Removed all main roles from user {}: {}", request.getUserId(), 
                        rolesToRemove.stream().map(Role::getName).collect(Collectors.toList()));

                // Rule: If demoting from ADMIN, remove all sub-admin roles
                if (oldPrimaryRole == PrimaryRole.ADMIN && newPrimaryRole != PrimaryRole.ADMIN) {
                        Set<String> subAdminNames = PrimaryRole.subAdminRoleNames();
                        Set<Role> subAdminRolesToRemove = user.getRoles().stream()
                                        .filter(r -> subAdminNames.contains(r.getName()))
                                        .collect(Collectors.toSet());
                        user.getRoles().removeAll(subAdminRolesToRemove);
                        log.info("Demoting from ADMIN - removed sub-admin roles from user {}: {}", request.getUserId(),
                                subAdminRolesToRemove.stream().map(Role::getName).collect(Collectors.toList()));
                }
                
                // Add new main role
                Role newRole = roleRepository.findByName(newPrimaryRole.name())
                                .orElseThrow(() -> new NotFoundException("Không tìm thấy vai trò: " + newPrimaryRole.name()));
                user.getRoles().add(newRole);
                
                // Update PrimaryRole enum
                user.setPrimaryRole(newPrimaryRole);
                
                user.setUpdatedAt(LocalDateTime.now());
                User updatedUser = userRepository.save(user);

                // Invalidate refresh session; existing access token keeps its current claims until expiry
                refreshTokenRepository.deleteByUserId(request.getUserId());
                log.info("Invalidated refresh token for user {} due to primary role change", request.getUserId());

                log.info("Successfully updated user role for userId: {} - old: {}, new: {}", 
                        request.getUserId(), oldPrimaryRole, newPrimaryRole);
                return convertToAdminUserResponse(updatedUser);
        }
        

        @Override
        @Transactional
        public AdminUserResponse setSubAdminRoles(AddRoleRequest request) {
                log.info("Setting sub-admin roles for user - userId: {}, roles: {}", request.getUserId(), request.getRoles());

                // Self-edit check: Lấy current user ID và fail closed nếu không có
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication == null || !authentication.isAuthenticated()) {
                        throw new ForbiddenException("Yêu cầu xác thực để thay đổi quyền");
                }
                Long currentUserId = JwtUtils.extractUserId(authentication);
                if (currentUserId.equals(request.getUserId())) {
                        throw new ForbiddenException("Không thể tự thay đổi quyền của chính mình");
                }

                // ✅ Use findByIdWithRoles to eagerly fetch roles collection for modification
                User user = userRepository.findByIdWithRoles(request.getUserId())
                                .orElseThrow(() -> new NotFoundException(
                                                "Không tìm thấy người dùng với ID: " + request.getUserId()));

                // ADMIN protection: Cannot modify another ADMIN's sub-admin roles
                if (user.getPrimaryRole() == PrimaryRole.ADMIN && !currentUserId.equals(request.getUserId())) {
                        throw new ForbiddenException("Không thể thay đổi quyền của quản trị viên cấp cao");
                }

                // Validate and filter roles - chỉ cho phép sub-admin roles
                List<String> requestedRoles = request.getRoles() != null ? request.getRoles() : List.of();
                Set<String> subAdminRoles = PrimaryRole.subAdminRoleNames();
                for (String roleName : requestedRoles) {
                        if (!subAdminRoles.contains(roleName)) {
                                throw new BadRequestException("Vai trò không hợp lệ: " + roleName + 
                                        ". Chỉ các vai trò phụ trợ (USER_ADMIN, CONTENT_ADMIN, COMMUNITY_ADMIN, " +
                                        "FINANCE_ADMIN, PREMIUM_ADMIN, AI_ADMIN, SUPPORT_ADMIN, SYSTEM_ADMIN) được phép.");
                        }
                }

                // Get current sub-admin roles
                Set<String> subAdminRoleNames = PrimaryRole.subAdminRoleNames();
                Set<Role> currentSubAdminRoles = user.getRoles().stream()
                                .filter(r -> subAdminRoleNames.contains(r.getName()))
                                .collect(Collectors.toSet());

                // Get target roles from request
                Set<Role> targetRoles = requestedRoles.stream()
                                .map(name -> roleRepository.findByName(name)
                                                .orElseThrow(() -> new NotFoundException("Không tìm thấy vai trò: " + name)))
                                .collect(Collectors.toSet());

                // Replace: remove old sub-admin roles, add new ones
                user.getRoles().removeAll(currentSubAdminRoles);
                user.getRoles().addAll(targetRoles);

                user.setUpdatedAt(LocalDateTime.now());
                User updatedUser = userRepository.save(user);

                // Invalidate refresh session; existing access token remains valid until expiry
                refreshTokenRepository.deleteByUserId(request.getUserId());
                log.info("Invalidated refresh token for user {} due to sub-admin role change", request.getUserId());

                log.info("Successfully set sub-admin roles for userId: {}. New roles: {}", 
                         request.getUserId(), 
                         updatedUser.getRoles().stream().map(Role::getName).collect(Collectors.toList()));
                return convertToAdminUserResponse(updatedUser);
        }

        @Override
        @Transactional
        public void deleteUser(Long userId) {
                log.info("Deleting user with userId: {}", userId);

                // Self-delete guard: Prevent user from soft-deleting themselves
                Long currentUserId = getCurrentUserId();
                if (currentUserId.equals(userId)) {
                        throw new ForbiddenException("Không thể tự xóa tài khoản của chính mình");
                }

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng với ID: " + userId));

                // ADMIN protection: Cannot delete another ADMIN
                if (user.getPrimaryRole() == PrimaryRole.ADMIN) {
                        throw new ForbiddenException("Không thể xóa tài khoản quản trị viên cấp cao");
                }

                // Defense-in-depth; strict ADMIN target guards normally prevent this path
                if (user.getPrimaryRole() == PrimaryRole.ADMIN) {
                        Long activeAdminCount = userRepository.countByPrimaryRoleAndStatus(PrimaryRole.ADMIN, UserStatus.ACTIVE);
                        if (activeAdminCount <= 1) {
                                throw new BadRequestException("Không thể xóa quản trị viên cuối cùng đang hoạt động");
                        }
                }

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
                                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng với ID: " + userId));

                // USER_ADMIN không thể xem tài khoản ADMIN
                if (isUserAdminOnly() && user.getPrimaryRole() == PrimaryRole.ADMIN) {
                        throw new ForbiddenException("Từ chối truy cập: Không thể xem tài khoản quản trị viên cấp cao");
                }

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

                AdminUserDetailResponse.AdminUserDetailResponseBuilder builder = AdminUserDetailResponse.builder()
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
                                .coursesEnrolled(user.getEnrollments() != null ? (long) user.getEnrollments().size() : 0L)
                                .certificatesEarned(user.getCertificates() != null ? (long) user.getCertificates().size() : 0L)
                                .totalSpent(0L) // TODO: Calculate from purchases
                                .totalEarned(0L) // TODO: Calculate from earnings
                                .loginCount(0) // TODO: Track login count
                                .lastLoginAt(user.getUpdatedAt())
                                .lastLoginIp(null) // TODO: Track IP
                                .recentCourses(recentCourses)
                                .recentCertificates(recentCertificates);

                // Populate Mentor/Recruiter specific fields
                if (user.getPrimaryRole() == PrimaryRole.MENTOR) {
                        mentorProfileRepository.findByUserId(userId).ifPresent(mentor -> {
                                builder.cccdNumber(mentor.getCccdNumber())
                                       .cccdExtractedData(mentor.getCccdExtractedData())
                                       .identityVerified(mentor.getIdentityVerified())
                                       .mentorSkills(mentor.getSkills())
                                       .mentorExpertise(mentor.getMainExpertiseAreas())
                                       .yearsOfExperience(mentor.getYearsOfExperience())
                                       .bio(mentor.getBio());
                        });
                } else if (user.getPrimaryRole() == PrimaryRole.RECRUITER) {
                        recruiterProfileRepository.findByUserId(userId).ifPresent(recruiter -> {
                                builder.companyName(recruiter.getCompanyName())
                                       .taxCode(recruiter.getTaxCodeOrBusinessRegistrationNumber())
                                       .industry(recruiter.getIndustry())
                                       .businessLicenseUrl(recruiter.getCompanyDocumentsUrl())
                                       .companyVerified(com.exe.skillverse_backend.mentor_service.entity.ApplicationStatus.APPROVED.equals(recruiter.getApplicationStatus()))
                                       .bio(recruiter.getCompanyAddress());
                        });
                }

                return builder.build();
        }

        @Override
        @Transactional
        public AdminUserResponse updateUserProfile(UpdateUserProfileRequest request) {
                log.info("Updating user profile - userId: {}", request.getUserId());

                User user = userRepository.findById(request.getUserId())
                                .orElseThrow(() -> new NotFoundException(
                                                "Không tìm thấy người dùng với ID: " + request.getUserId()));

                // Strict ADMIN protection: No actor can modify another ADMIN account
                Long currentUserId = getCurrentUserId();
                if (user.getPrimaryRole() == PrimaryRole.ADMIN && !currentUserId.equals(request.getUserId())) {
                        throw new ForbiddenException("Không thể cập nhật tài khoản quản trị viên cấp cao");
                }

                StringBuilder changes = new StringBuilder("Cập nhật hồ sơ: ");

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
                                throw new BadRequestException("Email đã tồn tại: " + request.getEmail());
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

                // ADMIN protection: Cannot reset another ADMIN's password
                Long currentUserId = getCurrentUserId();
                User user = userRepository.findById(request.getUserId())
                                .orElseThrow(() -> new NotFoundException(
                                                "Không tìm thấy người dùng với ID: " + request.getUserId()));

                if (user.getPrimaryRole() == PrimaryRole.ADMIN && !currentUserId.equals(request.getUserId())) {
                        throw new ForbiddenException("Không thể đặt lại mật khẩu của quản trị viên cấp cao");
                }

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

                // Self-delete guard: Prevent user from permanently deleting themselves
                Long currentUserId = getCurrentUserId();
                if (currentUserId.equals(userId)) {
                        throw new ForbiddenException("Không thể tự xóa vĩnh viễn tài khoản của chính mình");
                }

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng với ID: " + userId));

                // ADMIN protection: Cannot permanently delete another ADMIN
                if (user.getPrimaryRole() == PrimaryRole.ADMIN) {
                        throw new ForbiddenException("Không thể xóa vĩnh viễn tài khoản quản trị viên cấp cao");
                }

                // Only allow permanent deletion for INACTIVE users
                if (user.getStatus() == UserStatus.ACTIVE) {
                        throw new BadRequestException(
                                        "Không thể xóa vĩnh viễn tài khoản đang hoạt động. Vui lòng khóa tài khoản trước.");
                }

                // Defense-in-depth; strict ADMIN target guards normally prevent this path
                if (user.getPrimaryRole() == PrimaryRole.ADMIN) {
                        Long activeAdminCount = userRepository.countByPrimaryRoleAndStatus(PrimaryRole.ADMIN, UserStatus.ACTIVE);
                        if (activeAdminCount <= 1) {
                                throw new BadRequestException("Không thể xóa vĩnh viễn quản trị viên cuối cùng đang hoạt động");
                        }
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
                        entityManager.createNativeQuery(
                                        "DELETE FROM chat_messages WHERE user_id = ?1 " +
                                                        "OR session_id IN (SELECT id FROM chat_sessions WHERE user_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM chat_sessions WHERE user_id = ?1")
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
                        entityManager.createNativeQuery(
                                        "DELETE FROM project_attachments WHERE project_id IN (SELECT id FROM portfolio_projects WHERE user_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM project_outcomes WHERE project_id IN (SELECT id FROM portfolio_projects WHERE user_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM project_tools WHERE project_id IN (SELECT id FROM portfolio_projects WHERE user_id = ?1)")
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
                                        "DELETE FROM recruitment_messages WHERE sender_id = ?1 " +
                                                        "OR session_id IN (SELECT id FROM recruitment_sessions WHERE recruiter_id = ?1 OR candidate_id = ?1 " +
                                                        "OR job_posting_id IN (SELECT id FROM job_postings WHERE recruiter_id = ?1) " +
                                                        "OR short_term_job_id IN (SELECT id FROM short_term_jobs WHERE recruiter_id = ?1))")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM recruitment_sessions WHERE recruiter_id = ?1 OR candidate_id = ?1 " +
                                                        "OR job_posting_id IN (SELECT id FROM job_postings WHERE recruiter_id = ?1) " +
                                                        "OR short_term_job_id IN (SELECT id FROM short_term_jobs WHERE recruiter_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM recruiter_shortlists WHERE candidate_id = ?1 OR recruiter_id = ?1 " +
                                                        "OR job_posting_id IN (SELECT id FROM job_postings WHERE recruiter_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM candidate_match_scores WHERE candidate_id = ?1 " +
                                                        "OR job_posting_id IN (SELECT id FROM job_postings WHERE recruiter_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM job_boosts WHERE recruiter_id = ?1 OR created_by = ?1 " +
                                                        "OR job_posting_id IN (SELECT id FROM job_postings WHERE recruiter_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM interview_schedules WHERE application_id IN " +
                                                        "(SELECT id FROM job_applications WHERE user_id = ?1 " +
                                                        "OR job_posting_id IN (SELECT id FROM job_postings WHERE recruiter_id = ?1))")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM job_contracts WHERE application_id IN " +
                                                        "(SELECT id FROM job_applications WHERE user_id = ?1 " +
                                                        "OR job_posting_id IN (SELECT id FROM job_postings WHERE recruiter_id = ?1))")
                                        .setParameter(1, userId).executeUpdate();
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

                        // User Service (remaining)
                        entityManager.createNativeQuery("DELETE FROM user_skills WHERE user_id = ?1")
                                        .setParameter(1, userId).executeUpdate();

                        // Course Service
                        entityManager.createNativeQuery(
                                        "DELETE FROM group_chat_messages WHERE sender_id = ?1 " +
                                                        "OR group_id IN (SELECT gc.id FROM group_chats gc WHERE gc.mentor_id = ?1 " +
                                                        "OR gc.course_id IN (SELECT id FROM courses WHERE author_id = ?1))")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM group_chat_members WHERE user_id = ?1 " +
                                                        "OR group_id IN (SELECT gc.id FROM group_chats gc WHERE gc.mentor_id = ?1 " +
                                                        "OR gc.course_id IN (SELECT id FROM courses WHERE author_id = ?1))")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM group_chats WHERE mentor_id = ?1 " +
                                                        "OR course_id IN (SELECT id FROM courses WHERE author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();

                        entityManager.createNativeQuery(
                                        "DELETE FROM lesson_progress WHERE user_id = ?1 " +
                                                        "OR lesson_id IN (SELECT l.id FROM lessons l " +
                                                        "JOIN modules m ON l.module_id = m.id " +
                                                        "JOIN courses c ON m.course_id = c.id WHERE c.author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM module_progress WHERE user_id = ?1 " +
                                                        "OR module_id IN (SELECT m.id FROM modules m " +
                                                        "JOIN courses c ON m.course_id = c.id WHERE c.author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM certificates WHERE user_id = ?1 " +
                                                        "OR course_id IN (SELECT id FROM courses WHERE author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM course_purchase WHERE user_id = ?1 " +
                                                        "OR course_id IN (SELECT id FROM courses WHERE author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM course_enrollment WHERE user_id = ?1 " +
                                                        "OR course_id IN (SELECT id FROM courses WHERE author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM submission_criteria_scores WHERE submission_id IN " +
                                                        "(SELECT id FROM assignment_submissions WHERE user_id = ?1 OR graded_by = ?1 " +
                                                        "OR assignment_id IN (SELECT a.id FROM assignments a " +
                                                        "JOIN modules m ON a.module_id = m.id " +
                                                        "JOIN courses c ON m.course_id = c.id WHERE c.author_id = ?1))")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM assignment_submissions WHERE user_id = ?1 OR graded_by = ?1 " +
                                                        "OR assignment_id IN (SELECT a.id FROM assignments a " +
                                                        "JOIN modules m ON a.module_id = m.id " +
                                                        "JOIN courses c ON m.course_id = c.id WHERE c.author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM assignment_criteria WHERE assignment_id IN (SELECT a.id FROM assignments a " +
                                                        "JOIN modules m ON a.module_id = m.id " +
                                                        "JOIN courses c ON m.course_id = c.id WHERE c.author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM assignments WHERE module_id IN (SELECT m.id FROM modules m " +
                                                        "JOIN courses c ON m.course_id = c.id WHERE c.author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM quiz_attempt_answer_snapshots WHERE attempt_id IN " +
                                                        "(SELECT qa.id FROM quiz_attempts qa WHERE qa.user_id = ?1 " +
                                                        "OR qa.quiz_id IN (SELECT q.id FROM quizzes q " +
                                                        "JOIN modules m ON q.module_id = m.id " +
                                                        "JOIN courses c ON m.course_id = c.id WHERE c.author_id = ?1))")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM quiz_attempt_sessions WHERE user_id = ?1 " +
                                                        "OR quiz_id IN (SELECT q.id FROM quizzes q " +
                                                        "JOIN modules m ON q.module_id = m.id " +
                                                        "JOIN courses c ON m.course_id = c.id WHERE c.author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM quiz_attempts WHERE user_id = ?1 " +
                                                        "OR quiz_id IN (SELECT q.id FROM quizzes q " +
                                                        "JOIN modules m ON q.module_id = m.id " +
                                                        "JOIN courses c ON m.course_id = c.id WHERE c.author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM quiz_options WHERE question_id IN (SELECT qq.id FROM quiz_questions qq " +
                                                        "JOIN quizzes q ON qq.quiz_id = q.id " +
                                                        "JOIN modules m ON q.module_id = m.id " +
                                                        "JOIN courses c ON m.course_id = c.id WHERE c.author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM quiz_questions WHERE quiz_id IN (SELECT q.id FROM quizzes q " +
                                                        "JOIN modules m ON q.module_id = m.id " +
                                                        "JOIN courses c ON m.course_id = c.id WHERE c.author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM quizzes WHERE module_id IN (SELECT m.id FROM modules m " +
                                                        "JOIN courses c ON m.course_id = c.id WHERE c.author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM coding_submissions WHERE user_id = ?1 " +
                                                        "OR exercise_id IN (SELECT ce.id FROM coding_exercises ce " +
                                                        "JOIN modules m ON ce.module_id = m.id " +
                                                        "JOIN courses c ON m.course_id = c.id WHERE c.author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM coding_test_cases WHERE exercise_id IN (SELECT ce.id FROM coding_exercises ce " +
                                                        "JOIN modules m ON ce.module_id = m.id " +
                                                        "JOIN courses c ON m.course_id = c.id WHERE c.author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM coding_exercises WHERE module_id IN (SELECT m.id FROM modules m " +
                                                        "JOIN courses c ON m.course_id = c.id WHERE c.author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM lesson_attachments WHERE lesson_id IN (SELECT l.id FROM lessons l " +
                                                        "JOIN modules m ON l.module_id = m.id " +
                                                        "JOIN courses c ON m.course_id = c.id WHERE c.author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM lessons WHERE module_id IN (SELECT m.id FROM modules m " +
                                                        "JOIN courses c ON m.course_id = c.id WHERE c.author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM course_skill WHERE course_id IN (SELECT id FROM courses WHERE author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM course_revisions WHERE course_id IN (SELECT id FROM courses WHERE author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM course_learning_objectives WHERE course_id IN (SELECT id FROM courses WHERE author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM course_requirements WHERE course_id IN (SELECT id FROM courses WHERE author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM modules WHERE course_id IN (SELECT id FROM courses WHERE author_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM courses WHERE author_id = ?1")
                                        .setParameter(1, userId).executeUpdate();

                        // Detach remaining cross-user references to this user's media before deleting it.
                        entityManager.createNativeQuery(
                                        "UPDATE courses SET thumbnail_media_id = NULL WHERE thumbnail_media_id IN (SELECT id FROM media WHERE uploaded_by = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "UPDATE lessons SET video_media_id = NULL WHERE video_media_id IN (SELECT id FROM media WHERE uploaded_by = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "UPDATE lesson_attachments SET media_id = NULL WHERE media_id IN (SELECT id FROM media WHERE uploaded_by = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery(
                                        "UPDATE assignment_submissions SET file_media_id = NULL WHERE file_media_id IN (SELECT id FROM media WHERE uploaded_by = ?1)")
                                        .setParameter(1, userId).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM media WHERE uploaded_by = ?1")
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
                        entityManager.createNativeQuery(
                                        "DELETE FROM quiz_attempt_answer_snapshots WHERE attempt_id IN (SELECT id FROM quiz_attempts WHERE user_id = ?1)")
                                        .setParameter(1, userId).executeUpdate();
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
         * Check if current user is USER_ADMIN only (not full ADMIN).
         * Used to enforce that USER_ADMIN cannot view/modify super admin accounts.
         */
        private boolean isUserAdminOnly() {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication == null || !authentication.isAuthenticated()) {
                        return false;
                }
                boolean hasUserAdmin = authentication.getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equals("ROLE_USER_ADMIN"));
                boolean hasAdmin = authentication.getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                return hasUserAdmin && !hasAdmin;
        }

        /**
         * Check if target user has ADMIN primary role.
         * Used to prevent USER_ADMIN from accessing/modifying super admin accounts.
         */
        private boolean isTargetAdmin(Long userId) {
                return userRepository.findById(userId)
                                .map(user -> user.getPrimaryRole() == PrimaryRole.ADMIN)
                                .orElse(false);
        }

        /**
         * Get current authenticated user ID from security context.
         * Used for self-check guards (prevent self-role-change, self-delete, etc.)
         */
        private Long getCurrentUserId() {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication == null || !authentication.isAuthenticated()) {
                        throw new ForbiddenException("Yêu cầu xác thực");
                }
                return JwtUtils.extractUserId(authentication);
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
