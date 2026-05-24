package com.exe.skillverse_backend.journey_service.node_mentoring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradingCriterionDto {
    private String id;
    private String title;
    private Integer maxScore;
}
