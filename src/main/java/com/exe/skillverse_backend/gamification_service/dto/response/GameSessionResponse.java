package com.exe.skillverse_backend.gamification_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for game sessions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameSessionResponse {

    private Long sessionId;
    private Long userId;
    private MiniGameDefinitionResponse gameDefinition;
    private String sessionStatus;
    private Integer scoreAchieved;
    private Integer coinsEarned;
    private Integer xpEarned;
    private Boolean isVerified;
    private LocalDateTime playedAt;
    private LocalDateTime completedAt;
    private Integer durationSeconds;
}
