package com.exe.skillverse_backend.gamification_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for mini-game definitions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiniGameDefinitionResponse {

    private Long gameDefId;
    private String gameKey;
    private String gameTitle;
    private String gameDescription;
    private String gameIcon;
    private String gameType;
    private String difficultyLevel;
    private Integer baseCoinReward;
    private Integer maxCoinReward;
    private Integer xpReward;
    private Integer cooldownMinutes;
    private Integer maxPlaysPerDay;
    private Integer maxCoinsPerDay;
    private Boolean isActive;
    private Boolean isPremiumOnly;
    private String requiredPremiumPlan;
    private Double premiumCoinMultiplier;
    private Boolean available; // Calculated field - if user can play now
    private LocalDateTime lastPlayed; // Last time user played this game
    private Integer remainingPlaysToday; // Calculated field
    private LocalDateTime createdAt;
}
