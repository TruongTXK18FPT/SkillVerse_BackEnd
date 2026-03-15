package com.exe.skillverse_backend.journey_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing an AI-generated assessment test for a journey.
 */
@Entity
@Table(name = "assessment_tests", indexes = {
        @Index(columnList = "journey_id"),
        @Index(columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssessmentTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Journey this test belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journey_id", nullable = false)
    private Journey journey;

    /**
     * Test title
     */
    @Column(nullable = false, length = 255)
    private String title;

    /**
     * Test description
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Target skill/career field
     */
    @Column(name = "target_field", length = 100)
    private String targetField;

    /**
     * Test status
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TestStatus status = TestStatus.PENDING;

    /**
     * Number of questions in the test
     */
    @Column(name = "question_count")
    private Integer questionCount;

    /**
     * Time limit in minutes (0 = no limit)
     */
    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    /**
     * Difficulty level of the test
     */
    @Column(name = "difficulty_level", length = 20)
    private String difficultyLevel;

    /**
     * JSON array of questions
     */
    @Column(name = "questions_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String questionsJson;

    /**
     * AI prompt used to generate this test
     */
    @Column(name = "generation_prompt", columnDefinition = "TEXT")
    private String generationPrompt;

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
     * Test status enum
     */
    public enum TestStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        EXPIRED
    }
}
