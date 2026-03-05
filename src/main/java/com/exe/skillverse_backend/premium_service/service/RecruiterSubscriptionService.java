package com.exe.skillverse_backend.premium_service.service;

import com.exe.skillverse_backend.premium_service.dto.response.RecruiterSubscriptionInfoResponse;

/**
 * Service for managing recruiter subscription features
 * Handles job posting quota, highlight, and AI candidate suggestion checks
 */
public interface RecruiterSubscriptionService {

    /**
     * Check if recruiter has active RECRUITER_PRO subscription
     */
    boolean hasActiveRecruiterSubscription(Long userId);

    /**
     * Validate recruiter can post a job (has subscription + under quota)
     * Throws exception if not allowed
     */
    void validateCanPostJob(Long userId);

    /**
     * Try to use subscription quota for a job posting (full-time).
     * Returns true if subscription was used, false if no active subscription
     * (caller should fall back to direct wallet payment).
     * Throws exception only if subscription exists but quota exceeded.
     */
    boolean tryUseSubscriptionQuota(Long userId);

    /**
     * Try to use subscription quota for a short-term/gig job posting.
     * Returns true if subscription was used, false if no active subscription
     * (caller should fall back to direct wallet payment).
     * Throws exception only if subscription exists but quota exceeded.
     */
    boolean tryUseShortTermJobQuota(Long userId);

    /**
     * Record a job posting (increment monthly usage)
     */
    void recordJobPosting(Long userId);

    /**
     * Check if recruiter can highlight job posts
     */
    boolean canHighlightJob(Long userId);

    /**
     * Check if recruiter can use AI candidate suggestion
     */
    boolean canUseAICandidateSuggestion(Long userId);

    /**
     * Get recruiter subscription info (plan details, quota, features)
     */
    RecruiterSubscriptionInfoResponse getSubscriptionInfo(Long userId);
}
