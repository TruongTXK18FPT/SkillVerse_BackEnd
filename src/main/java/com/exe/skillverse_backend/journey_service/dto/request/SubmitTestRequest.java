package com.exe.skillverse_backend.journey_service.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO to submit test answers for evaluation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitTestRequest {

    @NotNull(message = "Test ID is required")
    private Long testId;

    /**
     * List of user's answers (questionId -> answer)
     */
    @NotEmpty(message = "Answers are required")
    private Map<Long, Object> answers;

    /**
     * Time spent in seconds
     */
    private Integer timeSpentSeconds;
}
