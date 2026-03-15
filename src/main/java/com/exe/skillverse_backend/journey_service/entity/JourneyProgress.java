package com.exe.skillverse_backend.journey_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Entity representing a user's progress in a journey.
 * Tracks milestones and achievements.
 */
@Entity
@Table(name = "journey_progress", indexes = {
        @Index(columnList = "journey_id"),
        @Index(columnList = "user_id"),
        @Index(columnList = "milestone")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JourneyProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Journey this progress belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journey_id", nullable = false)
    private Journey journey;

    /**
     * User who made this progress
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Current milestone/step in the journey
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "milestone", length = 50, nullable = false)
    private Milestone milestone;

    /**
     * Whether this milestone has been completed
     */
    @Builder.Default
    @Column(name = "is_completed", nullable = false)
    private Boolean isCompleted = false;

    /**
     * Progress percentage for current milestone (0-100)
     */
    @Builder.Default
    @Column(name = "milestone_progress")
    private Integer milestoneProgress = 0;

    /**
     * Notes or details about this progress
     */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * Timestamp when milestone was completed
     */
    @Column(name = "completed_at")
    private Instant completedAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Milestones in the journey
     */
    public enum Milestone {
        ASSESSMENT_COMPLETED,
        TEST_GENERATED,
        TEST_COMPLETED,
        EVALUATION_COMPLETED,
        ROADMAP_CREATED,
        STUDY_PLAN_CREATED,
        FIRST_NODE_COMPLETED,
        JOURNEY_COMPLETED
    }
}
