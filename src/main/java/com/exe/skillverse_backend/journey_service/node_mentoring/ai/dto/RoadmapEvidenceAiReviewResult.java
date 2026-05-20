package com.exe.skillverse_backend.journey_service.node_mentoring.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapEvidenceAiReviewResult {
    private Integer scorePercent;
    private Double confidence;
    private String feedback;
    private String rubricBreakdownJson;
}
