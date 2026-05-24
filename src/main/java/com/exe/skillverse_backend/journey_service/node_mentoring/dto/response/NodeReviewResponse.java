package com.exe.skillverse_backend.journey_service.node_mentoring.dto.response;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeReview;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeReview.ReviewResult;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.GradingCriterionScoreDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeReviewResponse {

    private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private Long id;
    private Long submissionId;
    private Long mentorId;
    private Long bookingId;
    private Integer score;
    private String feedback;
    private ReviewResult reviewResult;
    private Instant reviewedAt;
    private List<GradingCriterionScoreDto> criteriaScores;

    public static NodeReviewResponse from(RoadmapNodeReview r) {
        List<GradingCriterionScoreDto> scoresList = null;
        if (r.getCriteriaScoresJson() != null && !r.getCriteriaScoresJson().isBlank()) {
            try {
                scoresList = java.util.Arrays.asList(mapper.readValue(r.getCriteriaScoresJson(), GradingCriterionScoreDto[].class));
            } catch (Exception ex) {
                // Ignore parse errors
            }
        }

        return NodeReviewResponse.builder()
                .id(r.getId())
                .submissionId(r.getSubmissionId())
                .mentorId(r.getMentorId())
                .bookingId(r.getBookingId())
                .score(r.getScore())
                .feedback(r.getFeedback())
                .reviewResult(r.getReviewResult())
                .reviewedAt(r.getReviewedAt())
                .criteriaScores(scoresList)
                .build();
    }
}
