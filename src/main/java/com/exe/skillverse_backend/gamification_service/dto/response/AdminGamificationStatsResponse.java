package com.exe.skillverse_backend.gamification_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for admin gamification dashboard statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminGamificationStatsResponse {

    // Overview stats
    private Long totalUsers;
    private Long activeUsers; // Users active in last 7 days
    private Long totalCoinsDistributed;
    private Long totalCoinsSpent;
    private Long totalBadgesAwarded;
    private Long totalGameSessions;
    
    // Daily/Weekly stats
    private Long coinsDistributedToday;
    private Long coinsDistributedThisWeek;
    private Long badgesAwardedToday;
    private Long badgesAwardedThisWeek;
    private Long gameSessionsToday;
    private Long gameSessionsThisWeek;
    
    // Top performers
    private List<LeaderboardEntryResponse> topCoinEarners;
    private List<LeaderboardEntryResponse> topXpEarners;
    private List<UserActivitySummary> mostActiveUsers;
    
    // Game statistics
    private List<GameStatsSummary> gameStats;
    
    // Badge statistics  
    private List<BadgeStatsSummary> badgeStats;
    
    // Activity tracking
    private List<ActivityTrendData> activityTrends;
    
    private LocalDateTime generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserActivitySummary {
        private Long userId;
        private String userName;
        private String fullName;
        private String userAvatar;
        private String avatarMediaUrl;
        private Integer totalActivities;
        private Integer coursesCompleted;
        private Integer postsCreated;
        private Integer gamesPlayed;
        private Integer badgesEarned;
        private Integer totalCoins;
        private Integer totalXp;
        private Integer currentStreak;
        private LocalDateTime lastActivityAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GameStatsSummary {
        private Long gameDefId;
        private String gameKey;
        private String gameTitle;
        private Long totalSessions;
        private Long completedSessions;
        private Long failedSessions;
        private Long totalCoinsAwarded;
        private Long totalXpAwarded;
        private Double averageScore;
        private Double completionRate;
        private Long uniquePlayers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BadgeStatsSummary {
        private Long badgeDefId;
        private String badgeKey;
        private String badgeTitle;
        private String badgeCategory;
        private String badgeRarity;
        private Long totalAwarded;
        private Long awardedThisWeek;
        private Double awardRate; // Percentage of users who have this badge
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityTrendData {
        private String date;
        private Long coinsDistributed;
        private Long badgesAwarded;
        private Long gameSessions;
        private Long activeUsers;
        private Long newUsers;
    }
}
