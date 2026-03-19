package com.exe.skillverse_backend.gamification_service.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for user activity tracking with achievement progress
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivityTrackingResponse {

    private Long userId;
    private String userName;
    private String fullName;
    private String userAvatar;
    private String avatarMediaUrl;
    
    // Achievement Counters
    private ActivityCounters counters;
    
    // Achievement Progress
    private List<AchievementProgress> achievements;
    
    // Recent Activities
    private List<RecentActivity> recentActivities;
    
    // Milestones reached
    private List<MilestoneReached> milestones;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityCounters {
        // Learning
        private Integer coursesEnrolled;
        private Integer coursesCompleted;
        private Integer lessonsCompleted;
        private Integer quizzesCompleted;
        private Integer quizzesPerfectScore;
        private Integer assignmentsSubmitted;
        private Integer certificatesEarned;
        
        // Community
        private Integer postsCreated;
        private Integer commentsWritten;
        private Integer likesReceived;
        private Integer mentorSessionsBooked;
        private Integer mentorSessionsCompleted;
        private Integer reviewsWritten;
        private Integer helpfulAnswers;
        
        // Gamification
        private Integer gamesPlayed;
        private Integer gamesWon;
        private Integer totalCoinsEarned;
        private Integer totalCoinsSpent;
        private Integer badgesEarned;
        private Integer currentStreak;
        private Integer longestStreak;
        
        // Events
        private Integer seminarsAttended;
        private Integer eventsParticipated;
        private Integer challengesCompleted;
        
        // Misc
        private Integer loginDays;
        private Integer profileViews;
        private Integer referrals;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AchievementProgress {
        private String achievementKey;
        private String achievementTitle;
        private String achievementDescription;
        private String achievementIcon;
        private String category;
        private Integer currentValue;
        private Integer targetValue;
        private Double progressPercent;
        private Boolean isCompleted;
        private LocalDateTime completedAt;
        private Integer coinReward;
        private Integer xpReward;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentActivity {
        private String activityType;
        private String activityDescription;
        private String entityType; // course, post, game, etc.
        private Long entityId;
        private Integer coinsEarned;
        private Integer xpEarned;
        private LocalDateTime occurredAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MilestoneReached {
        private String milestoneKey;
        private String milestoneTitle;
        private String milestoneDescription;
        private Integer threshold;
        private Integer coinReward;
        private LocalDateTime reachedAt;
    }
}
