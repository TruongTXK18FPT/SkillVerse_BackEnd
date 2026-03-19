package com.exe.skillverse_backend.gamification_service.service;

import com.exe.skillverse_backend.gamification_service.dto.response.GamificationDashboardResponse;
import java.util.Map;

/**
 * Service interface for gamification dashboard and overview
 */
public interface GamificationDashboardService {

    /**
     * Get complete gamification dashboard for user
     */
    GamificationDashboardResponse getUserDashboard(Long userId, String userPlan);

    /**
     * Get gamification statistics for user
     */
    Map<String, Object> getUserStatistics(Long userId);
}
