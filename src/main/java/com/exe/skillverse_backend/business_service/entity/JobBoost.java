package com.exe.skillverse_backend.business_service.entity;

import com.exe.skillverse_backend.business_service.entity.enums.JobBoostStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing a premium job boost.
 * When a job is boosted, it appears at the top of job listings with premium styling.
 */
@Entity
@Table(name = "job_boosts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobBoost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false, unique = true)
    private JobPosting jobPosting;

    @Column(name = "recruiter_id", nullable = false)
    private Long recruiterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "boost_status", nullable = false, length = 20)
    @Builder.Default
    private JobBoostStatus boostStatus = JobBoostStatus.ACTIVE;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "scheduled_start_at")
    private LocalDateTime scheduledStartAt;

    // Analytics fields
    @Column(name = "impressions", nullable = false)
    @Builder.Default
    private Integer impressions = 0;

    @Column(name = "clicks", nullable = false)
    @Builder.Default
    private Integer clicks = 0;

    @Column(name = "applications", nullable = false)
    @Builder.Default
    private Integer applications = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Check if the boost is currently active
     */
    public boolean isActive() {
        return boostStatus == JobBoostStatus.ACTIVE
            && LocalDateTime.now().isBefore(expiresAt)
            && (scheduledStartAt == null || LocalDateTime.now().isAfter(scheduledStartAt));
    }

    /**
     * Check if the boost has expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if the boost is scheduled for future
     */
    public boolean isScheduled() {
        return boostStatus == JobBoostStatus.SCHEDULED
            && scheduledStartAt != null
            && LocalDateTime.now().isBefore(scheduledStartAt);
    }

    /**
     * Get remaining boost time in minutes
     */
    public long getRemainingMinutes() {
        if (!isActive()) {
            return 0;
        }
        return java.time.Duration.between(LocalDateTime.now(), expiresAt).toMinutes();
    }

    /**
     * Increment impressions count
     */
    public void incrementImpressions() {
        this.impressions = (impressions != null ? impressions : 0) + 1;
    }

    /**
     * Increment clicks count
     */
    public void incrementClicks() {
        this.clicks = (clicks != null ? clicks : 0) + 1;
    }

    /**
     * Increment applications count
     */
    public void incrementApplications() {
        this.applications = (applications != null ? applications : 0) + 1;
    }

    /**
     * Calculate click-through rate
     */
    public double getClickThroughRate() {
        if (impressions == null || impressions == 0) {
            return 0.0;
        }
        return (double) clicks / impressions;
    }

    /**
     * Calculate application conversion rate
     */
    public double getApplicationConversionRate() {
        if (impressions == null || impressions == 0) {
            return 0.0;
        }
        return (double) applications / impressions;
    }
}
