package com.exe.skillverse_backend.admin_service.dto.response;

import com.exe.skillverse_backend.auth_service.entity.AuthProvider;
import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Detailed DTO for admin user detail modal/page
 * Includes more information than the list response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserDetailResponse {
    // Basic Info
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String phoneNumber;
    private PrimaryRole primaryRole;
    private UserStatus status;
    private boolean isEmailVerified;
    private AuthProvider authProvider;
    private boolean googleLinked;
    
    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastActive;
    
    // Profile
    private String avatarUrl;
    private String bio;
    
    // Statistics
    private Long coursesCreated;
    private Long coursesEnrolled;
    private Long certificatesEarned;
    private Long totalSpent; // Total money spent
    private Long totalEarned; // Total money earned (for mentors/recruiters)
    
    // Activity
    private Integer loginCount;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    
    // Recent courses (top 5)
    private List<UserCourseInfo> recentCourses;
    
    // Recent certificates (top 5)
    private List<UserCertificateInfo> recentCertificates;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserCourseInfo {
        private Long courseId;
        private String courseTitle;
        private String courseThumbnail;
        private LocalDateTime enrolledAt;
        private Integer progress; // 0-100
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserCertificateInfo {
        private Long certificateId;
        private String courseName;
        private LocalDateTime issuedAt;
        private String certificateUrl;
    }
}
