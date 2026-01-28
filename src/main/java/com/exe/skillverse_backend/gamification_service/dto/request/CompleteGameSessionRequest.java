package com.exe.skillverse_backend.gamification_service.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for completing a mini-game session
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteGameSessionRequest {

    @NotNull(message = "Session ID is required")
    private Long sessionId;

    @NotNull(message = "Session status is required")
    private String sessionStatus; // COMPLETED, FAILED, ABANDONED

    @Min(value = 0, message = "Score must be non-negative")
    private Integer scoreAchieved;

    private Integer durationSeconds;

    private String sessionData; // JSON with game-specific results

    private String verificationData; // For anti-cheat
}
