package com.exe.skillverse_backend.premium_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.premium_service.dto.response.FeatureLimitInfo;
import com.exe.skillverse_backend.premium_service.dto.response.UsageCheckResult;
import com.exe.skillverse_backend.premium_service.entity.*;
import com.exe.skillverse_backend.premium_service.exception.UsageLimitExceededException;
import com.exe.skillverse_backend.premium_service.repository.PlanFeatureLimitsRepository;
import com.exe.skillverse_backend.premium_service.repository.UserUsageTrackingRepository;
import com.exe.skillverse_backend.premium_service.repository.UserSubscriptionRepository;
import com.exe.skillverse_backend.premium_service.service.UsageLimitService;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of UsageLimitService
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UsageLimitServiceImpl implements UsageLimitService {

    private final UserRepository userRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final PlanFeatureLimitsRepository featureLimitsRepository;
    private final UserUsageTrackingRepository usageTrackingRepository;

    @Override
    @Transactional(readOnly = true)
    public UsageCheckResult canUseFeature(Long userId, FeatureType featureType) {
        log.debug("Checking if user {} can use feature {}", userId, featureType);

        // Get user and their active subscription
        User user = getUserOrThrow(userId);
        UserSubscription subscription = getActiveSubscriptionOrThrow(user);
        PremiumPlan plan = subscription.getPlan();

        // Get feature limit configuration for this plan
        Optional<PlanFeatureLimits> limitConfig = featureLimitsRepository
                .findByPlanAndFeatureTypeAndIsActiveTrue(plan, featureType);

        if (limitConfig.isEmpty()) {
            // No limit configured = unlimited
            log.debug("No limit configured for feature {} in plan {}, allowing unlimited",
                    featureType, plan.getName());
            return UsageCheckResult.unlimited(0);
        }

        PlanFeatureLimits limit = limitConfig.get();

        // Check if unlimited
        if (limit.getIsUnlimited()) {
            log.debug("Feature {} is unlimited for plan {}", featureType, plan.getName());

            // Get current usage for display purposes
            Optional<UserUsageTracking> tracking = usageTrackingRepository
                    .findByUserAndFeatureType(user, featureType);
            Integer currentUsage = tracking.map(UserUsageTracking::getUsageCount).orElse(0);

            return UsageCheckResult.unlimited(currentUsage);
        }

        // Get or create usage tracking
        UserUsageTracking tracking = getOrCreateUsageTracking(user, featureType, limit.getResetPeriod());

        // Check if period expired and reset if needed
        if (tracking.checkAndResetIfExpired(limit.getResetPeriod())) {
            usageTrackingRepository.save(tracking);
            log.info("Reset usage for user {} feature {} (period expired)", userId, featureType);
        }

        // Check if limit exceeded
        Integer currentUsage = tracking.getUsageCount();
        Integer limitValue = limit.getLimitValue();

        if (tracking.hasReachedLimit(limitValue)) {
            String timeUntilReset = tracking.getFormattedTimeUntilReset();
            log.warn("User {} exceeded limit for feature {}: {}/{}",
                    userId, featureType, currentUsage, limitValue);

            return UsageCheckResult.limitExceeded(
                    currentUsage,
                    limitValue,
                    tracking.getCurrentPeriodEnd(),
                    timeUntilReset);
        }

        // Usage allowed
        String timeUntilReset = tracking.getFormattedTimeUntilReset();
        return UsageCheckResult.allowed(
                currentUsage,
                limitValue,
                tracking.getCurrentPeriodEnd(),
                timeUntilReset);
    }

    @Override
    @Transactional
    public void recordUsage(Long userId, FeatureType featureType) {
        log.debug("Recording usage for user {} feature {}", userId, featureType);

        User user = getUserOrThrow(userId);
        UserSubscription subscription = getActiveSubscriptionOrThrow(user);
        PremiumPlan plan = subscription.getPlan();

        // Get feature limit configuration
        Optional<PlanFeatureLimits> limitConfig = featureLimitsRepository
                .findByPlanAndFeatureTypeAndIsActiveTrue(plan, featureType);

        if (limitConfig.isEmpty()) {
            log.debug("No limit configured for feature {}, not tracking usage", featureType);
            return;
        }

        PlanFeatureLimits limit = limitConfig.get();

        // Don't track unlimited features (optional - can track for analytics)
        if (limit.getIsUnlimited()) {
            log.debug("Feature {} is unlimited, not tracking usage", featureType);
            return;
        }

        // Get or create usage tracking
        UserUsageTracking tracking = getOrCreateUsageTracking(user, featureType, limit.getResetPeriod());

        // Check if period expired and reset if needed
        if (tracking.checkAndResetIfExpired(limit.getResetPeriod())) {
            log.info("Reset usage for user {} feature {} before recording", userId, featureType);
        }

        // Increment usage
        tracking.incrementUsage();
        usageTrackingRepository.save(tracking);

        log.info("Recorded usage for user {} feature {}: {}/{}",
                userId, featureType, tracking.getUsageCount(), limit.getLimitValue());
    }

    @Override
    @Transactional
    public void checkAndRecordUsage(Long userId, FeatureType featureType) {
        UsageCheckResult check = canUseFeature(userId, featureType);

        if (!check.getAllowed()) {
            throw UsageLimitExceededException.fromCheckResult(featureType, check);
        }

        recordUsage(userId, featureType);
    }

    @Override
    @Transactional(readOnly = true)
    public FeatureLimitInfo getUserUsage(Long userId, FeatureType featureType) {
        User user = getUserOrThrow(userId);
        UserSubscription subscription = getActiveSubscriptionOrThrow(user);
        PremiumPlan plan = subscription.getPlan();

        // Get feature limit configuration
        Optional<PlanFeatureLimits> limitConfig = featureLimitsRepository
                .findByPlanAndFeatureTypeAndIsActiveTrue(plan, featureType);

        FeatureLimitInfo.FeatureLimitInfoBuilder builder = FeatureLimitInfo.builder()
                .featureType(featureType)
                .featureName(featureType.getDisplayName())
                .featureNameVi(featureType.getDisplayNameVi());

        if (limitConfig.isEmpty()) {
            // No limit = unlimited
            return builder
                    .isUnlimited(true)
                    .currentUsage(0)
                    .remaining(null)
                    .limit(null)
                    .build();
        }

        PlanFeatureLimits limit = limitConfig.get();

        // Handle unlimited
        if (limit.getIsUnlimited()) {
            Optional<UserUsageTracking> tracking = usageTrackingRepository
                    .findByUserAndFeatureType(user, featureType);
            Integer currentUsage = tracking.map(UserUsageTracking::getUsageCount).orElse(0);

            return builder
                    .isUnlimited(true)
                    .currentUsage(currentUsage)
                    .remaining(null)
                    .limit(null)
                    .build();
        }

        // Handle multiplier features
        if (limit.isMultiplierFeature()) {
            return builder
                    .isUnlimited(false)
                    .bonusMultiplier(limit.getBonusMultiplier())
                    .currentUsage(0)
                    .remaining(null)
                    .limit(null)
                    .build();
        }

        // Handle boolean features
        if (limit.isBooleanFeature()) {
            return builder
                    .isUnlimited(false)
                    .isEnabled(limit.isFeatureEnabled())
                    .currentUsage(0)
                    .remaining(null)
                    .limit(null)
                    .build();
        }

        // Handle regular count-based features
        UserUsageTracking tracking = getOrCreateUsageTracking(user, featureType, limit.getResetPeriod());

        FeatureLimitInfo info = builder
                .limit(limit.getLimitValue())
                .currentUsage(tracking.getUsageCount())
                .resetPeriod(limit.getResetPeriod())
                .nextResetAt(tracking.getCurrentPeriodEnd())
                .timeUntilReset(tracking.getFormattedTimeUntilReset())
                .isUnlimited(false)
                .build();

        // Calculate derived fields
        info.calculateRemaining();
        info.calculateUsagePercentage();

        return info;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeatureLimitInfo> getUserPlanLimits(Long userId) {
        User user = getUserOrThrow(userId);
        UserSubscription subscription = getActiveSubscriptionOrThrow(user);
        PremiumPlan plan = subscription.getPlan();

        List<PlanFeatureLimits> limits = featureLimitsRepository.findByPlanAndIsActiveTrue(plan);
        List<FeatureLimitInfo> result = new ArrayList<>();

        for (PlanFeatureLimits limit : limits) {
            FeatureLimitInfo info = getUserUsage(userId, limit.getFeatureType());
            result.add(info);
        }

        return result;
    }

    @Override
    @Transactional
    @Scheduled(cron = "0 0 * * * ?") // Every hour
    public void resetExpiredUsagePeriods() {
        log.info("Running scheduled task: resetExpiredUsagePeriods");

        LocalDateTime now = LocalDateTime.now();
        List<UserUsageTracking> expiredPeriods = usageTrackingRepository.findExpiredPeriods(now);

        int resetCount = 0;
        for (UserUsageTracking tracking : expiredPeriods) {
            // Get the reset period from plan configuration
            User user = tracking.getUser();
            Optional<UserSubscription> subscription = subscriptionRepository.findByUserAndIsActiveTrue(user);

            if (subscription.isEmpty()) {
                log.warn("No active subscription for user {} during reset, skipping", user.getId());
                continue;
            }

            PremiumPlan plan = subscription.get().getPlan();
            Optional<PlanFeatureLimits> limitConfig = featureLimitsRepository
                    .findByPlanAndFeatureTypeAndIsActiveTrue(plan, tracking.getFeatureType());

            if (limitConfig.isEmpty()) {
                log.warn("No limit config for user {} feature {}, skipping reset",
                        user.getId(), tracking.getFeatureType());
                continue;
            }

            ResetPeriod resetPeriod = limitConfig.get().getResetPeriod();
            tracking.resetUsage(resetPeriod);
            usageTrackingRepository.save(tracking);
            resetCount++;

            log.debug("Reset usage for user {} feature {}", user.getId(), tracking.getFeatureType());
        }

        log.info("Reset {} expired usage periods", resetCount);
    }

    @Override
    @Transactional
    public void initializeUserUsage(Long userId, FeatureType featureType) {
        User user = getUserOrThrow(userId);
        UserSubscription subscription = getActiveSubscriptionOrThrow(user);
        PremiumPlan plan = subscription.getPlan();

        Optional<PlanFeatureLimits> limitConfig = featureLimitsRepository
                .findByPlanAndFeatureTypeAndIsActiveTrue(plan, featureType);

        if (limitConfig.isEmpty()) {
            log.debug("No limit config for feature {}, not initializing tracking", featureType);
            return;
        }

        ResetPeriod resetPeriod = limitConfig.get().getResetPeriod();
        getOrCreateUsageTracking(user, featureType, resetPeriod);

        log.info("Initialized usage tracking for user {} feature {}", userId, featureType);
    }

    @Override
    @Transactional
    public void resetUserUsage(Long userId, FeatureType featureType) {
        User user = getUserOrThrow(userId);
        Optional<UserUsageTracking> tracking = usageTrackingRepository
                .findByUserAndFeatureType(user, featureType);

        if (tracking.isEmpty()) {
            log.debug("No usage tracking found for user {} feature {}", userId, featureType);
            return;
        }

        UserSubscription subscription = getActiveSubscriptionOrThrow(user);
        PremiumPlan plan = subscription.getPlan();
        Optional<PlanFeatureLimits> limitConfig = featureLimitsRepository
                .findByPlanAndFeatureTypeAndIsActiveTrue(plan, featureType);

        if (limitConfig.isEmpty()) {
            log.warn("No limit config found, deleting tracking record");
            usageTrackingRepository.delete(tracking.get());
            return;
        }

        ResetPeriod resetPeriod = limitConfig.get().getResetPeriod();
        tracking.get().resetUsage(resetPeriod);
        usageTrackingRepository.save(tracking.get());

        log.info("Reset usage for user {} feature {}", userId, featureType);
    }

    // ==================== Helper Methods ====================

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
    }

    private UserSubscription getActiveSubscriptionOrThrow(User user) {
        return subscriptionRepository.findByUserAndIsActiveTrue(user)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.NOT_FOUND,
                        "No active subscription found. Please subscribe to a plan."));
    }

    private UserUsageTracking getOrCreateUsageTracking(User user, FeatureType featureType, ResetPeriod resetPeriod) {
        Optional<UserUsageTracking> existing = usageTrackingRepository
                .findByUserAndFeatureType(user, featureType);

        if (existing.isPresent()) {
            return existing.get();
        }

        // Create new tracking record
        UserUsageTracking newTracking = UserUsageTracking.initializeTracking(user, featureType, resetPeriod);
        return usageTrackingRepository.save(newTracking);
    }
}
