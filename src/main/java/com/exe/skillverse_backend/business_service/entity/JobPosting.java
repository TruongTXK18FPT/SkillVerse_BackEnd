package com.exe.skillverse_backend.business_service.entity;

import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruiter_id", nullable = false)
    private RecruiterProfile recruiterProfile;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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
