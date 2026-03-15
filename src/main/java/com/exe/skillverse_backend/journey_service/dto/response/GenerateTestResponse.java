package com.exe.skillverse_backend.journey_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for AI-generated test.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateTestResponse {

    private Long journeyId;
    private Long testId;
    private String title;
    private String description;
    private String targetField;
    private Integer questionCount;
    private Integer timeLimitMinutes;
    private String difficultyLevel;
    private String questionsJson;
    private String message;
}
