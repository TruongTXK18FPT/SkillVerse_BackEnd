package com.exe.skillverse_backend.gamification_service.service;

import com.exe.skillverse_backend.gamification_service.dto.response.LeaderboardEntryResponse;
import com.exe.skillverse_backend.gamification_service.dto.response.LeaderboardResponse;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for managing leaderboards
 */
public interface GamificationLeaderboardService {

    /**
     * Get leaderboard for specified period and type
     */
    LeaderboardResponse getLeaderboard(String period, String type, Long currentUserId, Pageable pageable);

    /**
     * Get current user's position in leaderboard
     */
    LeaderboardEntryResponse getUserLeaderboardPosition(Long userId, String period, String type);

    /**
     * Update leaderboard snapshot (scheduled task)
     */
    void updateLeaderboardSnapshot(String period, String type);

    /**
     * Calculate and create real-time leaderboard
     */
    LeaderboardResponse calculateRealtimeLeaderboard(String period, String type, Long currentUserId, Pageable pageable);
}
