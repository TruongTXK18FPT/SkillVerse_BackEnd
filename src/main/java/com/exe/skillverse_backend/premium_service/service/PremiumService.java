package com.exe.skillverse_backend.premium_service.service;

import com.exe.skillverse_backend.premium_service.dto.request.CreateSubscriptionRequest;
import com.exe.skillverse_backend.premium_service.dto.response.PremiumPlanResponse;
import com.exe.skillverse_backend.premium_service.dto.response.UserSubscriptionResponse;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import com.exe.skillverse_backend.premium_service.entity.UserSubscription;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for premium subscription management
 */
public interface PremiumService {

    /**
     * Get all available premium plans
     */
    List<PremiumPlanResponse> getAvailablePlans();

    /**
     * Get a specific plan by ID
     */
    Optional<PremiumPlanResponse> getPlanById(Long planId);

    /**
     * Get a specific plan by type
     */
    Optional<PremiumPlanResponse> getPlanByType(PremiumPlan.PlanType planType);

    /**
     * Create a new subscription
     */
    UserSubscriptionResponse createSubscription(Long userId, CreateSubscriptionRequest request);

    /**
     * Get user's current active subscription
     */
    Optional<UserSubscriptionResponse> getCurrentSubscription(Long userId);

    /**
     * Get user's subscription history
     */
    List<UserSubscriptionResponse> getSubscriptionHistory(Long userId);

    /**
     * Cancel user's current subscription
     */
    void cancelSubscription(Long userId, String reason);

    /**
     * Activate subscription after successful payment
     */
    UserSubscription activateSubscription(Long subscriptionId, String paymentTransactionId);

    /**
     * Check if user has active premium subscription
     */
    boolean hasActivePremiumSubscription(Long userId);

    /**
     * Validate student email for discount eligibility
     */
    boolean isValidStudentEmail(String email);

    /**
     * Process expired subscriptions (scheduled task)
     */
    void processExpiredSubscriptions();

    /**
     * Assign Free Tier to user if no active subscription exists
     */
    void assignFreeTierIfMissing(Long userId);

    /**
     * Ensure the user has an active subscription; fallback to Free Tier if needed
     */
    UserSubscriptionResponse ensureActiveSubscriptionOrFree(Long userId);
}