package com.exe.skillverse_backend.business_service.entity;

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
 * Milestone cho Short-term Job phức tạp
 */
@Entity
@Table(name = "short_term_job_milestones")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortTermJobMilestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "short_term_job_id", nullable = false)
    private ShortTermJob shortTermJob;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount; // Số tiền cho milestone

    @Column(nullable = false)
    private LocalDateTime deadline;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MilestoneStatus status = MilestoneStatus.PENDING;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @OneToMany(mappedBy = "milestone", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<JobDeliverable> deliverables = new ArrayList<>();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

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

    public enum MilestoneStatus {
        PENDING,
        IN_PROGRESS,
        SUBMITTED,
        APPROVED,
        REJECTED
    }
}
