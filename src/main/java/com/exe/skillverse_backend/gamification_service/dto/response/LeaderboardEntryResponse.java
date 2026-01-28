package com.exe.skillverse_backend.gamification_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for leaderboard entries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardEntryResponse {

    private Long userId;
    private String userName;
    private String fullName;
    private String userAvatar;
    private String avatarMediaUrl;
    private Integer rankPosition;
    private Integer scoreValue;
    private Integer totalCoins;
    private Integer totalXp;
    private Integer badgesCount;
    private Integer streakDays;
    private Integer contributionsCount;
    private Integer skinsCount;
    private Boolean isCurrentUser;
    private Integer rankChange; // Change from previous period
}
