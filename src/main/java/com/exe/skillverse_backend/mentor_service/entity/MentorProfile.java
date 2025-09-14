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

    @Column(name = "expertise_areas", columnDefinition = "TEXT")
    private String expertiseAreas; // JSON array of expertise areas

    @Column(name = "certifications", columnDefinition = "TEXT")
    private String certifications; // JSON array of certifications

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    @Column(name = "hourly_rate")
    private Double hourlyRate;

    @Column(name = "availability", columnDefinition = "TEXT")
    private String availability; // JSON for availability schedule

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "languages_spoken")
    private String languagesSpoken; // JSON array

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "portfolio_url")
    private String portfolioUrl;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "application_status", nullable = false)
    private ApplicationStatus applicationStatus = ApplicationStatus.PENDING;

    @Column(name = "application_date", nullable = false)
    @Builder.Default
    private LocalDateTime applicationDate = LocalDateTime.now();

    @Column(name = "approval_date")
    private LocalDateTime approvalDate;

    @Column(name = "approved_by")
    private Long approvedBy; // Admin user ID

    @Column(name = "rejection_reason")
    private String rejectionReason;

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