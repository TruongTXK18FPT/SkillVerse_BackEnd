package com.exe.skillverse_backend.mentor_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "mentor_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @MapsId
    private User user;

    // Basic Information (from form)
    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "linkedin_profile")
    private String linkedinProfile; // Optional

    // Expertise & Experience (from form)
    @Column(name = "main_expertise_areas", columnDefinition = "TEXT", nullable = false)
    private String mainExpertiseAreas; // Main specialization field from dropdown

    @Column(name = "years_of_experience", nullable = false)
    private Integer yearsOfExperience;

    @Column(name = "personal_profile", columnDefinition = "TEXT", nullable = false)
    private String personalProfile; // Personal achievements and experience description

    // Documents (from form)
    @Column(name = "cv_portfolio_url")
    private String cvPortfolioUrl; // File upload for CV/Portfolio

    @Column(name = "certificates_url")
    private String certificatesUrl; // File upload for certificates

    // Additional profile fields for frontend compatibility
    @Column(name = "avatar_url")
    private String avatarUrl; // Avatar image URL

    @Column(name = "github_profile")
    private String githubProfile; // GitHub profile URL

    @Column(name = "website_url")
    private String websiteUrl; // Personal website URL

    @Column(name = "skills", columnDefinition = "TEXT")
    private String skills; // JSON array of skills

    @Column(name = "achievements", columnDefinition = "TEXT")
    private String achievements; // JSON array of achievements

    // Gamification fields
    @Builder.Default
    @Column(name = "skill_points", nullable = false, columnDefinition = "integer default 0")
    private Integer skillPoints = 0;

    @Builder.Default
    @Column(name = "current_level", nullable = false, columnDefinition = "integer default 0")
    private Integer currentLevel = 0;

    @Column(name = "badges", columnDefinition = "TEXT")
    private String badges; // JSON array of awarded badges


    // Application Status & Admin Fields
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "application_status", nullable = false)
    private ApplicationStatus applicationStatus = ApplicationStatus.PENDING;

    @Builder.Default
    @Column(name = "pre_chat_enabled")
    private Boolean preChatEnabled = true;

    @Builder.Default
    @Column(name = "rating_average", nullable = false, columnDefinition = "float default 0.0")
    private Double ratingAverage = 0.0;

    @Builder.Default
    @Column(name = "rating_count", nullable = false, columnDefinition = "integer default 0")
    private Integer ratingCount = 0;

    @Column(name = "application_date", nullable = false)
    @Builder.Default
    private LocalDateTime applicationDate = LocalDateTime.now();

    @Column(name = "approval_date")
    private LocalDateTime approvalDate;

    @Column(name = "approved_by")
    private Long approvedBy; // Admin user ID

    @Column(name = "rejection_reason")
    private String rejectionReason;

    // Legacy fields (keep for backward compatibility but deprecate)
    @Deprecated
    @Column(name = "expertise_areas", columnDefinition = "TEXT")
    private String expertiseAreas; // JSON array of expertise areas

    @Deprecated
    @Column(name = "certifications", columnDefinition = "TEXT")
    private String certifications; // JSON array of certifications

    @Deprecated
    @Column(name = "hourly_rate")
    private Double hourlyRate;

    @Deprecated
    @Column(name = "availability", columnDefinition = "TEXT")
    private String availability; // JSON for availability schedule

    @Deprecated
    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Deprecated
    @Column(name = "languages_spoken")
    private String languagesSpoken; // JSON array

    @Deprecated
    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Deprecated
    @Column(name = "portfolio_url")
    private String portfolioUrl;

    // Timestamps
    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
