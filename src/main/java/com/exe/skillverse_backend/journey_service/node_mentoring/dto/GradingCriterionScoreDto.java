package com.exe.skillverse_backend.journey_service.node_mentoring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradingCriterionScoreDto {
    private String criterionId;
    private String title;
    private Double score;
    private Integer maxScore;
}
