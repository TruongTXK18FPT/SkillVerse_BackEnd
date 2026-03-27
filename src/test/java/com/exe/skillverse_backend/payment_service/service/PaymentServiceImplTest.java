package com.exe.skillverse_backend.payment_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.repository.CoursePurchaseRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.service.EnrollmentService;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.payment_service.dto.request.CreatePaymentRequest;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import com.exe.skillverse_backend.payment_service.repository.PaymentTransactionRepository;
import com.exe.skillverse_backend.payment_service.service.impl.PayOSGatewayService;
import com.exe.skillverse_backend.payment_service.service.impl.PaymentServiceImpl;
import com.exe.skillverse_backend.premium_service.service.PremiumService;
import com.exe.skillverse_backend.shared.service.EmailService;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import com.exe.skillverse_backend.wallet_service.repository.WalletTransactionRepository;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceImplTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PayOSGatewayService payOSGatewayService;

    @Mock
    private PremiumService premiumService;

    @Mock
    private WalletService walletService;

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private EmailService emailService;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CoursePurchaseRepository coursePurchaseRepository;

    @Mock
    private EnrollmentService enrollmentService;

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private PaymentTransaction premiumPayment;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(10L)
                .email("premium@test.com")
                .build();

        premiumPayment = PaymentTransaction.builder()
                .id(99L)
                .user(user)
                .amount(java.math.BigDecimal.valueOf(79000))
                .currency("VND")
                .type(PaymentTransaction.PaymentType.PREMIUM_SUBSCRIPTION)
                .paymentMethod(PaymentTransaction.PaymentMethod.PAYOS)
                .status(PaymentTransaction.PaymentStatus.PENDING)
                .referenceId("PAYOS_123")
                .internalReference("TXN_123")
                .metadata("{\"subscriptionId\":123}")
                .description("Premium Basic")
                .build();

        when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void processPaymentCallback_cancelledPremiumPayment_rollsBackPendingSubscription() {
        when(paymentTransactionRepository.findByReferenceId("PAYOS_123"))
                .thenReturn(java.util.Optional.of(premiumPayment));

        PaymentTransaction saved = paymentService.processPaymentCallback("PAYOS_123", "CANCELLED", null);

        assertSame(premiumPayment, saved);
        verify(premiumService).rollbackPendingSubscriptionPayment(
                eq("{\"subscriptionId\":123}"),
                eq("Payment cancelled before activation"));
    }

    @Test
    void processPaymentCallback_failedPremiumPayment_rollsBackPendingSubscription() {
        when(paymentTransactionRepository.findByReferenceId("PAYOS_123"))
                .thenReturn(java.util.Optional.of(premiumPayment));

        PaymentTransaction saved = paymentService.processPaymentCallback("PAYOS_123", "FAILED", null);

        assertSame(premiumPayment, saved);
        verify(premiumService).rollbackPendingSubscriptionPayment(
                eq("{\"subscriptionId\":123}"),
                eq("Payment failed before activation"));
    }

    @Test
    void cancelPayment_pendingPremiumPayment_rollsBackPendingSubscription() {
        when(paymentTransactionRepository.findByInternalReference("TXN_123"))
                .thenReturn(java.util.Optional.of(premiumPayment));

        paymentService.cancelPayment("TXN_123", "User cancelled checkout");

        verify(premiumService).rollbackPendingSubscriptionPayment(
                eq("{\"subscriptionId\":123}"),
                eq("Payment cancelled before activation"));
    }

    @Test
    void verifyPaymentWithGateway_cancelledPremiumPayment_usesSharedRollbackFlow() {
        when(paymentTransactionRepository.findByInternalReference("TXN_123"))
                .thenReturn(java.util.Optional.of(premiumPayment));
        when(paymentTransactionRepository.findByReferenceId("PAYOS_123"))
                .thenReturn(java.util.Optional.of(premiumPayment));
        when(payOSGatewayService.verifyPayment("PAYOS_123"))
                .thenReturn(PaymentTransaction.PaymentStatus.CANCELLED);

        boolean verified = paymentService.verifyPaymentWithGateway("TXN_123");

        assertFalse(verified);
        verify(premiumService).rollbackPendingSubscriptionPayment(
                eq("{\"subscriptionId\":123}"),
                eq("Payment cancelled before activation"));
    }

    @Test
    void createPayment_rejectsPayOsForNonWalletTopup() {
        User user = premiumPayment.getUser();
        when(userRepository.findById(user.getId())).thenReturn(java.util.Optional.of(user));

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .amount(java.math.BigDecimal.valueOf(79000))
                .currency("VND")
                .type(PaymentTransaction.PaymentType.PREMIUM_SUBSCRIPTION)
                .paymentMethod(PaymentTransaction.PaymentMethod.PAYOS)
                .description("Premium Basic")
                .build();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> paymentService.createPayment(user.getId(), request));

        assertEquals(
                "PayOS chi duoc phep dung cho nap tien vao vi. Vui long nap vi va thanh toan bang so du vi.",
                ex.getMessage());
        verify(paymentTransactionRepository, never()).save(any(PaymentTransaction.class));
    }

        @Test
        void processPaymentCallback_coursePurchaseNonPayos_isIgnored() {
                User user = premiumPayment.getUser();
                PaymentTransaction coursePayment = PaymentTransaction.builder()
                                .id(100L)
                                .user(user)
                                .amount(java.math.BigDecimal.valueOf(199000))
                                .currency("VND")
                                .type(PaymentTransaction.PaymentType.COURSE_PURCHASE)
                                .paymentMethod(PaymentTransaction.PaymentMethod.MOMO)
                                .status(PaymentTransaction.PaymentStatus.PENDING)
                                .referenceId("MOMO_123")
                                .internalReference("TXN_COURSE_123")
                                .metadata("{\"courseId\":88,\"userId\":10}")
                                .description("Course purchase")
                                .build();

                when(paymentTransactionRepository.findByReferenceId("MOMO_123"))
                                .thenReturn(java.util.Optional.of(coursePayment));

                PaymentTransaction result = paymentService.processPaymentCallback("MOMO_123", "SUCCESS", null);

                assertSame(coursePayment, result);
                verify(coursePurchaseRepository, never()).save(any());
                verify(enrollmentService, never()).enrollUser(any(), any());
        }
}
