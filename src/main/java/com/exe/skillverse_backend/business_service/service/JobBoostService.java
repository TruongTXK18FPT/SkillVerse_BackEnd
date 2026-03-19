package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.business_service.dto.request.CreateJobBoostRequest;
import com.exe.skillverse_backend.business_service.dto.response.JobBoostAnalyticsResponse;
import com.exe.skillverse_backend.business_service.dto.response.JobBoostResponse;

import java.util.List;

/**
 * Service interface for job boost operations
 */
public interface JobBoostService {

    /**
     * Create a new job boost for a premium recruiter
     */
    JobBoostResponse createBoost(Long recruiterId, CreateJobBoostRequest request);

    /**
     * Get boost details for a specific job
     */
    JobBoostResponse getBoostByJobId(Long jobId);

    /**
     * Get all boosts for a recruiter
     */
    List<JobBoostResponse> getBoostsByRecruiter(Long recruiterId);

    /**
     * Cancel an active boost
     */
    JobBoostResponse cancelBoost(Long recruiterId, Long boostId);

    /**
     * Extend boost expiration
     */
    JobBoostResponse extendBoost(Long recruiterId, Long boostId, int additionalDays);

    /**
     * Get analytics for a specific boost
     */
    JobBoostAnalyticsResponse getBoostAnalytics(Long recruiterId, Long boostId);

    /**
     * Get available boost quota for a recruiter
     */
    int getAvailableBoostQuota(Long recruiterId);

    /**
     * Record an impression for a boosted job
     */
    void recordImpression(Long jobId, Long userId, Integer position);

    /**
     * Record a click on a boosted job
     */
    void recordClick(Long jobId, Long userId);

    /**
     * Get list of active boosted job IDs (for ranking)
     */
    List<Long> getActiveBoostedJobIds();

    /**
     * Check if a job has active boost
     */
    boolean hasActiveBoost(Long jobId);

    /**
     * Process expired boosts (called by scheduler)
     */
    void processExpiredBoosts();

    /**
     * Activate scheduled boosts that are ready
     */
    void activateScheduledBoosts();
}
