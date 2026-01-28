package com.exe.skillverse_backend.gamification_service.service;

import com.exe.skillverse_backend.gamification_service.dto.request.LogActivityRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Service interface for tracking user activities
 */
public interface GamificationActivityService {

    /**
     * Log user activity
     */
    void logActivity(Long userId, LogActivityRequest request);

    /**
     * Verify and process activity (award coins if eligible)
     */
    void verifyAndProcessActivity(Long activityLogId);

    /**
     * Get user activity summary
     */
    Map<String, Object> getUserActivitySummary(Long userId, LocalDateTime since);

    /**
     * Get total study time for user
     */
    Integer getTotalStudyMinutes(Long userId, LocalDateTime since);

    /**
     * Count activities by type
     */
    Long countActivitiesByType(Long userId, String activityType, LocalDateTime since);
}
