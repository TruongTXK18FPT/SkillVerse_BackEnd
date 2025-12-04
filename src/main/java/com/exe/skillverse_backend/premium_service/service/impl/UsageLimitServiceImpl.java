package com.exe.skillverse_backend.premium_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.premium_service.dto.response.FeatureLimitInfo;
import com.exe.skillverse_backend.premium_service.dto.response.UsageCheckResult;
import com.exe.skillverse_backend.premium_service.entity.*;
import com.exe.skillverse_backend.premium_service.exception.UsageLimitExceededException;
import com.exe.skillverse_backend.premium_service.repository.PlanFeatureLimitsRepository;
import com.exe.skillverse_backend.premium_service.repository.UserUsageTrackingRepository;
import com.exe.skillverse_backend.premium_service.repository.PremiumPlanRepository;
import com.exe.skillverse_backend.premium_service.repository.UserSubscriptionRepository;
import com.exe.skillverse_backend.premium_service.service.UsageLimitService;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.CertificateRepository;
import com.exe.skillverse_backend.course_service.repository.AssignmentSubmissionRepository;
import com.exe.skillverse_backend.course_service.repository.LessonProgressRepository;
import com.exe.skillverse_backend.premium_service.dto.response.UserCycleStatsDTO;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Comparator;

/**
 * Implementation of UsageLimitService
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UsageLimitServiceImpl implements UsageLimitService {

    private final UserRepository userRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final PremiumPlanRepository premiumPlanRepository;
    private final PlanFeatureLimitsRepository featureLimitsRepository;
    private final UserUsageTrackingRepository usageTrackingRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final CertificateRepository certificateRepository;
    private final AssignmentSubmissionRepository assignmentSubmissionRepository;
    private final LessonProgressRepository lessonProgressRepository;

    @Override
    @Transactional
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
    @Transactional
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
    @Transactional
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
    @Transactional(readOnly = true)
    public UserCycleStatsDTO getUserCycleStats(Long userId) {
        User user = getUserOrThrow(userId);

        // Determine cycle start (default to 1st of current month)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cycleStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime cycleEnd = cycleStart.plusMonths(1);

        Optional<UserSubscription> subscription = subscriptionRepository.findByUserAndIsActiveTrue(user);
        if (subscription.isPresent()) {
            // If user has subscription, try to align with subscription start date
            // For now, we'll stick to calendar month for simplicity unless detailed billing
            // logic is required
            // cycleStart = subscription.get().getStartDate(); // This would be the absolute
            // start, need to mod by month
        }

        // Convert to Instant for Enrollment Repository
        Instant since = cycleStart.atZone(ZoneId.systemDefault()).toInstant();

        Integer enrolledCount = (int) courseEnrollmentRepository.countEnrollmentsSince(userId, since);
        Integer completedProjectsCount = (int) assignmentSubmissionRepository.countCompletedProjectsByUserId(userId);
        // TODO: [USER NOTE] Certificates are not currently used - this logic returns 0
        // if no certificates exist (safe to keep for future use)
        Integer certificatesCount = (int) certificateRepository.countByUserId(userId);
        Integer totalHours = (int) (courseEnrollmentRepository.sumTotalCourseDurationByUserId(userId) / 3600);

        // Calculate Streak
        List<Instant> activityInstants = lessonProgressRepository.findCompletionInstantsByUserId(userId);
        List<LocalDate> activityDates = activityInstants.stream()
                .map(instant -> instant.atZone(ZoneId.systemDefault()).toLocalDate())
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
        int currentStreak = 0;
        int longestStreak = 0;
        int tempStreak = 0;
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        if (!activityDates.isEmpty()) {
            // Check current streak
            LocalDate lastDate = activityDates.get(0);
            if (lastDate.equals(today) || lastDate.equals(yesterday)) {
                currentStreak = 1;
                tempStreak = 1;
                for (int i = 1; i < activityDates.size(); i++) {
                    LocalDate prev = activityDates.get(i - 1);
                    LocalDate curr = activityDates.get(i);
                    if (prev.minusDays(1).equals(curr)) {
                        currentStreak++;
                    } else {
                        break;
                    }
                }
            }

            // Longest streak calculation
            longestStreak = 1;
            tempStreak = 1;
            for (int i = 1; i < activityDates.size(); i++) {
                LocalDate prev = activityDates.get(i - 1);
                LocalDate curr = activityDates.get(i);
                if (prev.minusDays(1).equals(curr)) {
                    tempStreak++;
                } else {
                    if (tempStreak > longestStreak)
                        longestStreak = tempStreak;
                    tempStreak = 1;
                }
            }
            if (tempStreak > longestStreak)
                longestStreak = tempStreak;
        }

        // TODO: [USER NOTE] Modified to calculate real weekly activity (Mon-Sun) from
        // lesson completion dates (backend-driven for dashboard)
        // Weekly Activity Calculation
        LocalDateTime startOfWeek = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        Instant startOfWeekInstant = startOfWeek.atZone(ZoneId.systemDefault()).toInstant();
        List<Instant> weeklyInstants = lessonProgressRepository.findCompletionInstantsSince(userId, startOfWeekInstant);
        List<LocalDate> weeklyDates = weeklyInstants.stream()
                .map(instant -> instant.atZone(ZoneId.systemDefault()).toLocalDate())
                .distinct()
                .collect(Collectors.toList());

        List<Boolean> weeklyActivity = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = startOfWeek.plusDays(i).toLocalDate();
            weeklyActivity.add(weeklyDates.contains(date));
        }

        return UserCycleStatsDTO.builder()
                .weeklyActivity(weeklyActivity)
                .enrolledCoursesCount(enrolledCount)
                .completedCoursesCount(certificatesCount)
                .completedProjectsCount(completedProjectsCount)
                .certificatesCount(certificatesCount)
                .totalHoursStudied(totalHours)
                .currentStreak(currentStreak)
                .longestStreak(longestStreak)
                .cycleStartDate(cycleStart)
                .cycleEndDate(cycleEnd)
                .build();
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
                .orElseGet(() -> {
                    // SAFETY CHECK: Double check if user truly has no subscription before assigning
                    // FREE_TIER
                    // This prevents overwriting if a paid subscription was just created but not yet
                    // synced or in race condition
                    boolean hasAnyActive = subscriptionRepository.hasActiveSubscription(user, LocalDateTime.now());
                    if (hasAnyActive) {
                        // If repository says true but findByUserAndIsActiveTrue returned empty,
                        // it might be a timing issue or expired-but-active state.
                        // Fetch explicitly to be safe and avoid creating duplicate/free tier.
                        return subscriptionRepository.findByUserAndIsActiveTrue(user)
                                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                                        "Subscription state inconsistent for user " + user.getId()));
                    }

                    // TODO: [USER NOTE] Modified to auto-assign FREE_TIER subscription for users
                    // without active subscriptions (ensures limit enforcement works for all users)
                    // If no active subscription found, assign FREE_TIER automatically
                    log.info("No active subscription found for user {}. Assigning FREE_TIER.", user.getId());

                    PremiumPlan freePlan = premiumPlanRepository
                            .findByPlanTypeAndIsActiveTrue(PremiumPlan.PlanType.FREE_TIER)
                            .orElseThrow(() -> new ApiException(
                                    ErrorCode.NOT_FOUND,
                                    "Free tier plan not configured. Please contact support."));

                    UserSubscription freeSubscription = new UserSubscription();
                    freeSubscription.setUser(user);
                    freeSubscription.setPlan(freePlan);
                    freeSubscription.setIsActive(true);
                    freeSubscription.setStartDate(LocalDateTime.now());
                    // Free tier has no end date, set to 100 years in future
                    freeSubscription.setEndDate(LocalDateTime.now().plusYears(100));

                    return subscriptionRepository.save(freeSubscription);
                });
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
