package com.exe.skillverse_backend.premium_service.service;

import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.premium_service.dto.response.FeatureLimitInfo;
import com.exe.skillverse_backend.premium_service.dto.response.RecruiterSubscriptionInfoResponse;
import com.exe.skillverse_backend.premium_service.entity.FeatureType;
import com.exe.skillverse_backend.premium_service.repository.PremiumPlanRepository;
import com.exe.skillverse_backend.premium_service.repository.UserSubscriptionRepository;
import com.exe.skillverse_backend.premium_service.service.impl.RecruiterSubscriptionServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecruiterSubscriptionServiceImplTest {

    @Mock
    private UserSubscriptionRepository subscriptionRepository;

    @Mock
    private PremiumPlanRepository planRepository;

    @Mock
    private UsageLimitService usageLimitService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PremiumService premiumService;

    private RecruiterSubscriptionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RecruiterSubscriptionServiceImpl(
                subscriptionRepository,
                planRepository,
                usageLimitService,
                userRepository,
                premiumService);
    }

    @Test
    @DisplayName("validateCanPostJob should reject users who are not recruiters")
    void validateCanPostJob_ShouldRejectUsersWhoAreNotRecruiters() {
        User user = User.builder().id(1L).primaryRole(PrimaryRole.USER).build();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThrows(IllegalStateException.class, () -> service.validateCanPostJob(user.getId()));
    }

    @Test
    @DisplayName("tryUseSubscriptionQuota should return false when there is no active recruiter subscription")
    void tryUseSubscriptionQuota_ShouldReturnFalseWhenNoActiveSubscriptionExists() {
        when(subscriptionRepository.hasActiveRecruiterSubscription(5L)).thenReturn(false);

        assertFalse(service.tryUseSubscriptionQuota(5L));
    }

    @Test
    @DisplayName("canHighlightJob should return true when the feature is enabled")
    void canHighlightJob_ShouldReturnTrueWhenFeatureIsEnabled() {
        when(subscriptionRepository.hasActiveRecruiterSubscription(5L)).thenReturn(true);
        when(usageLimitService.getUserUsage(5L, FeatureType.HIGHLIGHT_JOB_POST))
                .thenReturn(FeatureLimitInfo.builder()
                        .featureType(FeatureType.HIGHLIGHT_JOB_POST)
                        .isEnabled(true)
                        .build());

        assertTrue(service.canHighlightJob(5L));
    }

    @Test
    @DisplayName("getSubscriptionInfo should return an empty subscription summary when nothing is active")
    void getSubscriptionInfo_ShouldReturnEmptySubscriptionSummaryWhenNothingIsActive() {
        User recruiter = User.builder().id(5L).primaryRole(PrimaryRole.RECRUITER).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(recruiter));
        when(subscriptionRepository.hasActiveRecruiterSubscription(5L)).thenReturn(false);
        when(premiumService.tryRecoverPendingSubscriptions(5L)).thenReturn(false);

        RecruiterSubscriptionInfoResponse response = service.getSubscriptionInfo(5L);

        assertFalse(response.isHasSubscription());
        assertFalse(response.isCanHighlightJobs());
        assertEqualsInt(0, response.getJobPostingLimit());
    }

    private void assertEqualsInt(int expected, Integer actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
