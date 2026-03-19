package com.exe.skillverse_backend.premium_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.premium_service.dto.response.FeatureLimitInfo;
import com.exe.skillverse_backend.premium_service.dto.response.RecruiterSubscriptionInfoResponse;
import com.exe.skillverse_backend.premium_service.entity.FeatureType;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import com.exe.skillverse_backend.premium_service.entity.UserSubscription;
import com.exe.skillverse_backend.premium_service.repository.PremiumPlanRepository;
import com.exe.skillverse_backend.premium_service.repository.UserSubscriptionRepository;
import com.exe.skillverse_backend.premium_service.service.PremiumService;
import com.exe.skillverse_backend.premium_service.service.RecruiterSubscriptionService;
import com.exe.skillverse_backend.premium_service.service.UsageLimitService;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecruiterSubscriptionServiceImpl implements RecruiterSubscriptionService {

    private final UserSubscriptionRepository subscriptionRepository;
    private final PremiumPlanRepository planRepository;
    private final UsageLimitService usageLimitService;
    private final UserRepository userRepository;
    @Lazy
    private final PremiumService premiumService;

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveRecruiterSubscription(Long userId) {
        return Boolean.TRUE.equals(subscriptionRepository.hasActiveRecruiterSubscription(userId));
    }

    @Override
    @Transactional
    public void validateCanPostJob(Long userId) {
        // Only RECRUITER role can use recruiter subscription
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));
        if (user.getPrimaryRole() != PrimaryRole.RECRUITER) {
            throw new IllegalStateException(
                    "Chỉ tài khoản Recruiter mới có thể đăng tin tuyển dụng.");
        }

        if (!hasActiveRecruiterSubscription(userId)) {
            throw new IllegalStateException(
                    "Bạn cần mua gói Recruiter để đăng tin tuyển dụng. " +
                    "Vui lòng đăng ký gói tại trang Subscription.");
        }

        // Check JOB_POSTING_MONTHLY quota (throws UsageLimitExceededException if exceeded)
        // For unlimited plans, checkAndRecordUsage will pass because isUnlimited=true
        try {
            usageLimitService.checkAndRecordUsage(userId, FeatureType.JOB_POSTING_MONTHLY);
        } catch (Exception e) {
            log.warn("Job posting quota exceeded for user {}: {}", userId, e.getMessage());
            throw new IllegalStateException(
                    "Bạn đã hết quota đăng tin trong tháng này. " +
                    "Vui lòng chờ đến kỳ tiếp theo hoặc nâng cấp gói.");
        }
    }

    @Override
    @Transactional
    public boolean tryUseSubscriptionQuota(Long userId) {
        if (!hasActiveRecruiterSubscription(userId)) {
            // No active subscription — caller should fall back to direct payment
            return false;
        }

        // Has subscription — try to use quota
        try {
            usageLimitService.checkAndRecordUsage(userId, FeatureType.JOB_POSTING_MONTHLY);
            log.info("Subscription quota used for user {} — no wallet charge needed", userId);
            return true;
        } catch (Exception e) {
            log.warn("Job posting quota exceeded for user {}: {}", userId, e.getMessage());
            throw new IllegalStateException(
                    "Bạn đã hết quota đăng tin trong tháng này. " +
                    "Vui lòng chờ đến kỳ tiếp theo hoặc nâng cấp gói.");
        }
    }

    @Override
    @Transactional
    public boolean tryUseShortTermJobQuota(Long userId) {
        if (!hasActiveRecruiterSubscription(userId)) {
            // No active subscription — caller should fall back to direct payment
            return false;
        }

        // Has subscription — try to use quota for short-term job
        try {
            usageLimitService.checkAndRecordUsage(userId, FeatureType.SHORT_TERM_JOB_POSTING);
            log.info("Short-term job subscription quota used for user {} — no wallet charge needed", userId);
            return true;
        } catch (Exception e) {
            log.warn("Short-term job posting quota exceeded for user {}: {}", userId, e.getMessage());
            throw new IllegalStateException(
                    "Bạn đã hết quota đăng tin ngắn hạn trong tháng này. " +
                    "Vui lòng chờ đến kỳ tiếp theo hoặc nâng cấp gói.");
        }
    }

    @Override
    @Transactional
    public void recordJobPosting(Long userId) {
        // Usage is already recorded in validateCanPostJob via checkAndRecordUsage
        // This method is a no-op since checkAndRecordUsage does both check + record
        log.debug("Job posting recorded for user {} (via validateCanPostJob)", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canHighlightJob(Long userId) {
        if (!hasActiveRecruiterSubscription(userId)) {
            return false;
        }

        try {
            FeatureLimitInfo info = usageLimitService.getUserUsage(userId, FeatureType.HIGHLIGHT_JOB_POST);
            return info.getIsEnabled() != null && info.getIsEnabled();
        } catch (Exception e) {
            log.debug("Highlight feature not configured for user {}", userId);
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUseAICandidateSuggestion(Long userId) {
        if (!hasActiveRecruiterSubscription(userId)) {
            return false;
        }

        try {
            FeatureLimitInfo info = usageLimitService.getUserUsage(userId, FeatureType.AI_CANDIDATE_SUGGESTION);
            return info.getIsEnabled() != null && info.getIsEnabled();
        } catch (Exception e) {
            log.debug("AI candidate suggestion not configured for user {}", userId);
            return false;
        }
    }

    @Override
    @Transactional
    public RecruiterSubscriptionInfoResponse getSubscriptionInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));

        boolean hasSub = hasActiveRecruiterSubscription(userId);

        if (!hasSub) {
            // Auto-recovery: check for PENDING RECRUITER_PRO subscriptions with completed payments
            hasSub = tryAutoRecoverPendingSubscription(userId);
        }

        if (!hasSub) {
            // Return info about available plans
            return RecruiterSubscriptionInfoResponse.builder()
                    .hasSubscription(false)
                    .jobPostingLimit(0)
                    .jobPostingUsed(0)
                    .jobPostingRemaining(0)
                    .jobPostingUnlimited(false)
                    .shortTermJobPostingLimit(0)
                    .shortTermJobPostingUsed(0)
                    .shortTermJobPostingRemaining(0)
                    .shortTermJobPostingUnlimited(false)
                    .jobBoostLimit(0)
                    .jobBoostUsed(0)
                    .jobBoostRemaining(0)
                    .canHighlightJobs(false)
                    .canUseAICandidateSuggestion(false)
                    .hasPremiumCompanyProfile(false)
                    .hasAnalyticsDashboard(false)
                    .hasCandidateDatabaseAccess(false)
                    .hasAutomatedOutreach(false)
                    .hasApiAccess(false)
                    .hasPrioritySupport(false)
                    .build();
        }

        // Get active RECRUITER_PRO subscription specifically (not generic subscription)
        UserSubscription subscription = subscriptionRepository
                .findActiveRecruiterSubscription(user)
                .orElseThrow(() -> new NotFoundException("Active recruiter subscription not found"));

        PremiumPlan plan = subscription.getPlan();

        // Get job posting usage info (Full-time)
        FeatureLimitInfo jobPostingInfo = null;
        try {
            jobPostingInfo = usageLimitService.getUserUsage(userId, FeatureType.JOB_POSTING_MONTHLY);
        } catch (Exception e) {
            log.warn("Could not get job posting usage for user {}", userId);
        }

        // Get short-term job posting usage info
        FeatureLimitInfo shortTermJobPostingInfo = null;
        try {
            shortTermJobPostingInfo = usageLimitService.getUserUsage(userId, FeatureType.SHORT_TERM_JOB_POSTING);
        } catch (Exception e) {
            log.warn("Could not get short-term job posting usage for user {}", userId);
        }

        // Get job boost usage info
        FeatureLimitInfo jobBoostInfo = null;
        try {
            jobBoostInfo = usageLimitService.getUserUsage(userId, FeatureType.JOB_BOOST_MONTHLY);
        } catch (Exception e) {
            log.warn("Could not get job boost usage for user {}", userId);
        }

        // Check feature access
        boolean canHighlight = canHighlightJob(userId);
        boolean canAISuggest = canUseAICandidateSuggestion(userId);

        // Check other features
        boolean hasPremiumCompanyProfile = checkFeatureEnabled(userId, FeatureType.COMPANY_PROFILE_PREMIUM);
        boolean hasAnalyticsDashboard = checkFeatureEnabled(userId, FeatureType.ANALYTICS_DASHBOARD);
        boolean hasCandidateDatabaseAccess = checkFeatureEnabled(userId, FeatureType.CANDIDATE_DATABASE_ACCESS);
        boolean hasAutomatedOutreach = checkFeatureEnabled(userId, FeatureType.AUTOMATED_OUTREACH);
        boolean hasApiAccess = checkFeatureEnabled(userId, FeatureType.API_ACCESS);
        boolean hasPrioritySupport = checkFeatureEnabled(userId, FeatureType.RECRUITER_PRIORITY_SUPPORT);

        RecruiterSubscriptionInfoResponse.RecruiterSubscriptionInfoResponseBuilder builder =
                RecruiterSubscriptionInfoResponse.builder()
                        .hasSubscription(true)
                        .planName(plan.getName())
                        .planDisplayName(plan.getDisplayName())
                        .planPrice(plan.getPrice())
                        .durationMonths(plan.getDurationMonths())
                        .startDate(subscription.getStartDate())
                        .endDate(subscription.getEndDate())
                        .daysRemaining(subscription.getDaysRemaining())
                        .autoRenew(subscription.getAutoRenew())
                        .canHighlightJobs(canHighlight)
                        .canUseAICandidateSuggestion(canAISuggest)
                        .hasPremiumCompanyProfile(hasPremiumCompanyProfile)
                        .hasAnalyticsDashboard(hasAnalyticsDashboard)
                        .hasCandidateDatabaseAccess(hasCandidateDatabaseAccess)
                        .hasAutomatedOutreach(hasAutomatedOutreach)
                        .hasApiAccess(hasApiAccess)
                        .hasPrioritySupport(hasPrioritySupport);

        if (jobPostingInfo != null) {
            builder.jobPostingLimit(jobPostingInfo.getLimit())
                    .jobPostingUsed(jobPostingInfo.getCurrentUsage())
                    .jobPostingRemaining(jobPostingInfo.getRemaining())
                    .jobPostingUnlimited(jobPostingInfo.getIsUnlimited())
                    .jobPostingResetInfo(jobPostingInfo.getTimeUntilReset());
        }

        if (shortTermJobPostingInfo != null) {
            builder.shortTermJobPostingLimit(shortTermJobPostingInfo.getLimit())
                    .shortTermJobPostingUsed(shortTermJobPostingInfo.getCurrentUsage())
                    .shortTermJobPostingRemaining(shortTermJobPostingInfo.getRemaining())
                    .shortTermJobPostingUnlimited(shortTermJobPostingInfo.getIsUnlimited())
                    .shortTermJobPostingResetInfo(shortTermJobPostingInfo.getTimeUntilReset());
        }

        if (jobBoostInfo != null) {
            builder.jobBoostLimit(jobBoostInfo.getLimit())
                    .jobBoostUsed(jobBoostInfo.getCurrentUsage())
                    .jobBoostRemaining(jobBoostInfo.getRemaining())
                    .jobBoostResetInfo(jobBoostInfo.getTimeUntilReset());
        }

        return builder.build();
    }

    /**
     * Helper method to check if a feature is enabled for a user
     */
    private boolean checkFeatureEnabled(Long userId, FeatureType featureType) {
        try {
            FeatureLimitInfo info = usageLimitService.getUserUsage(userId, featureType);
            return info.getIsEnabled() != null && info.getIsEnabled();
        } catch (Exception e) {
            log.debug("Feature {} not configured for user {}", featureType, userId);
            return false;
        }
    }

    /**
     * Auto-recovery: find PENDING RECRUITER_PRO subscriptions that have completed payments
     * and activate them. This handles cases where PayOS webhook didn't reach the server
     * or the frontend polling failed to trigger activation.
     */
    @Transactional
    private boolean tryAutoRecoverPendingSubscription(Long userId) {
        try {
            return premiumService.tryRecoverPendingSubscriptions(userId);
        } catch (Exception e) {
            log.warn("Auto-recovery check failed for user {}: {}", userId, e.getMessage());
        }
        return false;
    }
}
