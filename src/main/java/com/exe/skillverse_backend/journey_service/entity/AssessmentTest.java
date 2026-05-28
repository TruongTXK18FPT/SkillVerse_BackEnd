package com.exe.skillverse_backend.journey_service.entity;

import com.exe.skillverse_backend.question_bank_service.entity.QuestionBank;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing an AI-generated assessment test for a journey.
 */
@Entity
@Table(name = "assessment_tests", indexes = {
        @Index(columnList = "journey_id"),
        @Index(columnList = "status"),
        @Index(columnList = "question_bank_id")
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
     * Optional source question bank used to build the assessment.
     * Null means the test came from pure AI generation.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_bank_id")
    private QuestionBank questionBank;

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
     * Assessment phase: PLACEMENT or CHALLENGE_UP.
     */
    @Column(name = "assessment_phase", length = 30)
    private String assessmentPhase;

    /**
     * User-selected baseline level when the journey was created.
     */
    @Column(name = "base_level", length = 20)
    private String baseLevel;

    /**
     * Level this concrete test is intended to verify.
     */
    @Column(name = "tested_level", length = 20)
    private String testedLevel;

    /**
     * Parent assessment test for adaptive challenge-up rounds.
     */
    @Column(name = "parent_test_id")
    private Long parentTestId;

    /**
     * Source of questions: QUESTION_BANK or AI.
     */
    @Column(name = "question_source", length = 30)
    private String questionSource;

    /**
     * JSON array of questions
     */
    @Column(name = "questions_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String questionsJson;

    /**
     * Temporary store of user's selected answers during progress.
     */
    @Column(name = "user_answers_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String userAnswersJson;

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
