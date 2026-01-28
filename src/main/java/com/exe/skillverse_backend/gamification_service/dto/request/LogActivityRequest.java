package com.exe.skillverse_backend.gamification_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for logging user activities
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogActivityRequest {

    @NotBlank(message = "Activity type is required")
    private String activityType; // STUDY, QUIZ, ROADMAP, CONTRIBUTION, MINIGAME

    @NotBlank(message = "Activity action is required")
    private String activityAction; // start, complete, submit, win

    private String targetType; // lesson, quiz, roadmap

    private Long targetId;

    private String activityData; // JSON

    private Integer durationMinutes;
}
