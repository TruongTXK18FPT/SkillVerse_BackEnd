package com.exe.skillverse_backend.gamification_service.service;

import com.exe.skillverse_backend.gamification_service.dto.response.AdminGamificationStatsResponse;
import com.exe.skillverse_backend.gamification_service.dto.response.UserActivityTrackingResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

/**
 * Service for admin gamification dashboard and activity tracking
 */
public interface GamificationAdminDashboardService {

    /**
     * Get comprehensive admin dashboard statistics
     */
    AdminGamificationStatsResponse getDashboardStats();

    /**
     * Get activity trends for a date range
     */
    List<AdminGamificationStatsResponse.ActivityTrendData> getActivityTrends(LocalDate startDate, LocalDate endDate);

    /**
     * Get top coin earners
     */
    List<AdminGamificationStatsResponse.UserActivitySummary> getTopCoinEarners(int limit);

    /**
     * Get most active users
     */
    List<AdminGamificationStatsResponse.UserActivitySummary> getMostActiveUsers(int limit);

    /**
     * Get game statistics
     */
    List<AdminGamificationStatsResponse.GameStatsSummary> getGameStats();

    /**
     * Get badge statistics
     */
    List<AdminGamificationStatsResponse.BadgeStatsSummary> getBadgeStats();

    /**
     * Get user activity tracking with achievement progress
     */
    UserActivityTrackingResponse getUserActivityTracking(Long userId);

    /**
     * Get all users with their activity summaries (paginated)
     */
    Page<AdminGamificationStatsResponse.UserActivitySummary> getAllUserActivities(Pageable pageable);

    /**
     * Bulk award badges to users meeting criteria
     */
    int bulkAwardBadges(String badgeKey);

    /**
     * Recalculate all user achievements
     */
    int recalculateAchievements();
}
