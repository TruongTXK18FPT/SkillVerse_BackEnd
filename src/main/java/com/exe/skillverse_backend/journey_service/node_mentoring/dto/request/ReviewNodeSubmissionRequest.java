package com.exe.skillverse_backend.journey_service.node_mentoring.dto.request;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeReview.ReviewResult;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.GradingCriterionScoreDto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Mentor review request for a node submission.
 * Produces a RoadmapNodeReview record and updates submission status.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewNodeSubmissionRequest {

    @NotNull
    private ReviewResult reviewResult;

    private String feedback;

    @Min(0)
    @Max(100)
    private Integer score;

    /** Optional link to the booking under which this review happened. */
    private Long bookingId;

    private List<GradingCriterionScoreDto> criteriaScores;
}
