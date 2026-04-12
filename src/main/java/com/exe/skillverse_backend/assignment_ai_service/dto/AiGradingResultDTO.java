package com.exe.skillverse_backend.assignment_ai_service.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiGradingResultDTO {
    private List<CriteriaScoreResult> criteriaScores;
    private BigDecimal totalScore;
    private String overallFeedback;
    private Double overallConfidence; // 0.0 - 1.0

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriteriaScoreResult {
        private Long criteriaId;
        private String criteriaName;
        private BigDecimal score;
        private BigDecimal maxPoints;
        private BigDecimal passingPoints;
        private Boolean passed;
        private String feedback;
        private Double confidence; // 0.0 - 1.0
    }
}
