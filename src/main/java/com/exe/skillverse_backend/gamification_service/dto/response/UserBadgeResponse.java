package com.exe.skillverse_backend.gamification_service.dto.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for user badges
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBadgeResponse {

    private Long userBadgeId;
    private Long userId;
    private BadgeDefinitionResponse badgeDefinition;
    private LocalDateTime earnedAt;
    private Integer coinsAwarded;
    private Integer xpAwarded;
    private String progressData;
    private Boolean unlocked;
}
