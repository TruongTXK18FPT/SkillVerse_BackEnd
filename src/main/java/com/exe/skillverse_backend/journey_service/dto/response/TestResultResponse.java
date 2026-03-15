package com.exe.skillverse_backend.journey_service.dto.response;

import com.exe.skillverse_backend.journey_service.entity.Journey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for test evaluation result.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestResultResponse {

    private Long id;
    private Long journeyId;
    private Long assessmentTestId;
    private Integer scorePercentage;
    private Journey.SkillLevel evaluatedLevel;
    private String skillGapsJson;
    private String strengthsJson;
    private String evaluationSummary;
    private String userAnswersJson;
    private String correctAnswersJson;
    private Instant evaluatedAt;
    private Instant createdAt;

    // Computed fields for UI
    private Integer totalQuestions;
    private Integer correctAnswers;
    private Integer incorrectAnswers;
    private Integer answeredQuestions;
    private String scoreBand;
    private String recommendationMode;
    private Integer assessmentConfidence;
    private Boolean reassessmentRecommended;
}
