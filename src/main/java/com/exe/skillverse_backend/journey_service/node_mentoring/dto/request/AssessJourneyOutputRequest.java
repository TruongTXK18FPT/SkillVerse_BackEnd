package com.exe.skillverse_backend.journey_service.node_mentoring.dto.request;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyOutputAssessment.AssessmentStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mentor decision on a submitted journey output assessment (APPROVED / REJECTED).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssessJourneyOutputRequest {

    @NotNull
    private AssessmentStatus assessmentStatus;

    private String feedback;

    @Min(0)
    @Max(100)
    private Integer score;
}
