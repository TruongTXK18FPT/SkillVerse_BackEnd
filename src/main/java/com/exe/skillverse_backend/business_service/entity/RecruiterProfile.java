package com.exe.skillverse_backend.business_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.mentor_service.entity.ApplicationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "recruiter_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecruiterProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @MapsId
    private User user;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "company_size")
    private String companySize; // e.g., "1-10", "11-50", "51-200", etc.

    @Column(name = "industry")
    private String industry;

    @Column(name = "job_title", nullable = false)
    private String jobTitle;

    @Column(name = "company_website")
    private String companyWebsite;

    @Column(name = "company_description", columnDefinition = "TEXT")
    private String companyDescription;

    @Column(name = "work_email")
    private String workEmail;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "company_address", columnDefinition = "TEXT")
    private String companyAddress;

    @Column(name = "verification_document_url")
    private String verificationDocumentUrl; // Business card, employment letter, etc.

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