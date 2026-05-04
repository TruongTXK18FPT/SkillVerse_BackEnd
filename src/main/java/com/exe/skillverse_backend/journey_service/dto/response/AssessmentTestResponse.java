package com.exe.skillverse_backend.journey_service.dto.response;

import com.exe.skillverse_backend.journey_service.entity.AssessmentTest;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for assessment test.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentTestResponse {

    private Long id;
    private String title;
    private String description;
    private String targetField;
    private AssessmentTest.TestStatus status;
    private Integer questionCount;
    private Integer timeLimitMinutes;
    private String difficultyLevel;
    private String assessmentPhase;
    private String baseLevel;
    private String testedLevel;
    private Long parentTestId;
    private String questionSource;
    private String questionsJson;
    private Instant createdAt;

    // Only include for completed tests
    private Boolean showResults;
}
