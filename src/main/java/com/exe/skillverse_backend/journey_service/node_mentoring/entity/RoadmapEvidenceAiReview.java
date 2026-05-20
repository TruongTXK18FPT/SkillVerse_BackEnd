package com.exe.skillverse_backend.journey_service.node_mentoring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "roadmap_evidence_ai_reviews")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapEvidenceAiReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "node_submission_id")
    private Long nodeSubmissionId;

    @Column(name = "journey_output_assessment_id")
    private Long journeyOutputAssessmentId;

    @Column(name = "journey_id", nullable = false)
    private Long journeyId;

    @Column(name = "roadmap_session_id", nullable = false)
    private Long roadmapSessionId;

    @Column(name = "node_id", length = 100)
    private String nodeId;

    @Column(name = "learner_id", nullable = false)
    private Long learnerId;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AiReviewStatus status;

    @Column(name = "ai_score_percent")
    private Integer aiScorePercent;

    @Column(name = "ai_confidence")
    private Double aiConfidence;

    @Column(name = "ai_feedback", columnDefinition = "TEXT")
    private String aiFeedback;

    @Column(name = "ai_rubric_breakdown_json", columnDefinition = "TEXT")
    private String aiRubricBreakdownJson;

    @Column(name = "ai_model_name", length = 100)
    private String aiModelName;

    @Column(name = "ai_provider", length = 50)
    private String aiProvider;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "admin_decision", length = 50)
    private AdminReviewDecision adminDecision;

    @Column(name = "admin_review_reason", columnDefinition = "TEXT")
    private String adminReviewReason;

    @Column(name = "admin_reviewed_by")
    private Long adminReviewedBy;

    @Column(name = "admin_reviewed_at")
    private Instant adminReviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
