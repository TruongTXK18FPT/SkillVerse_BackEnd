package com.exe.skillverse_backend.journey_service.node_mentoring.dto.response;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeReview;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeReview.ReviewResult;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.GradingCriterionScoreDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeReviewResponse {

    private static final ObjectMapper mapper = new ObjectMapper();

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
                scoresList = Arrays.asList(mapper.readValue(r.getCriteriaScoresJson(), GradingCriterionScoreDto[].class));
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
