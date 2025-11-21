package com.exe.skillverse_backend.premium_service.service;

import com.exe.skillverse_backend.premium_service.dto.response.FeatureLimitInfo;
import com.exe.skillverse_backend.premium_service.dto.response.UsageCheckResult;
import com.exe.skillverse_backend.premium_service.entity.FeatureType;

import java.util.List;

/**
 * Service for managing and enforcing usage limits
 */
public interface UsageLimitService {

    /**
     * Check if user can use a feature
     * Does NOT record usage, only checks
     * 
     * @param userId      User ID
     * @param featureType Feature to check
     * @return Check result with details
     */
    UsageCheckResult canUseFeature(Long userId, FeatureType featureType);

    /**
     * Record usage of a feature
     * Should be called after successful feature usage
     * 
     * @param userId      User ID
     * @param featureType Feature used
     */
    void recordUsage(Long userId, FeatureType featureType);

    /**
     * Check and record usage in one operation
     * Throws exception if limit exceeded
     * 
     * @param userId      User ID
     * @param featureType Feature to use
     * @throws com.exe.skillverse_backend.premium_service.exception.UsageLimitExceededException if
     *                                                                                          limit
     *                                                                                          exceeded
     */
    void checkAndRecordUsage(Long userId, FeatureType featureType);

    /**
     * Get usage information for a specific feature
     * 
     * @param userId      User ID
     * @param featureType Feature type
     * @return Feature limit info with current usage
     */
    FeatureLimitInfo getUserUsage(Long userId, FeatureType featureType);

    /**
     * Get all feature limits and usage for user's current plan
     * 
     * @param userId User ID
     * @return List of all feature limits with usage info
     */
    List<FeatureLimitInfo> getUserPlanLimits(Long userId);

    /**
     * Reset expired usage periods (scheduled task)
     * Runs every hour to check and reset expired periods
     */
    void resetExpiredUsagePeriods();

    /**
     * Initialize usage tracking for a user
     * Called when user first uses a feature or changes plan
     * 
     * @param userId      User ID
     * @param featureType Feature type
     */
    void initializeUserUsage(Long userId, FeatureType featureType);

    /**
     * Reset usage for a specific user and feature
     * Used when user upgrades plan or admin resets usage
     * 
     * @param userId      User ID
     * @param featureType Feature type
     */
    void resetUserUsage(Long userId, FeatureType featureType);
}
