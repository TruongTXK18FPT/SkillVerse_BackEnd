package com.exe.skillverse_backend.wallet_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.CoursePurchaseRepository;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.payment_service.service.PaymentService;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import com.exe.skillverse_backend.wallet_service.entity.Wallet;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.wallet_service.repository.WalletRepository;
import com.exe.skillverse_backend.wallet_service.repository.WalletTransactionRepository;
import com.exe.skillverse_backend.wallet_service.service.impl.WalletEmailServiceImpl;
import com.exe.skillverse_backend.wallet_service.service.impl.WalletServiceImpl;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private WalletEmailServiceImpl walletEmailService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ObjectProvider<PaymentService> paymentServiceProvider;

    @Mock
    private CourseEnrollmentRepository enrollmentRepository;

    @Mock
    private CoursePurchaseRepository coursePurchaseRepository;

    private WalletServiceImpl walletService;

    @BeforeEach
    void setUp() {
        walletService = new WalletServiceImpl(
                walletRepository,
                transactionRepository,
                userRepository,
                userProfileRepository,
                passwordEncoder,
                walletEmailService,
                notificationService,
                paymentServiceProvider,
                enrollmentRepository,
                coursePurchaseRepository);
    }

    @Test
    void deductCash_usesLockedWalletLookupAndPersistsTransaction() {
        Wallet wallet = Wallet.builder()
                .walletId(1L)
                .user(User.builder().id(100L).build())
                .cashBalance(new BigDecimal("300000"))
                .frozenCashBalance(new BigDecimal("20000"))
                .build();

        when(walletRepository.findByUserIdWithLock(100L)).thenReturn(Optional.of(wallet));
        when(transactionRepository.save(any(WalletTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WalletTransaction transaction = walletService.deductCash(
                100L,
                new BigDecimal("79000"),
                "Mua premium",
                "PREMIUM_SUBSCRIPTION",
                "SUB_1");

        verify(walletRepository).findByUserIdWithLock(100L);
        verify(walletRepository, never()).findByUser_Id(any());
        verify(walletRepository).save(wallet);
        verify(transactionRepository).save(any(WalletTransaction.class));
        assertEquals(new BigDecimal("221000"), wallet.getCashBalance());
        assertEquals("PREMIUM_SUBSCRIPTION", transaction.getReferenceType());
        assertEquals("SUB_1", transaction.getReferenceId());
    }

    @Test
    void deductCash_insufficientAvailableBalance_throwsAndDoesNotPersist() {
        Wallet wallet = Wallet.builder()
                .walletId(2L)
                .user(User.builder().id(101L).build())
                .cashBalance(new BigDecimal("100000"))
                .frozenCashBalance(new BigDecimal("40000"))
                .build();

        when(walletRepository.findByUserIdWithLock(101L)).thenReturn(Optional.of(wallet));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> walletService.deductCash(
                        101L,
                        new BigDecimal("70000"),
                        "Mua khoa hoc",
                        "COURSE_PURCHASE",
                        "COURSE_10"));

        verify(walletRepository).findByUserIdWithLock(101L);
        verify(walletRepository, never()).save(any(Wallet.class));
        verify(transactionRepository, never()).save(any(WalletTransaction.class));
        assertEquals(
                "Số dư khả dụng không đủ. Khả dụng: 60000 VND, Cần: 70000 VND",
                error.getMessage());
    }

    @Test
    void deductCash_withExplicitType_persistsProvidedTransactionType() {
        Wallet wallet = Wallet.builder()
                .walletId(3L)
                .user(User.builder().id(102L).build())
                .cashBalance(new BigDecimal("250000"))
                .frozenCashBalance(BigDecimal.ZERO)
                .build();

        when(walletRepository.findByUserIdWithLock(102L)).thenReturn(Optional.of(wallet));
        when(transactionRepository.save(any(WalletTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WalletTransaction transaction = walletService.deductCash(
                102L,
                new BigDecimal("50000"),
                "Mua ve seminar",
                WalletTransaction.TransactionType.SEMINAR_PURCHASE,
                "SEMINAR_PURCHASE",
                "SEMINAR_8");

        assertEquals(WalletTransaction.TransactionType.SEMINAR_PURCHASE, transaction.getTransactionType());
        assertEquals(new BigDecimal("200000"), wallet.getCashBalance());
    }

    @Test
    void processRefund_usesLockedWalletLookupAndIdempotency() {
        Wallet wallet = Wallet.builder()
                .walletId(4L)
                .user(User.builder().id(103L).build())
                .cashBalance(new BigDecimal("100000"))
                .frozenCashBalance(BigDecimal.ZERO)
                .build();

        WalletTransaction existing = WalletTransaction.builder()
                .transactionId(9001L)
                .referenceType("BOOKING_REFUND")
                .referenceId("BOOKING_15")
                .status(WalletTransaction.TransactionStatus.COMPLETED)
                .build();

        when(transactionRepository.existsByReferenceIdAndReferenceTypeAndStatus(
                "BOOKING_15", "BOOKING_REFUND", WalletTransaction.TransactionStatus.COMPLETED))
                .thenReturn(true);
        when(transactionRepository.findByReferenceIdAndReferenceType("BOOKING_15", "BOOKING_REFUND"))
                .thenReturn(Optional.of(existing));

        WalletTransaction result = walletService.processRefund(
                103L,
                new BigDecimal("30000"),
                "Hoan tien",
                "BOOKING_REFUND",
                "BOOKING_15");

        assertSame(existing, result);
        verify(walletRepository, never()).findByUser_Id(any());
        verify(walletRepository, never()).findByUserIdWithLock(103L);
        verify(walletRepository, never()).save(any(Wallet.class));
        verify(transactionRepository, never()).save(any(WalletTransaction.class));

        when(transactionRepository.existsByReferenceIdAndReferenceTypeAndStatus(
                "BOOKING_16", "BOOKING_REFUND", WalletTransaction.TransactionStatus.COMPLETED))
                .thenReturn(false);
        when(walletRepository.findByUserIdWithLock(103L)).thenReturn(Optional.of(wallet));
        when(transactionRepository.save(any(WalletTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WalletTransaction created = walletService.processRefund(
                103L,
                new BigDecimal("30000"),
                "Hoan tien",
                "BOOKING_REFUND",
                "BOOKING_16");

        verify(walletRepository).findByUserIdWithLock(103L);
        verify(walletRepository).save(wallet);
        assertEquals("BOOKING_REFUND", created.getReferenceType());
        assertEquals("BOOKING_16", created.getReferenceId());
    }

    @Test
    void payMentorForCourse_idempotentByDeterministicReference() {
        Wallet wallet = Wallet.builder()
                .walletId(5L)
                .user(User.builder().id(200L).build())
                .cashBalance(new BigDecimal("50000"))
                .frozenCashBalance(BigDecimal.ZERO)
                .build();

        WalletTransaction existing = WalletTransaction.builder()
                .transactionId(777L)
                .referenceType("COURSE_PAYOUT")
                .referenceId("COURSE_PURCHASE_321")
                .status(WalletTransaction.TransactionStatus.COMPLETED)
                .build();

        when(transactionRepository.existsByReferenceIdAndReferenceTypeAndStatus(
                "COURSE_PURCHASE_321", "COURSE_PAYOUT", WalletTransaction.TransactionStatus.COMPLETED))
                .thenReturn(true);
        when(transactionRepository.findByReferenceIdAndReferenceType("COURSE_PURCHASE_321", "COURSE_PAYOUT"))
                .thenReturn(Optional.of(existing));

        WalletTransaction duplicate = walletService.payMentorForCourse(
                200L,
                new BigDecimal("40000"),
                10L,
                "COURSE_PURCHASE_321");

        assertSame(existing, duplicate);
        verify(walletRepository, never()).findByUserIdWithLock(200L);

        when(transactionRepository.existsByReferenceIdAndReferenceTypeAndStatus(
                eq("COURSE_PURCHASE_322"), eq("COURSE_PAYOUT"), eq(WalletTransaction.TransactionStatus.COMPLETED)))
                .thenReturn(false);
        when(walletRepository.findByUserIdWithLock(200L)).thenReturn(Optional.of(wallet));

        Wallet adminWallet = Wallet.builder()
                .walletId(99L)
                .user(User.builder().id(999L).email("exeadmin@gmail.com").build())
                .cashBalance(BigDecimal.ZERO)
                .build();
        when(walletRepository.findByUser_Email("exeadmin@gmail.com")).thenReturn(Optional.of(adminWallet));

        when(transactionRepository.save(any(WalletTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WalletTransaction created = walletService.payMentorForCourse(
                200L,
                new BigDecimal("40000"),
                10L,
                "COURSE_PURCHASE_322");

        verify(walletRepository).findByUserIdWithLock(200L);
        verify(walletRepository).save(wallet);
        assertEquals("COURSE_PURCHASE_322", created.getReferenceId());
        assertEquals("COURSE_PAYOUT", created.getReferenceType());
    }
}
