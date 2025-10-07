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
     * User's learning goal
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String goal;

    /**
     * Expected duration (e.g., "3 months", "6 weeks")
     */
    @Column(length = 50)
    private String duration;

    /**
     * User's experience level (e.g., "beginner", "intermediate", "advanced")
     */
    @Column(length = 50)
    private String experience;

    /**
     * Learning style preference (e.g., "project-based", "theoretical",
     * "video-based")
     */
    @Column(length = 50)
    private String style;

    /**
     * Generated roadmap as JSON (tree structure with nodes)
     * Stored as JSONB for efficient querying in PostgreSQL
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
