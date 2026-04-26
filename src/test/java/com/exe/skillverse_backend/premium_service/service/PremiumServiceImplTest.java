package com.exe.skillverse_backend.premium_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.notification_service.service.impl.NotificationServiceImpl;
import com.exe.skillverse_backend.parent_service.repository.ParentStudentLinkRepository;
import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import com.exe.skillverse_backend.payment_service.repository.PaymentTransactionRepository;
import com.exe.skillverse_backend.premium_service.constants.PremiumConstants;
import com.exe.skillverse_backend.premium_service.dto.response.SubscriptionCheckoutPreviewResponse;
import com.exe.skillverse_backend.premium_service.dto.response.UserSubscriptionResponse;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import com.exe.skillverse_backend.premium_service.entity.UserSubscription;
import com.exe.skillverse_backend.premium_service.repository.PremiumPlanRepository;
import com.exe.skillverse_backend.premium_service.repository.SubscriptionCancellationRepository;
import com.exe.skillverse_backend.premium_service.repository.UserSubscriptionRepository;
import com.exe.skillverse_backend.premium_service.service.impl.PremiumServiceImpl;
import com.exe.skillverse_backend.student_verification_service.service.StudentVerificationService;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PremiumServiceImplTest {
    private static final DateTimeFormatter VIETNAMESE_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int AUTO_RENEWAL_INTERVAL_MINUTES = 1;

    @Mock
    private PremiumPlanRepository premiumPlanRepository;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private SubscriptionCancellationRepository cancellationRepository;

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private PremiumEmailService premiumEmailService;

    @Mock
    private NotificationServiceImpl notificationService;

    @Mock
    private ParentStudentLinkRepository parentStudentLinkRepository;

        @Mock
        private StudentVerificationService studentVerificationService;

    private PremiumServiceImpl premiumService;
    private User childUser;
    private UserSubscription pendingSubscription;

    private LocalDateTime alignToNextRenewalTick(LocalDateTime endDate) {
        LocalDateTime normalizedEndDate = endDate.withSecond(0).withNano(0);
        int remainder = normalizedEndDate.getMinute() % AUTO_RENEWAL_INTERVAL_MINUTES;

        if (endDate.getSecond() == 0 && endDate.getNano() == 0 && remainder == 0) {
            return endDate;
        }

        return normalizedEndDate.plusMinutes(remainder == 0
                ? AUTO_RENEWAL_INTERVAL_MINUTES
                : AUTO_RENEWAL_INTERVAL_MINUTES - remainder);
    }

    @BeforeEach
    void setUp() {
        premiumService = Mockito.spy(new PremiumServiceImpl(
                premiumPlanRepository,
                userSubscriptionRepository,
                userRepository,
                paymentTransactionRepository,
                walletService,
                cancellationRepository,
                userProfileService,
                premiumEmailService,
                notificationService,
                parentStudentLinkRepository,
                studentVerificationService,
                new ObjectMapper()));

        childUser = User.builder()
                .id(200L)
                .email("child@test.com")
                .build();

        when(premiumPlanRepository.countActiveSubscriptions(any(PremiumPlan.class))).thenReturn(0L);
        when(studentVerificationService.hasApprovedStudentVerification(any())).thenReturn(true);

        pendingSubscription = UserSubscription.builder()
                .id(555L)
                .user(childUser)
                .status(UserSubscription.SubscriptionStatus.PENDING)
                .isActive(false)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusMonths(1))
                .build();
    }

    @Test
    void tryRecoverPendingSubscriptions_recoversUsingCompletedParentPayment() {
        PaymentTransaction parentPayment = PaymentTransaction.builder()
                .id(900L)
                .user(User.builder().id(999L).email("parent@test.com").build())
                .type(PaymentTransaction.PaymentType.PREMIUM_SUBSCRIPTION)
                .status(PaymentTransaction.PaymentStatus.COMPLETED)
                .internalReference("TXN_PARENT_1")
                .metadata("{\"subscriptionId\":555,\"targetUserId\":200}")
                .build();

        when(userRepository.findById(200L)).thenReturn(Optional.of(childUser));
        when(userSubscriptionRepository.findPendingRecruiterSubscriptions(200L)).thenReturn(Collections.emptyList());
        when(userSubscriptionRepository.findByUserOrderByCreatedAtDesc(eq(childUser), eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of(pendingSubscription)));
        when(paymentTransactionRepository.findByUserAndType(
                childUser,
                PaymentTransaction.PaymentType.PREMIUM_SUBSCRIPTION))
                .thenReturn(Collections.emptyList());
        when(paymentTransactionRepository.findByTypeAndStatus(
                PaymentTransaction.PaymentType.PREMIUM_SUBSCRIPTION,
                PaymentTransaction.PaymentStatus.COMPLETED))
                .thenReturn(List.of(parentPayment));
        doReturn(pendingSubscription).when(premiumService)
                .activateSubscription(555L, "TXN_PARENT_1");

        boolean recovered = premiumService.tryRecoverPendingSubscriptions(200L);

        assertTrue(recovered);
        verify(premiumService).activateSubscription(555L, "TXN_PARENT_1");
    }

    @Test
    void getCheckoutPreview_returnsGraceWindowUpgradeAmountForLearnerWithin72Hours() {
        PremiumPlan currentPlan = PremiumPlan.builder()
                .id(10L)
                .name("premium_basic")
                .displayName("Ky Nang+")
                .description("Basic")
                .durationMonths(1)
                .price(new BigDecimal("79000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_BASIC)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        PremiumPlan targetPlan = PremiumPlan.builder()
                .id(20L)
                .name("premium_plus")
                .displayName("Co Van Pro")
                .description("Plus")
                .durationMonths(1)
                .price(new BigDecimal("249000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_PLUS)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        User selfUser = User.builder()
                .id(300L)
                .email("learner@test.com")
                .primaryRole(PrimaryRole.USER)
                .build();
        UserSubscription activeSubscription = UserSubscription.builder()
                .id(901L)
                .user(selfUser)
                .plan(currentPlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusHours(36))
                .endDate(LocalDateTime.now().plusDays(30))
                .isStudentSubscription(false)
                .autoRenew(true)
                .build();

        when(userRepository.findById(300L)).thenReturn(Optional.of(selfUser));
        when(premiumPlanRepository.findById(20L)).thenReturn(Optional.of(targetPlan));
        when(userSubscriptionRepository.findCurrentActiveSubscription(selfUser))
                .thenReturn(Optional.of(activeSubscription));

        SubscriptionCheckoutPreviewResponse preview = premiumService.getCheckoutPreview(300L, 20L, false, null);

        assertTrue(preview.isEligible());
        assertTrue(preview.isUpgrade());
        assertEquals(SubscriptionCheckoutPreviewResponse.PricingMode.UPGRADE_GRACE_WINDOW, preview.getPricingMode());
        assertEquals(activeSubscription.getId(), preview.getCurrentSubscriptionId());
        assertEquals(new BigDecimal("170000"), preview.getAmountDue());
        assertTrue(preview.getNextRenewalDate().isAfter(LocalDateTime.now().plusDays(29)));
    }

    @Test
    void getCheckoutPreview_appliesConfiguredLearnerDiscountWithoutLegacyFlag() {
        PremiumPlan targetPlan = PremiumPlan.builder()
                .id(210L)
                .name("premium_basic")
                .displayName("Ky Nang+")
                .description("Basic")
                .durationMonths(1)
                .price(new BigDecimal("100000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_BASIC)
                .targetRole(PremiumPlan.TargetRole.LEARNER)
                .studentDiscountPercent(new BigDecimal("25"))
                .isActive(true)
                .build();
        User selfUser = User.builder()
                .id(303L)
                .email("role-discount@test.com")
                .primaryRole(PrimaryRole.USER)
                .build();
        PremiumPlan freePlan = PremiumPlan.builder()
                .id(211L)
                .name("free_tier")
                .displayName("Mien phi")
                .description("Free")
                .durationMonths(999)
                .price(BigDecimal.ZERO)
                .currency("VND")
                .planType(PremiumPlan.PlanType.FREE_TIER)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        UserSubscription freeSubscription = UserSubscription.builder()
                .id(917L)
                .user(selfUser)
                .plan(freePlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusDays(10))
                .endDate(LocalDateTime.now().plusYears(1))
                .autoRenew(false)
                .build();

        when(userRepository.findById(303L)).thenReturn(Optional.of(selfUser));
        when(premiumPlanRepository.findById(210L)).thenReturn(Optional.of(targetPlan));
        when(userSubscriptionRepository.findCurrentActiveSubscriptionForUpdate(selfUser))
                .thenReturn(Optional.of(freeSubscription));

        SubscriptionCheckoutPreviewResponse preview = premiumService.getCheckoutPreview(303L, 210L, false, null);

        assertTrue(preview.isEligible());
        assertEquals(new BigDecimal("75000"), preview.getEffectivePrice());
        assertEquals(new BigDecimal("75000"), preview.getAmountDue());
    }

    @Test
    void getCheckoutPreview_usesCurrentCyclePaidAmountSnapshotForGraceWindowUpgradeCredit() {
        PremiumPlan currentPlan = PremiumPlan.builder()
                .id(14L)
                .name("premium_basic")
                .displayName("Ky Nang+")
                .description("Basic")
                .durationMonths(1)
                .price(new BigDecimal("79000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_BASIC)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        PremiumPlan targetPlan = PremiumPlan.builder()
                .id(24L)
                .name("premium_plus")
                .displayName("Co Van Pro")
                .description("Plus")
                .durationMonths(1)
                .price(new BigDecimal("249000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_PLUS)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        User selfUser = User.builder()
                .id(302L)
                .email("snapshot-credit@test.com")
                .primaryRole(PrimaryRole.USER)
                .build();
        UserSubscription activeSubscription = UserSubscription.builder()
                .id(914L)
                .user(selfUser)
                .plan(currentPlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusHours(18))
                .endDate(LocalDateTime.now().plusDays(30))
                .isStudentSubscription(false)
                .autoRenew(true)
                .currentCyclePaidAmountSnapshot(new BigDecimal("50000"))
                .build();

        when(userRepository.findById(302L)).thenReturn(Optional.of(selfUser));
        when(premiumPlanRepository.findById(24L)).thenReturn(Optional.of(targetPlan));
        when(userSubscriptionRepository.findCurrentActiveSubscription(selfUser))
                .thenReturn(Optional.of(activeSubscription));

        SubscriptionCheckoutPreviewResponse preview = premiumService.getCheckoutPreview(302L, 24L, false, null);

        assertEquals(SubscriptionCheckoutPreviewResponse.PricingMode.UPGRADE_GRACE_WINDOW, preview.getPricingMode());
        assertEquals(new BigDecimal("50000"), preview.getCurrentPlanCredit());
        assertEquals(new BigDecimal("199000"), preview.getAmountDue());
    }

    @Test
    void getCheckoutPreview_downgradeWithin72Hours_isRejectedAndNeverCalculatesNegativeDelta() {
        PremiumPlan currentPlan = PremiumPlan.builder()
                .id(12L)
                .name("premium_plus")
                .displayName("Co Van Pro")
                .description("Plus")
                .durationMonths(1)
                .price(new BigDecimal("249000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_PLUS)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        PremiumPlan targetPlan = PremiumPlan.builder()
                .id(13L)
                .name("premium_basic")
                .displayName("Ky Nang+")
                .description("Basic")
                .durationMonths(1)
                .price(new BigDecimal("79000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_BASIC)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        User selfUser = User.builder()
                .id(301L)
                .email("learner2@test.com")
                .primaryRole(PrimaryRole.USER)
                .build();
        UserSubscription activeSubscription = UserSubscription.builder()
                .id(912L)
                .user(selfUser)
                .plan(currentPlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusHours(24))
                .endDate(LocalDateTime.now().plusDays(30))
                .isStudentSubscription(false)
                .autoRenew(true)
                .build();

        when(userRepository.findById(301L)).thenReturn(Optional.of(selfUser));
        when(premiumPlanRepository.findById(13L)).thenReturn(Optional.of(targetPlan));
        when(userSubscriptionRepository.findCurrentActiveSubscription(selfUser))
                .thenReturn(Optional.of(activeSubscription));

        SubscriptionCheckoutPreviewResponse preview = premiumService.getCheckoutPreview(301L, 13L, false, null);

        assertTrue(preview.isEligible());
        assertFalse(preview.isUpgrade());
        assertTrue(preview.isDowngrade());
        assertEquals(SubscriptionCheckoutPreviewResponse.PricingMode.DOWNGRADE_SCHEDULED, preview.getPricingMode());
        assertEquals(BigDecimal.ZERO, preview.getAmountDue());
        assertEquals("Gói thấp hơn sẽ được đặt lịch và chỉ có hiệu lực khi gói hiện tại kết thúc.", preview.getMessage());
    }

    @Test
    void getCheckoutPreview_usesPlanTypeOrderInsteadOfPriceForLearnerProgression() {
        PremiumPlan currentPlan = PremiumPlan.builder()
                .id(112L)
                .name("student_pack")
                .displayName("Goi Sinh Vien")
                .description("Student")
                .durationMonths(1)
                .price(new BigDecimal("12000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.STUDENT_PACK)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        PremiumPlan targetPlan = PremiumPlan.builder()
                .id(113L)
                .name("premium_basic")
                .displayName("Ky Nang+")
                .description("Basic")
                .durationMonths(1)
                .price(new BigDecimal("11000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_BASIC)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        User selfUser = User.builder()
                .id(1301L)
                .email("plan-order@test.com")
                .primaryRole(PrimaryRole.USER)
                .build();
        UserSubscription activeSubscription = UserSubscription.builder()
                .id(1912L)
                .user(selfUser)
                .plan(currentPlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusHours(20))
                .endDate(LocalDateTime.now().plusDays(30))
                .isStudentSubscription(false)
                .autoRenew(true)
                .build();

        when(userRepository.findById(1301L)).thenReturn(Optional.of(selfUser));
        when(premiumPlanRepository.findById(113L)).thenReturn(Optional.of(targetPlan));
        when(userSubscriptionRepository.findCurrentActiveSubscription(selfUser))
                .thenReturn(Optional.of(activeSubscription));

        SubscriptionCheckoutPreviewResponse preview = premiumService.getCheckoutPreview(1301L, 113L, false, null);

        assertTrue(preview.isEligible());
        assertTrue(preview.isUpgrade());
        assertFalse(preview.isDowngrade());
        assertEquals(SubscriptionCheckoutPreviewResponse.PricingMode.UPGRADE_GRACE_WINDOW, preview.getPricingMode());
    }

    @Test
    void purchaseWithWalletCash_graceWindowUpgradeChargesDeltaAndResetsRenewalDate() {
        PremiumPlan currentPlan = PremiumPlan.builder()
                .id(11L)
                .name("premium_basic")
                .displayName("Ky Nang+")
                .description("Basic")
                .durationMonths(1)
                .price(new BigDecimal("79000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_BASIC)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        PremiumPlan targetPlan = PremiumPlan.builder()
                .id(22L)
                .name("premium_plus")
                .displayName("Co Van Pro")
                .description("Plus")
                .durationMonths(1)
                .price(new BigDecimal("249000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_PLUS)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        User selfUser = User.builder()
                .id(400L)
                .email("learner@test.com")
                .firstName("Le")
                .lastName("Arn")
                .primaryRole(PrimaryRole.USER)
                .build();
        LocalDateTime originalEndDate = LocalDateTime.now().plusDays(12);
        UserSubscription activeSubscription = UserSubscription.builder()
                .id(902L)
                .user(selfUser)
                .plan(currentPlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusHours(24))
                .endDate(originalEndDate)
                .isStudentSubscription(false)
                .autoRenew(true)
                .build();

        when(userRepository.findById(400L)).thenReturn(Optional.of(selfUser));
        when(premiumPlanRepository.findById(22L)).thenReturn(Optional.of(targetPlan));
        when(userSubscriptionRepository.findCurrentActiveSubscription(selfUser))
                .thenReturn(Optional.of(activeSubscription));
        List<UserSubscription> savedSubscriptions = new java.util.ArrayList<>();
        when(userSubscriptionRepository.save(any(UserSubscription.class)))
                .thenAnswer(invocation -> {
                    UserSubscription saved = invocation.getArgument(0);
                    savedSubscriptions.add(saved);
                    return saved;
                });

        UserSubscriptionResponse response = premiumService.purchaseWithWalletCash(400L, 22L, false, null);

        verify(walletService).deductCash(
                eq(400L),
                eq(new BigDecimal("170000")),
                anyString(),
                eq(WalletTransaction.TransactionType.PURCHASE_PREMIUM),
                eq("PREMIUM_SUBSCRIPTION"),
                eq("22"));
        assertEquals(PremiumPlan.PlanType.PREMIUM_PLUS, response.getPlan().getPlanType());
        assertTrue(response.getEndDate().isAfter(LocalDateTime.now().plusDays(29)));
        assertTrue(activeSubscription.getCancelledAt() != null);
        assertEquals(UserSubscription.SubscriptionStatus.CANCELLED, activeSubscription.getStatus());
        assertEquals(new BigDecimal("170000"),
                savedSubscriptions.get(savedSubscriptions.size() - 1).getCurrentCyclePaidAmountSnapshot());
    }

    @Test
    void getCheckoutPreview_returnsFullPriceUpgradeForLearnerOutside72Hours() {
        PremiumPlan currentPlan = PremiumPlan.builder()
                .id(12L)
                .name("premium_basic")
                .displayName("Ky Nang+")
                .description("Basic")
                .durationMonths(1)
                .price(new BigDecimal("79000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_BASIC)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        PremiumPlan targetPlan = PremiumPlan.builder()
                .id(23L)
                .name("premium_plus")
                .displayName("Co Van Pro")
                .description("Plus")
                .durationMonths(1)
                .price(new BigDecimal("249000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_PLUS)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        User selfUser = User.builder()
                .id(410L)
                .email("learner-late@test.com")
                .primaryRole(PrimaryRole.USER)
                .build();
        UserSubscription activeSubscription = UserSubscription.builder()
                .id(906L)
                .user(selfUser)
                .plan(currentPlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusDays(5))
                .endDate(LocalDateTime.now().plusDays(25))
                .isStudentSubscription(false)
                .build();

        when(userRepository.findById(410L)).thenReturn(Optional.of(selfUser));
        when(premiumPlanRepository.findById(23L)).thenReturn(Optional.of(targetPlan));
        when(userSubscriptionRepository.findCurrentActiveSubscription(selfUser))
                .thenReturn(Optional.of(activeSubscription));

        SubscriptionCheckoutPreviewResponse preview = premiumService.getCheckoutPreview(410L, 23L, false, null);

        assertTrue(preview.isEligible());
        assertTrue(preview.isUpgrade());
        assertEquals(SubscriptionCheckoutPreviewResponse.PricingMode.UPGRADE_FULL_PRICE, preview.getPricingMode());
        assertEquals(new BigDecimal("249000"), preview.getAmountDue());
        assertEquals(BigDecimal.ZERO, preview.getCurrentPlanCredit());
        assertTrue(preview.getNextRenewalDate().isAfter(LocalDateTime.now().plusDays(29)));
    }

    @Test
    void getCheckoutPreview_blocksImmediateUpgradeForNonUserRoles() {
        PremiumPlan currentPlan = PremiumPlan.builder()
                .id(13L)
                .name("premium_basic")
                .displayName("Ky Nang+")
                .description("Basic")
                .durationMonths(1)
                .price(new BigDecimal("79000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_BASIC)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        PremiumPlan targetPlan = PremiumPlan.builder()
                .id(24L)
                .name("premium_plus")
                .displayName("Co Van Pro")
                .description("Plus")
                .durationMonths(1)
                .price(new BigDecimal("249000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_PLUS)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        User parentUser = User.builder()
                .id(411L)
                .email("parent@test.com")
                .primaryRole(PrimaryRole.PARENT)
                .build();
        UserSubscription activeSubscription = UserSubscription.builder()
                .id(907L)
                .user(parentUser)
                .plan(currentPlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusHours(12))
                .endDate(LocalDateTime.now().plusDays(30))
                .isStudentSubscription(false)
                .build();

        when(userRepository.findById(411L)).thenReturn(Optional.of(parentUser));
        when(premiumPlanRepository.findById(24L)).thenReturn(Optional.of(targetPlan));
        when(userSubscriptionRepository.findCurrentActiveSubscription(parentUser))
                .thenReturn(Optional.of(activeSubscription));

        SubscriptionCheckoutPreviewResponse preview = premiumService.getCheckoutPreview(411L, 24L, false, null);

        assertFalse(preview.isEligible());
        assertTrue(preview.isUpgrade());
        assertEquals(SubscriptionCheckoutPreviewResponse.PricingMode.UPGRADE_NOT_ALLOWED, preview.getPricingMode());
    }

    @Test
    void purchaseWithWalletCash_fullPriceUpgradeAfter72HoursChargesFullPriceAndResetsRenewalDate() {
        PremiumPlan currentPlan = PremiumPlan.builder()
                .id(14L)
                .name("premium_basic")
                .displayName("Ky Nang+")
                .description("Basic")
                .durationMonths(1)
                .price(new BigDecimal("79000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_BASIC)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        PremiumPlan targetPlan = PremiumPlan.builder()
                .id(25L)
                .name("premium_plus")
                .displayName("Co Van Pro")
                .description("Plus")
                .durationMonths(1)
                .price(new BigDecimal("249000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_PLUS)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        User selfUser = User.builder()
                .id(412L)
                .email("late-upgrade@test.com")
                .firstName("Late")
                .lastName("Upgrade")
                .primaryRole(PrimaryRole.USER)
                .build();
        LocalDateTime originalEndDate = LocalDateTime.now().plusDays(8);
        UserSubscription activeSubscription = UserSubscription.builder()
                .id(908L)
                .user(selfUser)
                .plan(currentPlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusDays(6))
                .endDate(originalEndDate)
                .isStudentSubscription(false)
                .autoRenew(true)
                .build();

        when(userRepository.findById(412L)).thenReturn(Optional.of(selfUser));
        when(premiumPlanRepository.findById(25L)).thenReturn(Optional.of(targetPlan));
        when(userSubscriptionRepository.findCurrentActiveSubscription(selfUser))
                .thenReturn(Optional.of(activeSubscription));
        when(userSubscriptionRepository.save(any(UserSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserSubscriptionResponse response = premiumService.purchaseWithWalletCash(412L, 25L, false, null);

        verify(walletService).deductCash(
                eq(412L),
                eq(new BigDecimal("249000")),
                anyString(),
                eq(WalletTransaction.TransactionType.PURCHASE_PREMIUM),
                eq("PREMIUM_SUBSCRIPTION"),
                eq("25"));
        assertEquals(PremiumPlan.PlanType.PREMIUM_PLUS, response.getPlan().getPlanType());
        assertTrue(response.getEndDate().isAfter(LocalDateTime.now().plusDays(29)));
        assertNotEquals(originalEndDate, response.getEndDate());
        assertEquals(new BigDecimal("249000"), response.getRenewalPrice());
        assertEquals(alignToNextRenewalTick(response.getEndDate()), response.getRenewalAttemptDate());
        assertTrue(response.getRenewalPriceLockedAt() != null);
    }

    @Test
    void enableAutoRenewal_freeTier_throwsValidationError() {
        PremiumPlan freePlan = PremiumPlan.builder()
                .id(1L)
                .name("free_tier")
                .displayName("Mien phi")
                .description("Free")
                .durationMonths(999)
                .price(BigDecimal.ZERO)
                .currency("VND")
                .planType(PremiumPlan.PlanType.FREE_TIER)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        User selfUser = User.builder()
                .id(500L)
                .email("free@test.com")
                .build();
        UserSubscription freeSubscription = UserSubscription.builder()
                .id(903L)
                .user(selfUser)
                .plan(freePlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusDays(30))
                .endDate(LocalDateTime.now().plusYears(1))
                .autoRenew(false)
                .build();

        when(userRepository.findById(500L)).thenReturn(Optional.of(selfUser));
        when(userSubscriptionRepository.findCurrentActiveSubscriptionForUpdate(selfUser))
                .thenReturn(Optional.of(freeSubscription));
        when(userSubscriptionRepository.findPendingScheduledDowngradesForUpdate(selfUser))
                .thenReturn(Collections.emptyList());

        RuntimeException error = assertThrows(RuntimeException.class, () -> premiumService.enableAutoRenewal(500L));

        assertEquals(PremiumConstants.MSG_FREE_TIER_NO_AUTO_RENEW, error.getMessage());
    }

    @Test
    void processAutoRenewals_insufficientBalance_disablesAutoRenewAndNotifiesUser() {
        PremiumPlan premiumPlan = PremiumPlan.builder()
                .id(30L)
                .name("premium_plus")
                .displayName("Co Van Pro")
                .description("Plus")
                .durationMonths(1)
                .price(new BigDecimal("249000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_PLUS)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        User selfUser = User.builder()
                .id(600L)
                .email("renew@test.com")
                .build();
        UserSubscription renewableSubscription = UserSubscription.builder()
                .id(904L)
                .user(selfUser)
                .plan(premiumPlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusDays(28))
                .endDate(LocalDateTime.now().plusDays(2))
                .isStudentSubscription(false)
                .autoRenew(true)
                .build();

        when(userSubscriptionRepository.findSubscriptionsForAutoRenewalForUpdate(any(), eq(UserSubscription.SubscriptionStatus.ACTIVE)))
                .thenReturn(List.of(renewableSubscription));
        when(userSubscriptionRepository.save(any(UserSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(walletService.hasAvailableCash(600L, new BigDecimal("249000")))
                .thenReturn(false);

        premiumService.processAutoRenewals();

        assertFalse(renewableSubscription.getAutoRenew());
        verify(notificationService).createNotification(
                eq(600L),
                eq("Gia hạn tự động thất bại"),
                contains("ví không đủ số dư"),
                eq(NotificationType.WARNING),
                eq("904"));
        verify(premiumEmailService).sendAutoRenewalFailedEmail(
                selfUser,
                renewableSubscription,
                new BigDecimal("249000"));
    }

    @Test
    void processAutoRenewals_studentSubscription_usesStudentPriceForRenewal() {
        PremiumPlan premiumPlan = PremiumPlan.builder()
                .id(32L)
                .name("student_pack")
                .displayName("Goi Sinh Vien")
                .description("Student")
                .durationMonths(1)
                .price(new BigDecimal("249000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.STUDENT_PACK)
                .studentDiscountPercent(new BigDecimal("20"))
                .isActive(true)
                .build();
        User selfUser = User.builder()
                .id(602L)
                .email("student-renew@test.com")
                .build();
        UserSubscription renewableSubscription = UserSubscription.builder()
                .id(909L)
                .user(selfUser)
                .plan(premiumPlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusDays(28))
                .endDate(LocalDateTime.now().plusDays(2))
                .isStudentSubscription(true)
                .autoRenew(true)
                .build();

        when(userSubscriptionRepository.findSubscriptionsForAutoRenewalForUpdate(any(), eq(UserSubscription.SubscriptionStatus.ACTIVE)))
                .thenReturn(List.of(renewableSubscription));
        when(userSubscriptionRepository.save(any(UserSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(walletService.hasAvailableCash(602L, new BigDecimal("199200")))
                .thenReturn(true);

        premiumService.processAutoRenewals();

        verify(walletService).hasAvailableCash(602L, new BigDecimal("199200"));
        verify(walletService).deductCash(
                eq(602L),
                eq(new BigDecimal("199200")),
                anyString(),
                eq(WalletTransaction.TransactionType.PURCHASE_PREMIUM),
                eq("AUTO_RENEWAL"),
                eq("909"));
        verify(notificationService).createNotification(
                eq(602L),
                eq("Gia hạn Premium thành công"),
                contains("199200"),
                eq(NotificationType.PREMIUM_PURCHASE),
                eq("909"));
        verify(premiumEmailService).sendAutoRenewalSuccessEmail(
                selfUser,
                renewableSubscription,
                new BigDecimal("199200"));
    }

    @Test
    void processAutoRenewals_usesLockedSnapshotAndRefreshesNextCycleSnapshot() {
        PremiumPlan premiumPlan = PremiumPlan.builder()
                .id(34L)
                .name("premium_basic")
                .displayName("Ky Nang+")
                .description("Basic")
                .durationMonths(1)
                .price(new BigDecimal("99000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_BASIC)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        User selfUser = User.builder()
                .id(604L)
                .email("locked-renew@test.com")
                .build();
        UserSubscription renewableSubscription = UserSubscription.builder()
                .id(911L)
                .user(selfUser)
                .plan(premiumPlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusDays(28))
                .endDate(LocalDateTime.now().plusDays(2))
                .isStudentSubscription(false)
                .autoRenew(true)
                .renewalPriceSnapshot(new BigDecimal("79000"))
                .renewalPriceLockedAt(LocalDateTime.now().minusDays(10))
                .build();

        when(userSubscriptionRepository.findSubscriptionsForAutoRenewalForUpdate(any(), eq(UserSubscription.SubscriptionStatus.ACTIVE)))
                .thenReturn(List.of(renewableSubscription));
        when(userSubscriptionRepository.save(any(UserSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(walletService.hasAvailableCash(604L, new BigDecimal("79000")))
                .thenReturn(true);

        premiumService.processAutoRenewals();

        verify(walletService).deductCash(
                eq(604L),
                eq(new BigDecimal("79000")),
                anyString(),
                eq(WalletTransaction.TransactionType.PURCHASE_PREMIUM),
                eq("AUTO_RENEWAL"),
                eq("911"));
        assertEquals(new BigDecimal("79000"), renewableSubscription.getCurrentCyclePaidAmountSnapshot());
        assertEquals(new BigDecimal("99000"), renewableSubscription.getRenewalPriceSnapshot());
        assertTrue(renewableSubscription.getRenewalPriceLockedAt() != null);
    }

    @Test
    void processAutoRenewals_operationalFailure_keepsAutoRenewEnabled() {
        PremiumPlan premiumPlan = PremiumPlan.builder()
                .id(31L)
                .name("premium_plus")
                .displayName("Co Van Pro")
                .description("Plus")
                .durationMonths(1)
                .price(new BigDecimal("249000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_PLUS)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        User selfUser = User.builder()
                .id(601L)
                .email("renew-ops@test.com")
                .build();
        UserSubscription renewableSubscription = UserSubscription.builder()
                .id(905L)
                .user(selfUser)
                .plan(premiumPlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusDays(28))
                .endDate(LocalDateTime.now().plusDays(2))
                .isStudentSubscription(false)
                .autoRenew(true)
                .build();

        when(userSubscriptionRepository.findSubscriptionsForAutoRenewalForUpdate(any(), eq(UserSubscription.SubscriptionStatus.ACTIVE)))
                .thenReturn(List.of(renewableSubscription));
        when(walletService.hasAvailableCash(601L, new BigDecimal("249000")))
                .thenReturn(true);
        doThrow(new RuntimeException("Database timeout")).when(walletService)
                .deductCash(
                        eq(601L),
                        eq(new BigDecimal("249000")),
                        anyString(),
                        eq(WalletTransaction.TransactionType.PURCHASE_PREMIUM),
                        eq("AUTO_RENEWAL"),
                        eq("905"));

        premiumService.processAutoRenewals();

        assertTrue(renewableSubscription.getAutoRenew());
        verify(notificationService, never()).createNotification(
                eq(601L),
                eq("Gia hạn tự động thất bại"),
                anyString(),
                eq(NotificationType.WARNING),
                eq("905"));
        verify(premiumEmailService, never()).sendAutoRenewalFailedEmail(
                eq(selfUser),
                eq(renewableSubscription),
                any(BigDecimal.class));
    }

    @Test
    void enableAutoRenewal_notifiesRenewalAmountAndAttemptDate() {
        PremiumPlan premiumPlan = PremiumPlan.builder()
                .id(33L)
                .name("premium_basic")
                .displayName("Ky Nang+")
                .description("Basic")
                .durationMonths(1)
                .price(new BigDecimal("79000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_BASIC)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        User selfUser = User.builder()
                .id(603L)
                .email("enable-renew@test.com")
                .build();
        UserSubscription activeSubscription = UserSubscription.builder()
                .id(910L)
                .user(selfUser)
                .plan(premiumPlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusDays(15))
                .endDate(LocalDateTime.now().plusDays(20))
                .isStudentSubscription(false)
                .autoRenew(false)
                .build();

        when(userRepository.findById(603L)).thenReturn(Optional.of(selfUser));
        when(userSubscriptionRepository.findCurrentActiveSubscriptionForUpdate(selfUser))
                .thenReturn(Optional.of(activeSubscription));
        when(userSubscriptionRepository.findPendingScheduledDowngradesForUpdate(selfUser))
                .thenReturn(Collections.emptyList());
        when(userSubscriptionRepository.save(any(UserSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        premiumService.enableAutoRenewal(603L);

        LocalDateTime expectedAttemptDate = alignToNextRenewalTick(activeSubscription.getEndDate());

        assertTrue(activeSubscription.getAutoRenew());
        assertEquals(new BigDecimal("79000"), activeSubscription.getRenewalPriceSnapshot());
        assertTrue(activeSubscription.getRenewalPriceLockedAt() != null);
        verify(notificationService).createNotification(
                eq(603L),
                eq("Bật gia hạn tự động"),
                contains("79000"),
                eq(NotificationType.PREMIUM_PURCHASE),
                eq("910"));
        verify(notificationService).createNotification(
                eq(603L),
                eq("Bật gia hạn tự động"),
                contains(expectedAttemptDate.format(VIETNAMESE_DATE_TIME_FORMATTER)),
                eq(NotificationType.PREMIUM_PURCHASE),
                eq("910"));
    }

    @Test
    void activateSubscription_recordsCurrentCyclePaidAmountSnapshotFromPayment() {
        PremiumPlan premiumPlan = PremiumPlan.builder()
                .id(36L)
                .name("premium_basic")
                .displayName("Ky Nang+")
                .description("Basic")
                .durationMonths(1)
                .price(new BigDecimal("79000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_BASIC)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        User selfUser = User.builder()
                .id(906L)
                .email("activation-snapshot@test.com")
                .build();
        UserSubscription pendingActivationSubscription = UserSubscription.builder()
                .id(916L)
                .user(selfUser)
                .plan(premiumPlan)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusMonths(1))
                .isActive(false)
                .status(UserSubscription.SubscriptionStatus.PENDING)
                .build();
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(1900L)
                .user(selfUser)
                .amount(new BigDecimal("123456"))
                .type(PaymentTransaction.PaymentType.PREMIUM_SUBSCRIPTION)
                .status(PaymentTransaction.PaymentStatus.COMPLETED)
                .paymentMethod(PaymentTransaction.PaymentMethod.PAYOS)
                .internalReference("TXN_PHASE3_ACTIVATE")
                .build();

        when(userSubscriptionRepository.findById(916L)).thenReturn(Optional.of(pendingActivationSubscription));
        when(paymentTransactionRepository.findByInternalReference("TXN_PHASE3_ACTIVATE"))
                .thenReturn(Optional.of(paymentTransaction));
        when(userSubscriptionRepository.save(any(UserSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserSubscription activated = premiumService.activateSubscription(916L, "TXN_PHASE3_ACTIVATE");

        assertEquals(paymentTransaction, activated.getPaymentTransaction());
        assertEquals(new BigDecimal("123456"), activated.getCurrentCyclePaidAmountSnapshot());
    }

    @Test
    void getCurrentSubscription_alignsRenewalAttemptDateToNextRenewalTick() {
        PremiumPlan premiumPlan = PremiumPlan.builder()
                .id(35L)
                .name("premium_basic")
                .displayName("Ky Nang+")
                .description("Basic")
                .durationMonths(1)
                .price(new BigDecimal("79000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_BASIC)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        User selfUser = User.builder()
                .id(905L)
                .email("renew-attempt@test.com")
                .build();
        LocalDateTime endDate = LocalDateTime.of(2026, 3, 27, 15, 3, 12);
        UserSubscription activeSubscription = UserSubscription.builder()
                .id(915L)
                .user(selfUser)
                .plan(premiumPlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(endDate.minusDays(30))
                .endDate(endDate)
                .isStudentSubscription(false)
                .autoRenew(true)
                .build();

        when(userRepository.findById(905L)).thenReturn(Optional.of(selfUser));
        when(userSubscriptionRepository.findCurrentActiveSubscription(selfUser))
                .thenReturn(Optional.of(activeSubscription));

        Optional<UserSubscriptionResponse> response = premiumService.getCurrentSubscription(905L);

        assertTrue(response.isPresent());
        assertEquals(alignToNextRenewalTick(endDate), response.get().getRenewalAttemptDate());
    }

    @Test
    void getCurrentSubscription_reconcilesDueScheduledDowngradeOnRead() {
        PremiumPlan targetPlan = PremiumPlan.builder()
                .id(351L)
                .name("student_pack")
                .displayName("Goi Sinh Vien")
                .description("Student")
                .durationMonths(1)
                .price(new BigDecimal("11000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.STUDENT_PACK)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        User user = User.builder()
                .id(3501L)
                .email("reconcile-scheduled@test.com")
                .build();
        UserSubscription scheduledDowngrade = UserSubscription.builder()
                .id(3510L)
                .user(user)
                .plan(targetPlan)
                .isActive(false)
                .status(UserSubscription.SubscriptionStatus.PENDING)
                .startDate(LocalDateTime.now().minusMinutes(2))
                .endDate(LocalDateTime.now().plusMonths(1))
                .cancellationReason("SCHEDULED_DOWNGRADE:351")
                .autoRenew(false)
                .build();

        when(userRepository.findById(3501L)).thenReturn(Optional.of(user));
        when(userSubscriptionRepository.findDueScheduledDowngradesForUserForUpdate(eq(user), any(LocalDateTime.class)))
                .thenReturn(List.of(scheduledDowngrade));
        when(userSubscriptionRepository.hasActiveNonFreeSubscription(eq(user), any(LocalDateTime.class)))
                .thenReturn(false);
        when(userSubscriptionRepository.save(any(UserSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userSubscriptionRepository.findCurrentActiveSubscription(user))
                .thenReturn(Optional.of(scheduledDowngrade));

        Optional<UserSubscriptionResponse> response = premiumService.getCurrentSubscription(3501L);

        assertTrue(response.isPresent());
        assertTrue(scheduledDowngrade.getIsActive());
        assertEquals(UserSubscription.SubscriptionStatus.ACTIVE, scheduledDowngrade.getStatus());
        assertEquals(targetPlan.getDisplayName(), response.get().getPlan().getDisplayName());
    }

    @Test
    void hasActivePremiumSubscription_reconcilesDueAutoRenewalOnRead() {
        PremiumPlan premiumPlan = PremiumPlan.builder()
                .id(352L)
                .name("premium_basic")
                .displayName("Ky Nang+")
                .description("Basic")
                .durationMonths(1)
                .price(new BigDecimal("79000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_BASIC)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        User user = User.builder()
                .id(3502L)
                .email("reconcile-autorenew@test.com")
                .build();
        LocalDateTime originalEndDate = LocalDateTime.now().minusMinutes(1);
        UserSubscription dueAutoRenew = UserSubscription.builder()
                .id(3520L)
                .user(user)
                .plan(premiumPlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(originalEndDate.minusMonths(1))
                .endDate(originalEndDate)
                .renewalPriceSnapshot(new BigDecimal("79000"))
                .renewalPriceLockedAt(LocalDateTime.now().minusDays(1))
                .autoRenew(true)
                .build();

        when(userRepository.findById(3502L)).thenReturn(Optional.of(user));
        when(userSubscriptionRepository.findDueScheduledDowngradesForUserForUpdate(eq(user), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(userSubscriptionRepository.findDueAutoRenewSubscriptionsForUserForUpdate(eq(user), any(LocalDateTime.class)))
                .thenReturn(List.of(dueAutoRenew));
        when(userSubscriptionRepository.hasPendingScheduledDowngrade(user)).thenReturn(false);
        when(walletService.hasAvailableCash(3502L, new BigDecimal("79000"))).thenReturn(true);
        when(userSubscriptionRepository.save(any(UserSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userSubscriptionRepository.hasActiveNonFreeSubscription(eq(user), any(LocalDateTime.class)))
                .thenReturn(true);

        boolean activePremium = premiumService.hasActivePremiumSubscription(3502L);

        assertTrue(activePremium);
        assertEquals(originalEndDate, dueAutoRenew.getStartDate());
        assertTrue(dueAutoRenew.getEndDate().isAfter(originalEndDate));
        assertEquals(new BigDecimal("79000"), dueAutoRenew.getCurrentCyclePaidAmountSnapshot());
        verify(walletService).deductCash(
                eq(3502L),
                eq(new BigDecimal("79000")),
                contains("Gia hạn tự động"),
                eq(WalletTransaction.TransactionType.PURCHASE_PREMIUM),
                eq("AUTO_RENEWAL"),
                eq("3520"));
    }

    @Test
    void ensureActiveSubscriptionOrFree_reconcilesExpiredSubscriptionOnRead() {
        PremiumPlan expiredPlan = PremiumPlan.builder()
                .id(353L)
                .name("premium_plus")
                .displayName("Premium Sieu Xin")
                .description("Plus")
                .durationMonths(1)
                .price(new BigDecimal("12000"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_PLUS)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        PremiumPlan freePlan = PremiumPlan.builder()
                .id(354L)
                .name("free_tier")
                .displayName("Mien phi")
                .description("Free")
                .durationMonths(999)
                .price(BigDecimal.ZERO)
                .currency("VND")
                .planType(PremiumPlan.PlanType.FREE_TIER)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        User user = User.builder()
                .id(3503L)
                .email("reconcile-expired@test.com")
                .build();
        UserSubscription expiredSubscription = UserSubscription.builder()
                .id(3530L)
                .user(user)
                .plan(expiredPlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusMonths(1))
                .endDate(LocalDateTime.now().minusMinutes(2))
                .autoRenew(false)
                .build();
        UserSubscription freeTierSubscription = UserSubscription.builder()
                .id(3540L)
                .user(user)
                .plan(freePlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusYears(100))
                .autoRenew(false)
                .build();

        when(userRepository.findById(3503L)).thenReturn(Optional.of(user));
        when(userSubscriptionRepository.findDueScheduledDowngradesForUserForUpdate(eq(user), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(userSubscriptionRepository.findDueAutoRenewSubscriptionsForUserForUpdate(eq(user), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(userSubscriptionRepository.findExpiredActiveSubscriptionsForUserForUpdate(eq(user), any(LocalDateTime.class)))
                .thenReturn(List.of(expiredSubscription));
        when(userSubscriptionRepository.save(any(UserSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(premiumService).assignFreeTierIfMissing(3503L);
        when(userSubscriptionRepository.findCurrentActiveSubscription(user))
                .thenReturn(Optional.of(freeTierSubscription));

        UserSubscriptionResponse response = premiumService.ensureActiveSubscriptionOrFree(3503L);

        assertEquals(UserSubscription.SubscriptionStatus.EXPIRED, expiredSubscription.getStatus());
        assertFalse(expiredSubscription.getIsActive());
        verify(premiumService).assignFreeTierIfMissing(3503L);
        assertEquals(freePlan.getDisplayName(), response.getPlan().getDisplayName());
    }

    @Test
    void cancelSubscriptionWithRefund_studentSubscription_usesConfiguredStudentPriceWithoutHardcodedMultiplier() {
        User studentUser = User.builder()
                .id(900L)
                .email("student-refund@test.com")
                .build();
        PremiumPlan premiumPlan = PremiumPlan.builder()
                .id(990L)
                .name("premium_basic")
                .displayName("Ky Nang+")
                .description("Basic")
                .durationMonths(1)
                .price(new BigDecimal("100"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_BASIC)
                .studentDiscountPercent(new BigDecimal("20"))
                .isActive(true)
                .build();
        UserSubscription subscription = UserSubscription.builder()
                .id(991L)
                .user(studentUser)
                .plan(premiumPlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusHours(4))
                .endDate(LocalDateTime.now().plusDays(30))
                .isStudentSubscription(true)
                .autoRenew(true)
                .build();

        when(userRepository.findById(900L)).thenReturn(Optional.of(studentUser));
        when(userSubscriptionRepository.findCurrentActiveSubscription(studentUser)).thenReturn(Optional.of(subscription));
        when(cancellationRepository.countByUserAndCancellationMonth(eq(studentUser), anyString())).thenReturn(0L);
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cancellationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(premiumService).assignFreeTierIfMissing(900L);

        double refundAmount = premiumService.cancelSubscriptionWithRefund(900L, "Student refund");

        assertEquals(80.0, refundAmount);
        verify(walletService).processRefund(
                eq(900L),
                argThat(amount -> amount.compareTo(new BigDecimal("80")) == 0),
                contains("Hoàn tiền 100%"),
                eq("SUBSCRIPTION_REFUND"),
                anyString());
    }

    @Test
    void cancelSubscriptionWithRefund_usesCurrentCyclePaidAmountSnapshot() {
        User user = User.builder()
                .id(9050L)
                .email("snapshot-refund@test.com")
                .build();
        PremiumPlan premiumPlan = PremiumPlan.builder()
                .id(1990L)
                .name("premium_plus")
                .displayName("Co Van Pro")
                .description("Plus")
                .durationMonths(1)
                .price(new BigDecimal("300"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_PLUS)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        UserSubscription subscription = UserSubscription.builder()
                .id(1991L)
                .user(user)
                .plan(premiumPlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusHours(3))
                .endDate(LocalDateTime.now().plusDays(30))
                .isStudentSubscription(false)
                .autoRenew(true)
                .currentCyclePaidAmountSnapshot(new BigDecimal("100"))
                .build();

        when(userRepository.findById(9050L)).thenReturn(Optional.of(user));
        when(userSubscriptionRepository.findCurrentActiveSubscription(user)).thenReturn(Optional.of(subscription));
        when(cancellationRepository.countByUserAndCancellationMonth(eq(user), anyString())).thenReturn(0L);
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cancellationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(premiumService).assignFreeTierIfMissing(9050L);

        double refundAmount = premiumService.cancelSubscriptionWithRefund(9050L, "Snapshot refund");

        assertEquals(100.0, refundAmount);
    }

    @Test
    void getRefundEligibility_studentSubscription_usesConfiguredStudentPriceWithoutHardcodedMultiplier() {
        User studentUser = User.builder()
                .id(901L)
                .email("student-eligibility@test.com")
                .build();
        PremiumPlan premiumPlan = PremiumPlan.builder()
                .id(992L)
                .name("premium_plus")
                .displayName("Co Van Pro")
                .description("Plus")
                .durationMonths(1)
                .price(new BigDecimal("200"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_PLUS)
                .studentDiscountPercent(new BigDecimal("35"))
                .isActive(true)
                .build();
        UserSubscription subscription = UserSubscription.builder()
                .id(993L)
                .user(studentUser)
                .plan(premiumPlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusHours(12))
                .endDate(LocalDateTime.now().plusDays(30))
                .isStudentSubscription(true)
                .autoRenew(true)
                .build();

        when(userRepository.findById(901L)).thenReturn(Optional.of(studentUser));
        when(userSubscriptionRepository.findCurrentActiveSubscription(studentUser)).thenReturn(Optional.of(subscription));
        when(cancellationRepository.countByUserAndCancellationMonth(eq(studentUser), anyString())).thenReturn(0L);

        PremiumService.RefundEligibility eligibility = premiumService.getRefundEligibility(901L);

        assertTrue(eligibility.eligible());
        assertEquals(100, eligibility.refundPercentage());
        assertEquals(130.0, eligibility.refundAmount());
    }

    @Test
    void getRefundEligibility_withinSeventyTwoHours_returnsFiftyPercent() {
        User user = User.builder()
                .id(902L)
                .email("refund-72h@test.com")
                .build();
        PremiumPlan premiumPlan = PremiumPlan.builder()
                .id(994L)
                .name("premium_basic")
                .displayName("Ky Nang+")
                .description("Basic")
                .durationMonths(1)
                .price(new BigDecimal("300"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_BASIC)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        UserSubscription subscription = UserSubscription.builder()
                .id(995L)
                .user(user)
                .plan(premiumPlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusHours(72))
                .endDate(LocalDateTime.now().plusDays(30))
                .isStudentSubscription(false)
                .autoRenew(true)
                .build();

        when(userRepository.findById(902L)).thenReturn(Optional.of(user));
        when(userSubscriptionRepository.findCurrentActiveSubscription(user)).thenReturn(Optional.of(subscription));
        when(cancellationRepository.countByUserAndCancellationMonth(eq(user), anyString())).thenReturn(0L);

        PremiumService.RefundEligibility eligibility = premiumService.getRefundEligibility(902L);

        assertTrue(eligibility.eligible());
        assertEquals(50, eligibility.refundPercentage());
        assertEquals(150.0, eligibility.refundAmount());
    }

    @Test
    void getRefundEligibility_overSeventyTwoHours_returnsNoRefund() {
        User user = User.builder()
                .id(903L)
                .email("refund-73h@test.com")
                .build();
        PremiumPlan premiumPlan = PremiumPlan.builder()
                .id(996L)
                .name("premium_plus")
                .displayName("Co Van Pro")
                .description("Plus")
                .durationMonths(1)
                .price(new BigDecimal("300"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_PLUS)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        UserSubscription subscription = UserSubscription.builder()
                .id(997L)
                .user(user)
                .plan(premiumPlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusHours(73))
                .endDate(LocalDateTime.now().plusDays(30))
                .isStudentSubscription(false)
                .autoRenew(true)
                .build();

        when(userRepository.findById(903L)).thenReturn(Optional.of(user));
        when(userSubscriptionRepository.findCurrentActiveSubscription(user)).thenReturn(Optional.of(subscription));
        when(cancellationRepository.countByUserAndCancellationMonth(eq(user), anyString())).thenReturn(0L);

        PremiumService.RefundEligibility eligibility = premiumService.getRefundEligibility(903L);

        assertTrue(eligibility.eligible());
        assertEquals(0, eligibility.refundPercentage());
        assertEquals(0.0, eligibility.refundAmount());
    }

    @Test
    void getRefundEligibility_whenMonthlyCancellationLimitReached_returnsNonEligibleResponse() {
        User user = User.builder()
                .id(9030L)
                .email("refund-limit@test.com")
                .build();
        PremiumPlan premiumPlan = PremiumPlan.builder()
                .id(9960L)
                .name("premium_basic")
                .displayName("Ky Nang+")
                .description("Basic")
                .durationMonths(1)
                .price(new BigDecimal("100"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_BASIC)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        UserSubscription subscription = UserSubscription.builder()
                .id(9970L)
                .user(user)
                .plan(premiumPlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusHours(3))
                .endDate(LocalDateTime.now().plusDays(30))
                .isStudentSubscription(false)
                .autoRenew(true)
                .build();

        when(userRepository.findById(9030L)).thenReturn(Optional.of(user));
        when(userSubscriptionRepository.findCurrentActiveSubscription(user)).thenReturn(Optional.of(subscription));
        when(cancellationRepository.countByUserAndCancellationMonth(eq(user), anyString())).thenReturn(1L);

        PremiumService.RefundEligibility eligibility = premiumService.getRefundEligibility(9030L);

        assertFalse(eligibility.eligible());
        assertEquals(0, eligibility.refundPercentage());
        assertEquals(0.0, eligibility.refundAmount());
        assertTrue(eligibility.message().contains("1 lần/tháng"));
    }

    @Test
    void getRefundEligibility_whenNoActiveSubscription_returnsBusinessMessage() {
        User user = User.builder()
                .id(9031L)
                .email("refund-none@test.com")
                .build();

        when(userRepository.findById(9031L)).thenReturn(Optional.of(user));
        when(userSubscriptionRepository.findCurrentActiveSubscription(user)).thenReturn(Optional.empty());

        PremiumService.RefundEligibility eligibility = premiumService.getRefundEligibility(9031L);

        assertFalse(eligibility.eligible());
        assertEquals(0, eligibility.refundPercentage());
        assertEquals(0.0, eligibility.refundAmount());
        assertEquals(PremiumConstants.MSG_NO_ACTIVE_SUBSCRIPTION, eligibility.message());
    }

    @Test
    void getRefundEligibility_whenFreeTier_returnsBusinessMessage() {
        User user = User.builder()
                .id(9032L)
                .email("refund-free@test.com")
                .build();
        PremiumPlan freePlan = PremiumPlan.builder()
                .id(9962L)
                .name("free_tier")
                .displayName("Miễn phí")
                .description("Free")
                .durationMonths(1)
                .price(BigDecimal.ZERO)
                .currency("VND")
                .planType(PremiumPlan.PlanType.FREE_TIER)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        UserSubscription subscription = UserSubscription.builder()
                .id(9972L)
                .user(user)
                .plan(freePlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusHours(3))
                .endDate(LocalDateTime.now().plusDays(30))
                .isStudentSubscription(false)
                .autoRenew(false)
                .build();

        when(userRepository.findById(9032L)).thenReturn(Optional.of(user));
        when(userSubscriptionRepository.findCurrentActiveSubscription(user)).thenReturn(Optional.of(subscription));

        PremiumService.RefundEligibility eligibility = premiumService.getRefundEligibility(9032L);

        assertFalse(eligibility.eligible());
        assertEquals(0, eligibility.refundPercentage());
        assertEquals(0.0, eligibility.refundAmount());
        assertEquals(PremiumConstants.MSG_FREE_TIER_NO_REFUND, eligibility.message());
    }

    @Test
    void cancelSubscription_assignsFreeTierFallback() {
        User user = User.builder()
                .id(904L)
                .email("legacy-cancel@test.com")
                .build();
        PremiumPlan premiumPlan = PremiumPlan.builder()
                .id(998L)
                .name("premium_basic")
                .displayName("Ky Nang+")
                .description("Basic")
                .durationMonths(1)
                .price(new BigDecimal("79"))
                .currency("VND")
                .planType(PremiumPlan.PlanType.PREMIUM_BASIC)
                .studentDiscountPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        UserSubscription subscription = UserSubscription.builder()
                .id(999L)
                .user(user)
                .plan(premiumPlan)
                .isActive(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusDays(3))
                .endDate(LocalDateTime.now().plusDays(27))
                .isStudentSubscription(false)
                .autoRenew(true)
                .build();

        when(userRepository.findById(904L)).thenReturn(Optional.of(user));
        when(userSubscriptionRepository.findCurrentActiveSubscription(user)).thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(premiumService).assignFreeTierIfMissing(904L);

        premiumService.cancelSubscription(904L, "Legacy cancel");

        assertFalse(subscription.getIsActive());
        assertEquals(UserSubscription.SubscriptionStatus.CANCELLED, subscription.getStatus());
        verify(premiumService).assignFreeTierIfMissing(904L);
    }
}
