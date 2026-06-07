package com.exe.skillverse_backend.wallet_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.impl.NotificationServiceImpl;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.wallet_service.dto.response.WithdrawalRequestResponse;
import com.exe.skillverse_backend.wallet_service.entity.Wallet;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.wallet_service.entity.WithdrawalRequest;
import com.exe.skillverse_backend.wallet_service.repository.WalletRepository;
import com.exe.skillverse_backend.wallet_service.repository.WalletTransactionRepository;
import com.exe.skillverse_backend.wallet_service.repository.WithdrawalRequestRepository;
import com.exe.skillverse_backend.wallet_service.service.impl.WalletEmailServiceImpl;
import com.exe.skillverse_backend.wallet_service.service.impl.WithdrawalServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceImplTest {

    @Mock
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private WalletEmailServiceImpl walletEmailService;

    @Mock
    private NotificationServiceImpl notificationService;

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    private WithdrawalServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WithdrawalServiceImpl(
                withdrawalRequestRepository,
                walletRepository,
                transactionRepository,
                userRepository,
                walletService,
                userProfileService,
                walletEmailService,
                notificationService,
                mentorProfileRepository);
        lenient().when(withdrawalRequestRepository.save(any(WithdrawalRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(transactionRepository.save(any(WalletTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("createWithdrawalRequest should freeze funds and persist a pending request")
    void createWithdrawalRequest_ShouldFreezeFundsAndPersistPendingRequest() {
        User user = user(5L, "learner@skillverse.vn");
        Wallet wallet = activeWallet(user, new BigDecimal("2000000"), BigDecimal.ZERO, false);

        when(walletRepository.findByUserIdWithLock(user.getId())).thenReturn(Optional.of(wallet));
        when(walletService.verifyTransactionPin(user.getId(), "123456")).thenReturn(true);
        when(withdrawalRequestRepository.countByUser_IdAndStatusIn(eq(user.getId()), any())).thenReturn(0L);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        WithdrawalRequestResponse response = service.createWithdrawalRequest(
                user.getId(),
                new BigDecimal("1000000"),
                "Vietcombank",
                "1234567890",
                "Skill Learner",
                "Thu Duc",
                "Need payout",
                "notes",
                "123456",
                null,
                "127.0.0.1",
                "JUnit");

        ArgumentCaptor<WithdrawalRequest> captor = ArgumentCaptor.forClass(WithdrawalRequest.class);
        verify(withdrawalRequestRepository).save(captor.capture());
        WithdrawalRequest saved = captor.getValue();

        assertEquals(new BigDecimal("1000000"), saved.getAmount());
        assertEquals(new BigDecimal("10000"), saved.getFee());
        assertEquals(new BigDecimal("990000"), saved.getNetAmount());
        assertEquals(WithdrawalRequest.WithdrawalStatus.PENDING, saved.getStatus());
        assertEquals(3, saved.getPriority());
        assertTrue(saved.getPinVerified());
        assertEquals(new BigDecimal("1000000"), wallet.getFrozenCashBalance());
        assertEquals("PENDING", response.getStatus());
        assertTrue(response.getBankAccountNumber().endsWith("7890"));
    }

    @Test
    @DisplayName("createWithdrawalRequest should cap the fee at the configured maximum")
    void createWithdrawalRequest_ShouldCapFeeAtConfiguredMaximum() {
        User user = user(5L, "learner@skillverse.vn");
        Wallet wallet = activeWallet(user, new BigDecimal("50000000"), BigDecimal.ZERO, false);

        when(walletRepository.findByUserIdWithLock(user.getId())).thenReturn(Optional.of(wallet));
        when(walletService.verifyTransactionPin(user.getId(), "123456")).thenReturn(true);
        when(withdrawalRequestRepository.countByUser_IdAndStatusIn(eq(user.getId()), any())).thenReturn(0L);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        WithdrawalRequestResponse response = service.createWithdrawalRequest(
                user.getId(),
                new BigDecimal("10000000"),
                "Vietcombank",
                "1234567890",
                "Skill Learner",
                "Thu Duc",
                null,
                null,
                "123456",
                null,
                null,
                null);

        assertEquals(new BigDecimal("50000"), response.getFee());
        assertEquals(new BigDecimal("9950000"), response.getNetAmount());
    }

    @Test
    @DisplayName("createWithdrawalRequest should require a 2FA code when the wallet enforces it")
    void createWithdrawalRequest_ShouldRequire2FaCodeWhenWalletEnforcesIt() {
        User user = user(5L, "learner@skillverse.vn");
        Wallet wallet = activeWallet(user, new BigDecimal("2000000"), BigDecimal.ZERO, true);

        when(walletRepository.findByUserIdWithLock(user.getId())).thenReturn(Optional.of(wallet));
        when(walletService.verifyTransactionPin(user.getId(), "123456")).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.createWithdrawalRequest(
                user.getId(),
                new BigDecimal("1000000"),
                "Vietcombank",
                "1234567890",
                "Skill Learner",
                "Thu Duc",
                null,
                null,
                "123456",
                null,
                null,
                null));

        assertTrue(exception.getMessage().contains("2FA"));
    }

    @Test
    @DisplayName("createWithdrawalRequest should reject invalid transaction PINs")
    void createWithdrawalRequest_ShouldRejectInvalidPins() {
        User user = user(5L, "learner@skillverse.vn");
        Wallet wallet = activeWallet(user, new BigDecimal("2000000"), BigDecimal.ZERO, false);

        when(walletRepository.findByUserIdWithLock(user.getId())).thenReturn(Optional.of(wallet));
        when(walletService.verifyTransactionPin(user.getId(), "000000")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.createWithdrawalRequest(
                user.getId(),
                new BigDecimal("1000000"),
                "Vietcombank",
                "1234567890",
                "Skill Learner",
                "Thu Duc",
                null,
                null,
                "000000",
                null,
                null,
                null));

        verify(withdrawalRequestRepository, never()).save(any(WithdrawalRequest.class));
    }

    @Test
    @DisplayName("createWithdrawalRequest should reject users with too many pending requests")
    void createWithdrawalRequest_ShouldRejectUsersWithTooManyPendingRequests() {
        User user = user(5L, "learner@skillverse.vn");
        Wallet wallet = activeWallet(user, new BigDecimal("2000000"), BigDecimal.ZERO, false);

        when(walletRepository.findByUserIdWithLock(user.getId())).thenReturn(Optional.of(wallet));
        when(walletService.verifyTransactionPin(user.getId(), "123456")).thenReturn(true);
        when(withdrawalRequestRepository.countByUser_IdAndStatusIn(eq(user.getId()), any())).thenReturn(3L);

        assertThrows(IllegalStateException.class, () -> service.createWithdrawalRequest(
                user.getId(),
                new BigDecimal("1000000"),
                "Vietcombank",
                "1234567890",
                "Skill Learner",
                "Thu Duc",
                null,
                null,
                "123456",
                null,
                null,
                null));
    }

    @Test
    @DisplayName("approveWithdrawalRequest should complete the wallet deduction and emit approval side effects")
    void approveWithdrawalRequest_ShouldCompleteWalletDeductionAndEmitSideEffects() {
        User user = user(5L, "learner@skillverse.vn");
        User admin = user(99L, "admin@skillverse.vn");
        Wallet wallet = activeWallet(user, new BigDecimal("2000000"), new BigDecimal("500000"), false);
        WithdrawalRequest request = pendingRequest(100L, user, wallet, new BigDecimal("500000"));

        when(withdrawalRequestRepository.findById(request.getRequestId())).thenReturn(Optional.of(request));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(walletRepository.findByUserIdWithLock(user.getId())).thenReturn(Optional.of(wallet));

        WithdrawalRequestResponse response = service.approveWithdrawalRequest(request.getRequestId(), admin.getId(),
                "Approved");

        ArgumentCaptor<WalletTransaction> txCaptor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        WalletTransaction transaction = txCaptor.getValue();

        assertEquals(WalletTransaction.TransactionType.WITHDRAWAL_CASH, transaction.getTransactionType());
        assertEquals(new BigDecimal("500000"), transaction.getCashAmount());
        assertEquals(WithdrawalRequest.WithdrawalStatus.COMPLETED, request.getStatus());
        assertEquals(new BigDecimal("1500000"), wallet.getCashBalance());
        assertEquals(BigDecimal.ZERO, wallet.getFrozenCashBalance());
        assertEquals("COMPLETED", response.getStatus());
        verify(walletEmailService).sendWithdrawalApprovedEmail(user, request);
        verify(notificationService).createNotification(
                eq(user.getId()),
                anyString(),
                anyString(),
                eq(NotificationType.WITHDRAWAL_APPROVED),
                eq(request.getRequestId().toString()));
    }

    @Test
    @DisplayName("approveWithdrawalRequest should reject non-pending requests")
    void approveWithdrawalRequest_ShouldRejectNonPendingRequests() {
        User user = user(5L, "learner@skillverse.vn");
        Wallet wallet = activeWallet(user, new BigDecimal("2000000"), BigDecimal.ZERO, false);
        WithdrawalRequest request = pendingRequest(100L, user, wallet, new BigDecimal("500000"));
        request.setStatus(WithdrawalRequest.WithdrawalStatus.COMPLETED);

        when(withdrawalRequestRepository.findById(request.getRequestId())).thenReturn(Optional.of(request));

        assertThrows(IllegalStateException.class,
                () -> service.approveWithdrawalRequest(request.getRequestId(), 99L, "Approved"));
    }

    @Test
    @DisplayName("rejectWithdrawalRequest should unfreeze the balance and mark the request rejected")
    void rejectWithdrawalRequest_ShouldUnfreezeBalanceAndRejectRequest() {
        User user = user(5L, "learner@skillverse.vn");
        User admin = user(99L, "admin@skillverse.vn");
        Wallet wallet = activeWallet(user, new BigDecimal("2000000"), new BigDecimal("500000"), false);
        WithdrawalRequest request = pendingRequest(100L, user, wallet, new BigDecimal("500000"));

        when(withdrawalRequestRepository.findById(request.getRequestId())).thenReturn(Optional.of(request));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(walletRepository.findByUserIdWithLock(user.getId())).thenReturn(Optional.of(wallet));

        WithdrawalRequestResponse response = service.rejectWithdrawalRequest(request.getRequestId(), admin.getId(),
                "Invalid bank account");

        assertEquals(WithdrawalRequest.WithdrawalStatus.REJECTED, request.getStatus());
        assertEquals("Invalid bank account", request.getRejectionReason());
        assertEquals(BigDecimal.ZERO, wallet.getFrozenCashBalance());
        assertEquals("REJECTED", response.getStatus());
    }

    @Test
    @DisplayName("completeWithdrawal should only update the bank transaction reference for completed requests")
    void completeWithdrawal_ShouldOnlyUpdateBankTransactionReferenceForCompletedRequests() {
        User user = user(5L, "learner@skillverse.vn");
        Wallet wallet = activeWallet(user, new BigDecimal("1500000"), BigDecimal.ZERO, false);
        WithdrawalRequest request = pendingRequest(100L, user, wallet, new BigDecimal("500000"));
        request.setStatus(WithdrawalRequest.WithdrawalStatus.COMPLETED);

        when(withdrawalRequestRepository.findById(request.getRequestId())).thenReturn(Optional.of(request));

        WithdrawalRequestResponse response = service.completeWithdrawal(request.getRequestId(), 99L, "BANK-TX-123");

        assertEquals("BANK-TX-123", request.getBankTransactionId());
        assertEquals("BANK-TX-123", response.getBankTransactionId());
    }

    @Test
    @DisplayName("cancelWithdrawalRequest should unfreeze the amount for the owning user")
    void cancelWithdrawalRequest_ShouldUnfreezeAmountForOwningUser() {
        User user = user(5L, "learner@skillverse.vn");
        Wallet wallet = activeWallet(user, new BigDecimal("2000000"), new BigDecimal("500000"), false);
        WithdrawalRequest request = pendingRequest(100L, user, wallet, new BigDecimal("500000"));

        when(withdrawalRequestRepository.findById(request.getRequestId())).thenReturn(Optional.of(request));
        when(walletRepository.findByUserIdWithLock(user.getId())).thenReturn(Optional.of(wallet));

        WithdrawalRequestResponse response = service.cancelWithdrawalRequest(request.getRequestId(), user.getId());

        assertEquals(WithdrawalRequest.WithdrawalStatus.CANCELLED, request.getStatus());
        assertEquals(BigDecimal.ZERO, wallet.getFrozenCashBalance());
        assertEquals("CANCELLED", response.getStatus());
    }

    @Test
    @DisplayName("processExpiredRequests should mark each expired request and unfreeze its funds")
    void processExpiredRequests_ShouldMarkExpiredRequestsAndUnfreezeFunds() {
        User user = user(5L, "learner@skillverse.vn");
        Wallet wallet = activeWallet(user, new BigDecimal("2000000"), new BigDecimal("500000"), false);
        WithdrawalRequest request = pendingRequest(100L, user, wallet, new BigDecimal("500000"));
        request.setExpiresAt(LocalDateTime.now().minusHours(1));

        when(withdrawalRequestRepository.findExpiredRequests(any(LocalDateTime.class))).thenReturn(List.of(request));
        when(walletRepository.findByUserIdWithLock(user.getId())).thenReturn(Optional.of(wallet));

        service.processExpiredRequests();

        assertEquals(WithdrawalRequest.WithdrawalStatus.EXPIRED, request.getStatus());
        assertEquals(BigDecimal.ZERO, wallet.getFrozenCashBalance());
        verify(withdrawalRequestRepository).save(request);
    }

    private User user(Long id, String email) {
        return User.builder()
                .id(id)
                .email(email)
                .firstName("Skill")
                .lastName("User")
                .avatarUrl("https://cdn.skillverse/avatar.png")
                .build();
    }

    private Wallet activeWallet(User user, BigDecimal cashBalance, BigDecimal frozenBalance, boolean require2Fa) {
        return Wallet.builder()
                .walletId(10L)
                .user(user)
                .cashBalance(cashBalance)
                .frozenCashBalance(frozenBalance)
                .status(Wallet.WalletStatus.ACTIVE)
                .require2FA(require2Fa)
                .build();
    }

    private WithdrawalRequest pendingRequest(Long id, User user, Wallet wallet, BigDecimal amount) {
        return WithdrawalRequest.builder()
                .requestId(id)
                .requestCode("WD-TEST-0001")
                .user(user)
                .wallet(wallet)
                .amount(amount)
                .fee(new BigDecimal("5000"))
                .netAmount(amount.subtract(new BigDecimal("5000")))
                .status(WithdrawalRequest.WithdrawalStatus.PENDING)
                .bankName("Vietcombank")
                .bankAccountNumber("1234567890")
                .bankAccountName("Skill User")
                .priority(3)
                .pinVerified(true)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
    }
}
