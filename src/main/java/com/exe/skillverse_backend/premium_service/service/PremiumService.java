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

    /**
     * Purchase premium subscription using wallet cash
     * Throws exception if insufficient balance
     */
    UserSubscriptionResponse purchaseWithWalletCash(Long userId, Long planId, boolean applyStudentDiscount);

    /**
     * Enable auto-renewal for subscription
     */
    void enableAutoRenewal(Long userId);

    /**
     * Cancel auto-renewal (user keeps subscription until end date)
     * No refund, just prevents next billing cycle
     */
    void cancelAutoRenewal(Long userId);

    /**
     * Process auto-renewal for subscriptions expiring soon
     * Scheduled task runs daily to renew subscriptions
     */
    void processAutoRenewals();

    /**
     * Cancel subscription with refund based on usage time:
     * - Within 24h: 100% refund
     * - 1-3 days: 50% refund
     * - Over 3 days: 0% refund (only stops auto-renewal)
     * Returns refund amount
     */
    double cancelSubscriptionWithRefund(Long userId, String reason);

    /**
     * Get refund eligibility and amount
     * Returns object with: eligible, refundPercentage, refundAmount, daysUsed
     */
    RefundEligibility getRefundEligibility(Long userId);

    /**
     * DTO for refund eligibility information
     */
    record RefundEligibility(
        boolean eligible,
        int refundPercentage,
        double refundAmount,
        long daysUsed,
        String message
    ) {}
}