package com.exe.skillverse_backend.journey_service.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing the result of an assessment test evaluation.
 */
@Entity
@Table(name = "test_results", indexes = {
        @Index(columnList = "journey_id"),
        @Index(columnList = "assessment_test_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Journey this result belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journey_id", nullable = false)
    private Journey journey;

    /**
     * Assessment test that was evaluated
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_test_id", nullable = false)
    private AssessmentTest assessmentTest;

    /**
     * Score percentage (0-100)
     */
    @Column(name = "score_percentage")
    private Integer scorePercentage;

    /**
     * Evaluated skill level
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "evaluated_level", length = 20)
    private Journey.SkillLevel evaluatedLevel;

    /**
     * JSON array of skill gaps identified
     */
    @Column(name = "skill_gaps_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String skillGapsJson;

    /**
     * JSON array of strengths identified
     */
    @Column(name = "strengths_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String strengthsJson;

    /**
     * AI-generated evaluation summary
     */
    @Column(name = "evaluation_summary", columnDefinition = "TEXT")
    private String evaluationSummary;

    /**
     * JSON array of user answers for reference
     */
    @Column(name = "user_answers_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String userAnswersJson;

    /**
     * JSON array of correct answers (for review)
     */
    @Column(name = "correct_answers_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String correctAnswersJson;

    /**
     * Timestamp when evaluation was completed
     */
    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (evaluatedAt == null) evaluatedAt = Instant.now();
    }
}
