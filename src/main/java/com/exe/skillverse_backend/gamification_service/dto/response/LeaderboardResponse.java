package com.exe.skillverse_backend.gamification_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for full leaderboard
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardResponse {

    private String leaderboardPeriod; // week, month, all
    private String leaderboardType; // learning, community, coins
    private LeaderboardEntryResponse currentUserPosition;
    private List<LeaderboardEntryResponse> topEntries;
    private Integer totalParticipants;
    private Integer coinsToNextRank; // Coins needed to move up
}
