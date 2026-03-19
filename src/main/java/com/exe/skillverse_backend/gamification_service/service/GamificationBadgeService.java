package com.exe.skillverse_backend.gamification_service.service;

import com.exe.skillverse_backend.gamification_service.dto.request.BadgeDefinitionRequest;
import com.exe.skillverse_backend.gamification_service.dto.response.BadgeDefinitionResponse;
import com.exe.skillverse_backend.gamification_service.dto.response.UserBadgeResponse;
import java.util.List;

/**
 * Service interface for managing badges and achievements
 */
public interface GamificationBadgeService {

    /**
     * Get all badge definitions
     */
    List<BadgeDefinitionResponse> getAllBadgeDefinitions();

    /**
     * Get active badge definitions
     */
    List<BadgeDefinitionResponse> getActiveBadgeDefinitions();

    /**
     * Get badge definitions by category
     */
    List<BadgeDefinitionResponse> getBadgesByCategory(String category);

    /**
     * Get user badges with unlock status
     */
    List<UserBadgeResponse> getUserBadges(Long userId, String category);

    /**
     * Check and award eligible badges to user
     */
    List<UserBadgeResponse> checkAndAwardBadges(Long userId);

    /**
     * Award specific badge to user
     */
    UserBadgeResponse awardBadge(Long userId, String badgeKey);

    /**
     * Admin: Create badge definition
     */
    BadgeDefinitionResponse createBadgeDefinition(BadgeDefinitionRequest request, Long adminId);

    /**
     * Admin: Update badge definition
     */
    BadgeDefinitionResponse updateBadgeDefinition(Long badgeDefId, BadgeDefinitionRequest request);

    /**
     * Admin: Delete badge definition
     */
    void deleteBadgeDefinition(Long badgeDefId);

    /**
     * Get badge definition by key
     */
    BadgeDefinitionResponse getBadgeByKey(String badgeKey);
}
