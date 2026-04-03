package com.exe.skillverse_backend.premium_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.repository.AssignmentSubmissionRepository;
import com.exe.skillverse_backend.course_service.repository.CertificateRepository;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.LessonProgressRepository;
import com.exe.skillverse_backend.gamification_service.repository.DailyCheckInRepository;
import com.exe.skillverse_backend.premium_service.dto.response.FeatureLimitInfo;
import com.exe.skillverse_backend.premium_service.dto.response.UsageCheckResult;
import com.exe.skillverse_backend.premium_service.entity.FeatureType;
import com.exe.skillverse_backend.premium_service.entity.PlanFeatureLimits;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import com.exe.skillverse_backend.premium_service.entity.ResetPeriod;
import com.exe.skillverse_backend.premium_service.entity.UserSubscription;
import com.exe.skillverse_backend.premium_service.entity.UserUsageTracking;
import com.exe.skillverse_backend.premium_service.exception.UsageLimitExceededException;
import com.exe.skillverse_backend.premium_service.repository.PlanFeatureLimitsRepository;
import com.exe.skillverse_backend.premium_service.repository.PremiumPlanRepository;
import com.exe.skillverse_backend.premium_service.repository.UserSubscriptionRepository;
import com.exe.skillverse_backend.premium_service.repository.UserUsageTrackingRepository;
import com.exe.skillverse_backend.premium_service.service.impl.UsageLimitServiceImpl;
import com.exe.skillverse_backend.study_service.repository.StudySessionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageLimitServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSubscriptionRepository subscriptionRepository;

    @Mock
    private PremiumPlanRepository premiumPlanRepository;

    @Mock
    private PlanFeatureLimitsRepository featureLimitsRepository;

    @Mock
    private UserUsageTrackingRepository usageTrackingRepository;

    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private AssignmentSubmissionRepository assignmentSubmissionRepository;

    @Mock
    private LessonProgressRepository lessonProgressRepository;

    @Mock
    private StudySessionRepository studySessionRepository;

    @Mock
    private DailyCheckInRepository dailyCheckInRepository;

    private UsageLimitServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UsageLimitServiceImpl(
                userRepository,
                subscriptionRepository,
                premiumPlanRepository,
                featureLimitsRepository,
                usageTrackingRepository,
                courseEnrollmentRepository,
                certificateRepository,
                assignmentSubmissionRepository,
                lessonProgressRepository,
                studySessionRepository,
                dailyCheckInRepository);
    }

    @Test
    @DisplayName("canUseFeature should treat missing feature config as unlimited")
    void canUseFeature_ShouldTreatMissingFeatureConfigAsUnlimited() {
        User user = user();
        PremiumPlan plan = paidPlan();
        UserSubscription subscription = subscription(user, plan);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findCurrentActiveSubscription(user)).thenReturn(Optional.of(subscription));
        when(featureLimitsRepository.findByPlanAndFeatureTypeAndIsActiveTrue(plan, FeatureType.AI_CHATBOT_REQUESTS))
                .thenReturn(Optional.empty());

        UsageCheckResult result = service.canUseFeature(1L, FeatureType.AI_CHATBOT_REQUESTS);

        assertTrue(result.getAllowed());
        assertTrue(result.getIsUnlimited());
        assertNull(result.getLimit());
    }

    @Test
    @DisplayName("canUseFeature should report limit exceeded when tracking reached the configured cap")
    void canUseFeature_ShouldReportLimitExceededWhenTrackingReachedCap() {
        User user = user();
        PremiumPlan plan = paidPlan();
        UserSubscription subscription = subscription(user, plan);
        PlanFeatureLimits limits = PlanFeatureLimits.builder()
                .plan(plan)
                .featureType(FeatureType.AI_CHATBOT_REQUESTS)
                .limitValue(3)
                .resetPeriod(ResetPeriod.DAILY)
                .build();
        UserUsageTracking tracking = UserUsageTracking.builder()
                .id(11L)
                .user(user)
                .featureType(FeatureType.AI_CHATBOT_REQUESTS)
                .usageCount(3)
                .lastResetAt(LocalDateTime.now().minusHours(1))
                .currentPeriodStart(LocalDateTime.now().minusHours(1))
                .currentPeriodEnd(LocalDateTime.now().plusHours(5))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findCurrentActiveSubscription(user)).thenReturn(Optional.of(subscription));
        when(featureLimitsRepository.findByPlanAndFeatureTypeAndIsActiveTrue(plan, FeatureType.AI_CHATBOT_REQUESTS))
                .thenReturn(Optional.of(limits));
        when(usageTrackingRepository.findByUserAndFeatureType(user, FeatureType.AI_CHATBOT_REQUESTS))
                .thenReturn(Optional.of(tracking));

        UsageCheckResult result = service.canUseFeature(1L, FeatureType.AI_CHATBOT_REQUESTS);

        assertFalse(result.getAllowed());
        assertFalse(result.getIsUnlimited());
        assertTrue(result.getCurrentUsage() >= result.getLimit());
    }

    @Test
    @DisplayName("checkAndRecordUsage should throw when the atomic increment cannot pass the limit")
    void checkAndRecordUsage_ShouldThrowWhenAtomicIncrementCannotPassTheLimit() {
        User user = user();
        PremiumPlan plan = paidPlan();
        UserSubscription subscription = subscription(user, plan);
        PlanFeatureLimits limits = PlanFeatureLimits.builder()
                .plan(plan)
                .featureType(FeatureType.AI_CHATBOT_REQUESTS)
                .limitValue(1)
                .resetPeriod(ResetPeriod.DAILY)
                .build();
        UserUsageTracking tracking = UserUsageTracking.builder()
                .id(11L)
                .user(user)
                .featureType(FeatureType.AI_CHATBOT_REQUESTS)
                .usageCount(1)
                .lastResetAt(LocalDateTime.now().minusHours(1))
                .currentPeriodStart(LocalDateTime.now().minusHours(1))
                .currentPeriodEnd(LocalDateTime.now().plusHours(5))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findCurrentActiveSubscription(user)).thenReturn(Optional.of(subscription));
        when(featureLimitsRepository.findByPlanAndFeatureTypeAndIsActiveTrue(plan, FeatureType.AI_CHATBOT_REQUESTS))
                .thenReturn(Optional.of(limits));
        when(usageTrackingRepository.findByUserAndFeatureType(user, FeatureType.AI_CHATBOT_REQUESTS))
                .thenReturn(Optional.of(tracking));
        when(usageTrackingRepository.atomicIncrementIfUnderLimit(11L, 1)).thenReturn(0);
        when(usageTrackingRepository.findById(11L)).thenReturn(Optional.of(tracking));

        assertThrows(UsageLimitExceededException.class,
                () -> service.checkAndRecordUsage(1L, FeatureType.AI_CHATBOT_REQUESTS));
    }

    @Test
    @DisplayName("getUserUsage should expose boolean feature toggles")
    void getUserUsage_ShouldExposeBooleanFeatureToggles() {
        User user = user();
        PremiumPlan plan = paidPlan();
        UserSubscription subscription = subscription(user, plan);
        PlanFeatureLimits limits = PlanFeatureLimits.builder()
                .plan(plan)
                .featureType(FeatureType.PRIORITY_SUPPORT)
                .limitValue(1)
                .resetPeriod(ResetPeriod.MONTHLY)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findCurrentActiveSubscription(user)).thenReturn(Optional.of(subscription));
        when(featureLimitsRepository.findByPlanAndFeatureTypeAndIsActiveTrue(plan, FeatureType.PRIORITY_SUPPORT))
                .thenReturn(Optional.of(limits));

        FeatureLimitInfo info = service.getUserUsage(1L, FeatureType.PRIORITY_SUPPORT);

        assertFalse(info.getIsUnlimited());
        assertTrue(info.getIsEnabled());
        assertNull(info.getLimit());
    }

    private User user() {
        return User.builder()
                .id(1L)
                .email("user@skillverse.vn")
                .build();
    }

    private PremiumPlan paidPlan() {
        return PremiumPlan.builder()
                .id(2L)
                .name("Student")
                .displayName("Student")
                .durationMonths(1)
                .planType(PremiumPlan.PlanType.PREMIUM_BASIC)
                .price(BigDecimal.ONE)
                .build();
    }

    private UserSubscription subscription(User user, PremiumPlan plan) {
        return UserSubscription.builder()
                .id(3L)
                .user(user)
                .plan(plan)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .isActive(true)
                .build();
    }
}
