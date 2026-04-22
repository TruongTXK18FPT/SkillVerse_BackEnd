package com.exe.skillverse_backend.business_service.entity;

import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "job_postings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "required_skills", columnDefinition = "TEXT", nullable = false)
    private String requiredSkills; // JSON array stored as TEXT: ["java","spring boot","react"]

    @Column(name = "primary_skill")
    private String primarySkill; // The most important skill chosen by recruiter

    @Column(name = "min_budget", nullable = false, precision = 15, scale = 2)
    private BigDecimal minBudget;

    @Column(name = "max_budget", nullable = false, precision = 15, scale = 2)
    private BigDecimal maxBudget;

    @Column(nullable = false)
    private LocalDate deadline;

    @Column(name = "is_remote", nullable = false)
    private Boolean isRemote = true; // Default to remote

    @Column(length = 500)
    private String location; // Nullable - required only if isRemote = false

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status = JobStatus.IN_PROGRESS; // Default to IN_PROGRESS

    @Column(name = "applicant_count", nullable = false)
    private Integer applicantCount = 0; // Default to 0

    // Enhanced fields (TopCV inspired)
    @Column(name = "experience_level", length = 50)
    private String experienceLevel; // e.g. "Junior", "Senior", "Intern", "All Levels"

    @Column(name = "job_type", length = 50)
    private String jobType; // e.g. "FULL_TIME", "PART_TIME", "CONTRACT", "FREELANCE"

    @Column(name = "hiring_quantity")
    private Integer hiringQuantity; // Number of positions to fill

    @Column(columnDefinition = "TEXT")
    private String benefits; // JSON array or text description of benefits

    // More TopCV-like fields
    @Column(name = "gender_requirement", length = 20)
    private String genderRequirement; // "MALE", "FEMALE", "ANY"

    @Column(name = "is_negotiable")
    private Boolean isNegotiable = false;

    @Column(name = "is_highlighted")
    private Boolean isHighlighted = false;

    @Column(name = "paid_via_subscription")
    private Boolean paidViaSubscription = false;

    @Column(name = "posting_fee_charged")
    private Boolean postingFeeCharged = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruiter_id", nullable = false)
    private RecruiterProfile recruiterProfile;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
