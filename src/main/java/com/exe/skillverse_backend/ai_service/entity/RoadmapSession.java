package com.exe.skillverse_backend.ai_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing an AI-generated learning roadmap session
 */
@Entity
@Table(name = "roadmap_sessions", indexes = {
        @Index(columnList = "user_id"),
        @Index(columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User who owns this roadmap
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Title of the roadmap (e.g., "Learn Spring Boot in 3 Months")
     */
    @Column(nullable = false, length = 255)
    private String title;

    /**
     * Schema version: 1 = old format, 2 = enhanced format
     */
    @Builder.Default
    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion = 2;

    /**
     * User's original learning goal (unmodified)
     */
    @Column(name = "original_goal", columnDefinition = "TEXT")
    private String originalGoal;

    /**
     * AI-validated and clarified goal
     */
    @Column(name = "validated_goal", columnDefinition = "TEXT")
    private String validatedGoal;

    /**
     * User's learning goal (kept for backward compatibility)
     * 
     * @deprecated Use originalGoal and validatedGoal instead
     */
    @Deprecated
    @Column(columnDefinition = "TEXT")
    private String goal;

    /**
     * Expected duration (e.g., "3 tháng", "6 tháng")
     */
    @Column(length = 50)
    private String duration;

    /**
     * User's experience level (e.g., "Mới bắt đầu", "Trung cấp", "Nâng cao")
     */
    @Column(name = "experience_level", length = 100)
    private String experienceLevel;

    /**
     * Learning style preference (e.g., "Theo dự án - Học bằng cách làm")
     */
    @Column(name = "learning_style", length = 150)
    private String learningStyle;

    /**
     * Old experience column (deprecated)
     * 
     * @deprecated Use experienceLevel instead
     */
    @Deprecated
    @Column(name = "experience_level_deprecated", length = 50)
    private String experience;

    /**
     * Old style column (deprecated)
     * 
     * @deprecated Use learningStyle instead
     */
    @Deprecated
    @Column(name = "learning_style_deprecated", length = 50)
    private String style;

    @Column(name = "roadmap_type", length = 20)
    private String roadmapType;

    @Column(name = "roadmap_mode", length = 20)
    private String roadmapMode;

    @Column(name = "target", columnDefinition = "TEXT")
    private String target;

    @Column(name = "final_objective", length = 100)
    private String finalObjective;

    /**
     * Statistics for premium quota tracking
     */
    @Column(name = "total_nodes")
    private Integer totalNodes;

    @Column(name = "total_estimated_hours")
    private Double totalEstimatedHours;

    @Column(name = "difficulty_level", length = 20)
    private String difficultyLevel; // easy, medium, hard, expert

    /**
     * Whether user had premium subscription when roadmap was generated
     */
    @Builder.Default
    @Column(name = "is_premium_generated", nullable = false)
    private Boolean isPremiumGenerated = false;

    /**
     * Generated roadmap as JSON (tree structure with nodes)
     * Stored as JSONB for efficient querying in PostgreSQL
     * Schema V2: Includes metadata, statistics, and enhanced nodes
     */
    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String roadmapJson;

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
     * One-to-many relationship with user progress
     */
    @Builder.Default
    @OneToMany(mappedBy = "roadmapSession", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<UserRoadmapProgress> progressList = new ArrayList<>();
}
