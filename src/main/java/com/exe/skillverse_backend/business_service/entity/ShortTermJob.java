package com.exe.skillverse_backend.business_service.entity;

import com.exe.skillverse_backend.business_service.entity.enums.*;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity cho Short-term Job Posting (Gig/Freelance)
 * Cho phép ứng tuyển và làm việc ngay lập tức
 */
@Entity
@Table(name = "short_term_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortTermJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "required_skills", columnDefinition = "TEXT", nullable = false)
    private String requiredSkills; // JSON array stored as TEXT

    // ==================== PRICING ====================
    
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal budget;

    @Builder.Default
    @Column(name = "is_negotiable")
    private Boolean isNegotiable = false;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod = PaymentMethod.FIXED;

    // ==================== TIMING ====================
    
    @Column(nullable = false)
    private LocalDateTime deadline;

    @Column(name = "estimated_duration", length = 50)
    private String estimatedDuration; // e.g., "2 hours", "1 day"

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private JobUrgency urgency = JobUrgency.NORMAL;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    // ==================== WORK SETTINGS ====================
    
    @Builder.Default
    @Column(name = "is_remote", nullable = false)
    private Boolean isRemote = true;

    @Builder.Default
    @Column(name = "is_highlighted")
    private Boolean isHighlighted = false;

    @Builder.Default
    @Column(name = "paid_via_subscription")
    private Boolean paidViaSubscription = false;

    @Column(length = 500)
    private String location;

    // ==================== STATUS ====================
    
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShortTermJobStatus status = ShortTermJobStatus.DRAFT;

    @Builder.Default
    @Column(name = "applicant_count", nullable = false)
    private Integer applicantCount = 0;

    @Column(name = "selected_applicant_id")
    private Long selectedApplicantId;

    // ==================== REQUIREMENTS ====================
    
    @Column(name = "max_applicants")
    private Integer maxApplicants;

    @Column(name = "min_rating", precision = 3, scale = 2)
    private BigDecimal minRating; // Yêu cầu rating tối thiểu

    // ==================== RELATIONSHIPS ====================
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruiter_id", nullable = false)
    private RecruiterProfile recruiterProfile;

    @OneToMany(mappedBy = "shortTermJob", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ShortTermJobMilestone> milestones = new ArrayList<>();

    @OneToMany(mappedBy = "shortTermJob", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ShortTermJobApplication> applications = new ArrayList<>();

    // ==================== TIMESTAMPS ====================
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ==================== HELPER METHODS ====================
    
    public void addMilestone(ShortTermJobMilestone milestone) {
        milestones.add(milestone);
        milestone.setShortTermJob(this);
    }

    public void removeMilestone(ShortTermJobMilestone milestone) {
        milestones.remove(milestone);
        milestone.setShortTermJob(null);
    }

    public boolean isExpired() {
        return deadline != null && deadline.isBefore(LocalDateTime.now());
    }

    public boolean canApply() {
        return status == ShortTermJobStatus.PUBLISHED && !isExpired();
    }
}
