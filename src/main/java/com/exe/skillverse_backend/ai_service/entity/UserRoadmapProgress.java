package com.exe.skillverse_backend.ai_service.entity;


import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity tracking user progress on individual quests/nodes in a roadmap
 */
@Entity
@Table(name = "user_roadmap_progress", indexes = {
        @Index(columnList = "roadmap_session_id"),
        @Index(columnList = "quest_id")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = { "roadmap_session_id", "quest_id" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRoadmapProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Reference to the roadmap session
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_session_id", nullable = false)
    private RoadmapSession roadmapSession;

    /**
     * Quest/node ID from the JSON roadmap (e.g., "node-1", "quest-java-basics")
     */
    @Column(nullable = false, length = 255, name = "quest_id")
    private String questId;

    /**
     * Current status of this quest
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private ProgressStatus status = ProgressStatus.NOT_STARTED;

    /**
     * Progress percentage (0-100)
     */
    @Builder.Default
    @Column(nullable = false)
    private Integer progress = 0;

    /**
     * Timestamp when quest was completed
     */
    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Timestamps
     */
    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null)
            createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Progress status enum
     */
    public enum ProgressStatus {
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETED,
        SKIPPED
    }
}
