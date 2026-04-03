package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.dto.purchasedto.CoursePurchaseDTO;
import com.exe.skillverse_backend.course_service.dto.purchasedto.CoursePurchaseRequestDTO;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.CoursePurchase;
import com.exe.skillverse_backend.course_service.entity.enums.CourseUpgradePolicy;
import com.exe.skillverse_backend.course_service.entity.enums.PurchaseStatus;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.CoursePurchaseRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.service.impl.CoursePurchaseServiceImpl;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import com.exe.skillverse_backend.payment_service.event.PaymentSuccessEvent;
import com.exe.skillverse_backend.payment_service.service.InvoiceService;
import com.exe.skillverse_backend.shared.service.EmailService;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.wallet_service.repository.WalletTransactionRepository;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoursePurchaseServiceImplTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CoursePurchaseRepository coursePurchaseRepository;

    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private EmailService emailService;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    private CoursePurchaseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CoursePurchaseServiceImpl(
                courseRepository,
                coursePurchaseRepository,
                courseEnrollmentRepository,
                userRepository,
                walletService,
                notificationService,
                userProfileService,
                emailService,
                invoiceService,
                walletTransactionRepository);

        lenient().when(coursePurchaseRepository.save(any(CoursePurchase.class))).thenAnswer(invocation -> {
            CoursePurchase purchase = invocation.getArgument(0);
            if (purchase.getId() == null) {
                purchase.setId(1L);
            }
            return purchase;
        });
        lenient().when(courseEnrollmentRepository.save(any(CourseEnrollment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("purchaseWithWallet should prevent duplicate paid purchases")
    void purchaseWithWallet_ShouldPreventDuplicatePaidPurchases() {
        Course course = course(10L, new BigDecimal("499000"));
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(coursePurchaseRepository.existsByUserIdAndCourseIdAndStatus(2L, course.getId(), PurchaseStatus.PAID))
                .thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.purchaseWithWallet(2L, new CoursePurchaseRequestDTO(course.getId(), null, null, null)));
    }

    @Test
    @DisplayName("purchaseWithWallet should deduct the wallet, pay the mentor and auto-enroll the learner")
    void purchaseWithWallet_ShouldDeductWalletPayMentorAndAutoEnrollLearner() {
        User learner = User.builder().id(2L).email("learner@skillverse.vn").firstName("Learner").lastName("One").build();
        Course course = course(10L, new BigDecimal("499000"));

        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(courseRepository.findByIdForEnrollmentSnapshot(course.getId())).thenReturn(Optional.of(course));
        when(coursePurchaseRepository.existsByUserIdAndCourseIdAndStatus(learner.getId(), course.getId(), PurchaseStatus.PAID))
                .thenReturn(false);
        when(userRepository.findById(learner.getId())).thenReturn(Optional.of(learner));
        when(courseEnrollmentRepository.existsByCourseIdAndUserId(course.getId(), learner.getId())).thenReturn(false);
        when(walletTransactionRepository.findByReferenceIdAndReferenceType("COURSE_" + course.getId(), "COURSE_PURCHASE"))
                .thenReturn(Optional.empty());

        CoursePurchaseDTO response = service.purchaseWithWallet(learner.getId(),
                new CoursePurchaseRequestDTO(course.getId(), null, null, null));

        assertEquals("PAID", response.getStatus());
        verify(walletService).deductCash(
                learner.getId(),
                new BigDecimal("499000"),
                "Purchase course: " + course.getTitle(),
                WalletTransaction.TransactionType.PURCHASE_COURSE,
                "COURSE_PURCHASE",
                "COURSE_" + course.getId());
        verify(walletService).payMentorForCourse(course.getAuthor().getId(), new BigDecimal("399200.00"), course.getId(), "COURSE_PURCHASE_1");

        ArgumentCaptor<CourseEnrollment> enrollmentCaptor = ArgumentCaptor.forClass(CourseEnrollment.class);
        verify(courseEnrollmentRepository).save(enrollmentCaptor.capture());
        assertEquals(learner.getId(), enrollmentCaptor.getValue().getUser().getId());
        assertEquals(course.getId(), enrollmentCaptor.getValue().getCourse().getId());
        verify(notificationService).createNotification(anyLong(), anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("handlePaymentSuccess should ignore legacy non-PayOS course purchase events")
    void handlePaymentSuccess_ShouldIgnoreLegacyNonPayOsCoursePurchaseEvents() {
        PaymentTransaction tx = PaymentTransaction.builder()
                .type(PaymentTransaction.PaymentType.COURSE_PURCHASE)
                .paymentMethod(PaymentTransaction.PaymentMethod.BANK_TRANSFER)
                .metadata("{\"courseId\":10,\"userId\":2}")
                .build();

        service.handlePaymentSuccess(new PaymentSuccessEvent(this, tx));

        verify(courseRepository, never()).findById(anyLong());
        verify(coursePurchaseRepository, never()).save(any(CoursePurchase.class));
    }

    private Course course(Long courseId, BigDecimal price) {
        User author = User.builder().id(99L).email("mentor@skillverse.vn").build();
        return Course.builder()
                .id(courseId)
                .title("System Design")
                .price(price)
                .currency("VND")
                .author(author)
                .activeRevisionId(77L)
                .upgradePolicy(CourseUpgradePolicy.MANUAL)
                .build();
    }
}
