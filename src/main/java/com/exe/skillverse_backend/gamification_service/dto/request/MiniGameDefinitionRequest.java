package com.exe.skillverse_backend.gamification_service.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin request DTO for creating or updating mini-game definitions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiniGameDefinitionRequest {

    @NotBlank(message = "Game key is required")
    private String gameKey;

    @NotBlank(message = "Game title is required")
    private String gameTitle;

    private String gameDescription;

    private String gameIcon;

    @NotBlank(message = "Game type is required")
    private String gameType; // spin, quiz, hunt, help, game

    @NotBlank(message = "Difficulty level is required")
    private String difficultyLevel; // easy, medium, hard

    @NotNull(message = "Base coin reward is required")
    @Min(value = 0, message = "Base coin reward must be non-negative")
    private Integer baseCoinReward;

    @NotNull(message = "Max coin reward is required")
    @Min(value = 0, message = "Max coin reward must be non-negative")
    private Integer maxCoinReward;

    @NotNull(message = "XP reward is required")
    @Min(value = 0, message = "XP reward must be non-negative")
    private Integer xpReward;

    @NotNull(message = "Cooldown minutes is required")
    @Min(value = 0, message = "Cooldown must be non-negative")
    private Integer cooldownMinutes;

    @Min(value = 1, message = "Max plays per day must be at least 1")
    private Integer maxPlaysPerDay;

    @Min(value = 0, message = "Max coins per day must be non-negative")
    private Integer maxCoinsPerDay;

    private Boolean isActive = true;

    private Boolean isPremiumOnly = false;

    private String requiredPremiumPlan;

    private Double premiumCoinMultiplier = 1.0;

    private String gameConfig; // JSON
}
