package com.exe.skillverse_backend.journey_service.node_mentoring.dto.response;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.AdminReviewDecision;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.AiReviewStatus;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapEvidenceAiReview;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRoadmapEvidenceReviewResponse {
    private Long id;
    private Long nodeSubmissionId;
    private Long journeyOutputAssessmentId;
    private Long journeyId;
    private Long roadmapSessionId;
    private String nodeId;
    private Long learnerId;
    private Integer attemptNumber;
    private AiReviewStatus status;
    private Integer aiScorePercent;
    private Double aiConfidence;
    private String aiFeedback;
    private String aiRubricBreakdownJson;
    private String aiModelName;
    private String aiProvider;
    private String errorMessage;
    private AdminReviewDecision adminDecision;
    private String adminReviewReason;
    private Long adminReviewedBy;
    private Instant adminReviewedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private String targetType; // Derived
    private String reviewReasonType; // Derived

    public static AdminRoadmapEvidenceReviewResponse from(RoadmapEvidenceAiReview review) {
        return AdminRoadmapEvidenceReviewResponse.builder()
                .id(review.getId())
                .nodeSubmissionId(review.getNodeSubmissionId())
                .journeyOutputAssessmentId(review.getJourneyOutputAssessmentId())
                .journeyId(review.getJourneyId())
                .roadmapSessionId(review.getRoadmapSessionId())
                .nodeId(review.getNodeId())
                .learnerId(review.getLearnerId())
                .attemptNumber(review.getAttemptNumber())
                .status(review.getStatus())
                .aiScorePercent(review.getAiScorePercent())
                .aiConfidence(review.getAiConfidence())
                .aiFeedback(review.getAiFeedback())
                .aiRubricBreakdownJson(review.getAiRubricBreakdownJson())
                .aiModelName(review.getAiModelName())
                .aiProvider(review.getAiProvider())
                .errorMessage(review.getErrorMessage())
                .adminDecision(review.getAdminDecision())
                .adminReviewReason(review.getAdminReviewReason())
                .adminReviewedBy(review.getAdminReviewedBy())
                .adminReviewedAt(review.getAdminReviewedAt())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .targetType(review.getNodeSubmissionId() != null ? "NODE_EVIDENCE" : "FINAL_ASSIGNMENT")
                .reviewReasonType(
                        review.getErrorMessage() != null
                        && review.getErrorMessage().contains("AI review is disabled")
                                ? "AI_DISABLED"
                                : null
                )
                .build();
    }
}
