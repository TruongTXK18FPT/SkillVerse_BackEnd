package com.exe.skillverse_backend.wallet_service.service.impl;

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
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.exe.skillverse_backend.wallet_service.service.WalletEmailService;
import com.exe.skillverse_backend.wallet_service.service.WithdrawalService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for Withdrawal Request management
 * Handles 3-step withdrawal flow: Create → Admin Approve → Complete Transfer
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalServiceImpl implements WithdrawalService {

    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final UserProfileService userProfileService;
    private final WalletEmailService walletEmailService;
    private final NotificationServiceImpl notificationService;
    private final MentorProfileRepository mentorProfileRepository;

    // Configuration
    private static final BigDecimal MIN_WITHDRAWAL = new BigDecimal("100000"); // 100K VNĐ
    private static final BigDecimal MAX_WITHDRAWAL = new BigDecimal("100000000"); // 100M VNĐ
    private static final BigDecimal WITHDRAWAL_FEE_PERCENT = new BigDecimal("0.01"); // 1%
    private static final BigDecimal MIN_FEE = new BigDecimal("5000"); // 5K VNĐ
    private static final BigDecimal MAX_FEE = new BigDecimal("50000"); // 50K VNĐ
    private static final int MAX_PENDING_REQUESTS = 3;
    private static final int REQUEST_EXPIRY_HOURS = 72; // 3 days

    /**
     * STEP 1: User tạo yêu cầu rút tiền
     */
    @Transactional
    public WithdrawalRequestResponse createWithdrawalRequest(
            Long userId,
            BigDecimal amount,
            String bankName,
            String bankAccountNumber,
            String bankAccountName,
            String bankBranch,
            String reason,
            String userNotes,
            String transactionPin,
            String twoFACode,
            String ipAddress,
            String userAgent) {
        // 1. Validate amount
        validateWithdrawalAmount(amount);

        // 2. Get wallet with lock
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("Ví không tồn tại"));

        // 3. Check wallet status
        if (wallet.getStatus() != Wallet.WalletStatus.ACTIVE) {
            throw new IllegalStateException("Ví không ở trạng thái hoạt động");
        }

        // 4. Verify transaction PIN
        if (!walletService.verifyTransactionPin(userId, transactionPin)) {
            throw new IllegalArgumentException("Mã PIN không chính xác");
        }

        // 5. Verify 2FA if enabled
        if (wallet.getRequire2FA()) {
            if (twoFACode == null || twoFACode.isEmpty()) {
                throw new IllegalArgumentException("Yêu cầu mã 2FA");
            }
            // TODO: Implement 2FA verification
            // if (!verify2FACode(userId, twoFACode)) {
            // throw new IllegalArgumentException("Mã 2FA không chính xác");
            // }
        }

        // 6. Check pending requests limit
        List<WithdrawalRequest.WithdrawalStatus> pendingStatuses = Arrays.asList(
                WithdrawalRequest.WithdrawalStatus.PENDING,
                WithdrawalRequest.WithdrawalStatus.APPROVED,
                WithdrawalRequest.WithdrawalStatus.PROCESSING);
        long pendingCount = withdrawalRequestRepository.countByUser_IdAndStatusIn(userId, pendingStatuses);
        if (pendingCount >= MAX_PENDING_REQUESTS) {
            throw new IllegalStateException("Bạn có quá nhiều yêu cầu rút tiền chưa hoàn tất");
        }

        // 7. Calculate fee and net amount
        BigDecimal fee = calculateWithdrawalFee(amount);
        BigDecimal netAmount = amount.subtract(fee);

        // 8. Check available balance (including frozen)
        if (!wallet.hasAvailableCash(amount)) {
            throw new IllegalStateException(
                    String.format("Số dư không đủ. Có sẵn: %s VNĐ, Cần: %s VNĐ",
                            wallet.getAvailableCashBalance(), amount));
        }

        // 9. Freeze the withdrawal amount
        wallet.freezeCash(amount);
        walletRepository.save(wallet);

        // 10. Create withdrawal request
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));

        WithdrawalRequest request = WithdrawalRequest.builder()
                .requestCode(WithdrawalRequest.generateRequestCode())
                .user(user)
                .wallet(wallet)
                .amount(amount)
                .fee(fee)
                .netAmount(netAmount)
                .status(WithdrawalRequest.WithdrawalStatus.PENDING)
                .bankName(bankName)
                .bankAccountNumber(bankAccountNumber)
                .bankAccountName(bankAccountName)
                .bankBranch(bankBranch)
                .reason(reason)
                .userNotes(userNotes)
                .pinVerified(true)
                .twoFAVerified(wallet.getRequire2FA())
                .priority(calculatePriority(amount))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .expiresAt(LocalDateTime.now().plusHours(REQUEST_EXPIRY_HOURS))
                .build();

        WithdrawalRequest savedRequest = withdrawalRequestRepository.save(request);

        log.info("✅ Tạo yêu cầu rút tiền: {} - User: {} - Amount: {} VNĐ",
                savedRequest.getRequestCode(), userId, amount);

        // TODO: Send email notification to user and admin

        String fullName = getUserFullName(savedRequest.getUser());
        String avatarUrl = getUserAvatarUrl(savedRequest.getUser());
        return WithdrawalRequestResponse.fromEntity(savedRequest, fullName, avatarUrl);
    }

    /**
     * STEP 2A: Admin duyệt yêu cầu và hoàn tất rút tiền
     * Khi admin approve, hệ thống sẽ:
     * 1. Trừ tiền từ cashBalance và frozenCashBalance
     * 2. Tạo transaction record
     * 3. Đổi status thành COMPLETED
     */
    @Transactional
    public WithdrawalRequestResponse approveWithdrawalRequest(
            Long requestId,
            Long adminId,
            String adminNotes) {
        WithdrawalRequest request = withdrawalRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Yêu cầu không tồn tại"));

        if (request.getStatus() != WithdrawalRequest.WithdrawalStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể duyệt yêu cầu đang chờ xử lý");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin không tồn tại"));

        // Get wallet with lock
        Wallet wallet = walletRepository.findByUserIdWithLock(request.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException("Ví không tồn tại"));

        // Block approval if wallet is lock/suspend/close
        if (wallet.getStatus() != Wallet.WalletStatus.ACTIVE) {
                throw new IllegalStateException(
                        "Không thể duyệt rút tiền: ví đang bị "
                                        + wallet.getStatus().name().toLowerCase()
                                        + ". Vui lòng unlock ví trước.");
        }

        // Complete withdrawal (deduct from balance and frozen)
        wallet.completeWithdrawal(request.getAmount());
        walletRepository.save(wallet);

        // Create transaction record
        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(wallet)
                .transactionType(WalletTransaction.TransactionType.WITHDRAWAL_CASH)
                .currencyType(WalletTransaction.CurrencyType.CASH)
                .cashAmount(request.getAmount())
                .cashBalanceAfter(wallet.getCashBalance())
                .description(String.format("Rút tiền về %s - %s",
                        request.getBankName(),
                        maskAccountNumber(request.getBankAccountNumber())))
                .notes(String.format("Net: %s VNĐ, Fee: %s VNĐ, Admin: %s %s",
                        request.getNetAmount(),
                        request.getFee(),
                        admin.getFirstName() != null ? admin.getFirstName() : "",
                        admin.getLastName() != null ? admin.getLastName() : admin.getEmail()))
                .referenceType("WITHDRAWAL")
                .referenceId(request.getRequestCode())
                .status(WalletTransaction.TransactionStatus.COMPLETED)
                .fee(request.getFee())
                .build();

        WalletTransaction savedTransaction = transactionRepository.save(transaction);

        // Approve and complete withdrawal request
        request.approve(admin, adminNotes);
        request.complete(null); // Bank transaction ID will be updated later if needed
        request.setWalletTransaction(savedTransaction);
        WithdrawalRequest approvedRequest = withdrawalRequestRepository.save(request);

        log.info("✅ Admin {} đã duyệt và hoàn tất yêu cầu rút tiền: {} - Amount: {} VNĐ",
                adminId, request.getRequestCode(), request.getAmount());

        // Send email notification to user (approved)
        try {
            walletEmailService.sendWithdrawalApprovedEmail(approvedRequest.getUser(), approvedRequest);

            // Send in-app notification
            notificationService.createNotification(
                    approvedRequest.getUser().getId(),
                    "Rút tiền thành công",
                    String.format("Yêu cầu rút %s VNĐ của bạn đã được duyệt và chuyển khoản thành công.",
                            approvedRequest.getAmount().toBigInteger().toString()),
                    NotificationType.WITHDRAWAL_APPROVED,
                    approvedRequest.getRequestId().toString());
        } catch (Exception e) {
            log.error("❌ Failed to send withdrawal approved email/notification for {}: {}",
                    approvedRequest.getRequestCode(), e.getMessage());
        }

        String fullName = getUserFullName(approvedRequest.getUser());
        String avatarUrl = getUserAvatarUrl(approvedRequest.getUser());
        return WithdrawalRequestResponse.fromEntityForAdmin(approvedRequest, fullName, avatarUrl);
    }

    /**
     * STEP 2B: Admin từ chối yêu cầu
     */
    @Transactional
    public WithdrawalRequestResponse rejectWithdrawalRequest(
            Long requestId,
            Long adminId,
            String rejectionReason) {
        if (rejectionReason == null || rejectionReason.trim().isEmpty()) {
            throw new IllegalArgumentException("Phải có lý do từ chối");
        }

        WithdrawalRequest request = withdrawalRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Yêu cầu không tồn tại"));

        if (request.getStatus() != WithdrawalRequest.WithdrawalStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể từ chối yêu cầu đang chờ xử lý");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin không tồn tại"));

        // Unfreeze the amount
        Wallet wallet = walletRepository.findByUserIdWithLock(request.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException("Ví không tồn tại"));

        wallet.unfreezeCash(request.getAmount());
        walletRepository.save(wallet);

        request.reject(admin, rejectionReason);
        WithdrawalRequest rejectedRequest = withdrawalRequestRepository.save(request);

        log.info("❌ Admin {} đã từ chối yêu cầu rút tiền: {} - Lý do: {}",
                adminId, request.getRequestCode(), rejectionReason);

        // Send notification to user
        try {
            notificationService.createNotification(
                    rejectedRequest.getUser().getId(),
                    "Yêu cầu rút tiền bị từ chối",
                    String.format("Yêu cầu rút %s VNĐ của bạn đã bị từ chối. Lý do: %s",
                            rejectedRequest.getAmount().toBigInteger().toString(), rejectionReason),
                    NotificationType.WITHDRAWAL_REJECTED,
                    rejectedRequest.getRequestId().toString());
        } catch (Exception e) {
            log.error("Failed to send rejection notification", e);
        }

        String fullName = getUserFullName(rejectedRequest.getUser());
        String avatarUrl = getUserAvatarUrl(rejectedRequest.getUser());
        return WithdrawalRequestResponse.fromEntityForAdmin(rejectedRequest, fullName, avatarUrl);
    }

    /**
     * STEP 3: Admin cập nhật mã giao dịch ngân hàng (Optional/Deprecated)
     * 
     * LƯU Ý: Endpoint này giờ chỉ dùng để cập nhật bankTransactionId
     * Số dư đã được trừ khi admin gọi /approve
     * 
     * Để tương thích ngược, method này vẫn chấp nhận COMPLETED status
     */
    @Transactional
    public WithdrawalRequestResponse completeWithdrawal(
            Long requestId,
            Long adminId,
            String bankTransactionId) {
        WithdrawalRequest request = withdrawalRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Yêu cầu không tồn tại"));

        // Only accept COMPLETED requests (balance already deducted during approval)
        if (request.getStatus() != WithdrawalRequest.WithdrawalStatus.COMPLETED) {
            throw new IllegalStateException(
                    String.format("Không thể cập nhật mã giao dịch. Yêu cầu đang ở trạng thái: %s. " +
                            "Số dư đã được trừ khi admin approve.",
                            request.getStatus().getDisplayName()));
        }

        // Update bank transaction ID if provided
        if (bankTransactionId != null && !bankTransactionId.isBlank()) {
            request.setBankTransactionId(bankTransactionId);
        }

        WithdrawalRequest updatedRequest = withdrawalRequestRepository.save(request);

        log.info("✅ Admin {} đã cập nhật mã giao dịch ngân hàng cho withdrawal {}: {}",
                adminId, request.getRequestCode(), bankTransactionId);

        String fullName = getUserFullName(updatedRequest.getUser());
        String avatarUrl = getUserAvatarUrl(updatedRequest.getUser());
        return WithdrawalRequestResponse.fromEntityForAdmin(updatedRequest, fullName, avatarUrl);
    }

    /**
     * User hủy yêu cầu (chỉ khi còn PENDING)
     * Lưu ý: Sau khi admin approve, request sẽ chuyển thành COMPLETED ngay lập tức
     * nên user không thể hủy được nữa
     */
    @Transactional
    public WithdrawalRequestResponse cancelWithdrawalRequest(Long requestId, Long userId) {
        WithdrawalRequest request = withdrawalRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Yêu cầu không tồn tại"));

        // Check ownership
        if (!request.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Bạn không có quyền hủy yêu cầu này");
        }

        // Can only cancel PENDING requests
        if (request.getStatus() != WithdrawalRequest.WithdrawalStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể hủy yêu cầu đang chờ duyệt. Trạng thái hiện tại: " +
                    request.getStatus().getDisplayName());
        }

        // Unfreeze the amount
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("Ví không tồn tại"));

        wallet.unfreezeCash(request.getAmount());
        walletRepository.save(wallet);

        request.cancel();
        WithdrawalRequest cancelledRequest = withdrawalRequestRepository.save(request);

        log.info("🚫 User {} đã hủy yêu cầu rút tiền: {}", userId, request.getRequestCode());

        String fullName = getUserFullName(cancelledRequest.getUser());
        String avatarUrl = getUserAvatarUrl(cancelledRequest.getUser());
        return WithdrawalRequestResponse.fromEntity(cancelledRequest, fullName, avatarUrl);
    }

    /**
     * Get user's withdrawal requests
     */
    @Transactional(readOnly = true)
    public Page<WithdrawalRequestResponse> getMyWithdrawalRequests(Long userId, Pageable pageable) {
        Page<WithdrawalRequest> requests = withdrawalRequestRepository
                .findByUser_IdOrderByCreatedAtDesc(userId, pageable);

        return requests.map(request -> {
            String fullName = getUserFullName(request.getUser());
            String avatarUrl = getUserAvatarUrl(request.getUser());
            return WithdrawalRequestResponse.fromEntity(request, fullName, avatarUrl);
        });
    }

    /**
     * Get specific withdrawal request
     */
    @Transactional(readOnly = true)
    public WithdrawalRequestResponse getWithdrawalRequest(Long requestId, Long userId) {
        WithdrawalRequest request = withdrawalRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Yêu cầu không tồn tại"));

        // Check ownership
        if (!request.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Bạn không có quyền xem yêu cầu này");
        }

        String fullName = getUserFullName(request.getUser());
        String avatarUrl = getUserAvatarUrl(request.getUser());
        return WithdrawalRequestResponse.fromEntity(request, fullName, avatarUrl);
    }

    /**
     * Admin: Get all withdrawal requests
     */
    @Transactional(readOnly = true)
    public Page<WithdrawalRequestResponse> getAllWithdrawalRequests(
            WithdrawalRequest.WithdrawalStatus status,
            Pageable pageable) {
        Page<WithdrawalRequest> requests;

        if (status != null) {
            requests = withdrawalRequestRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else {
            // Use custom query with EntityGraph for findAll
            requests = withdrawalRequestRepository.findAll(pageable);
        }

        log.info("📊 Found {} withdrawal requests (status: {})", requests.getTotalElements(), status);
        return requests.map(request -> {
            try {
                String fullName = getUserFullName(request.getUser());
                String avatarUrl = getUserAvatarUrl(request.getUser());
                return WithdrawalRequestResponse.fromEntityForAdmin(request, fullName, avatarUrl);
            } catch (Exception e) {
                log.error("❌ Error mapping withdrawal request {}: {}", request.getRequestId(), e.getMessage());
                throw e;
            }
        });
    }

    /**
     * Admin: Get pending requests (priority queue)
     */
    @Transactional(readOnly = true)
    public Page<WithdrawalRequestResponse> getPendingRequests(Pageable pageable) {
        Page<WithdrawalRequest> requests = withdrawalRequestRepository
                .findAllPendingRequests(pageable);

        return requests.map(request -> {
            String fullName = getUserFullName(request.getUser());
            String avatarUrl = getUserAvatarUrl(request.getUser());
            return WithdrawalRequestResponse.fromEntityForAdmin(request, fullName, avatarUrl);
        });
    }

    /**
     * Admin: Get withdrawal request detail
     */
    @Transactional(readOnly = true)
    public WithdrawalRequestResponse getWithdrawalRequestForAdmin(Long requestId) {
        WithdrawalRequest request = withdrawalRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Yêu cầu không tồn tại"));

        String fullName = getUserFullName(request.getUser());
        String avatarUrl = getUserAvatarUrl(request.getUser());
        return WithdrawalRequestResponse.fromEntityForAdmin(request, fullName, avatarUrl);
    }

    /**
     * Check for expired requests and auto-cancel
     */
    @Transactional
    public void processExpiredRequests() {
        List<WithdrawalRequest> expiredRequests = withdrawalRequestRepository
                .findExpiredRequests(LocalDateTime.now());

        for (WithdrawalRequest request : expiredRequests) {
            try {
                // Unfreeze amount
                Wallet wallet = walletRepository.findByUserIdWithLock(request.getUser().getId())
                        .orElse(null);

                if (wallet != null) {
                    wallet.unfreezeCash(request.getAmount());
                    walletRepository.save(wallet);
                }

                // Mark as expired
                request.setStatus(WithdrawalRequest.WithdrawalStatus.EXPIRED);
                withdrawalRequestRepository.save(request);

                log.info("⏰ Đã tự động hủy yêu cầu rút tiền hết hạn: {}", request.getRequestCode());

                // TODO: Send email notification
            } catch (Exception e) {
                log.error("Lỗi khi xử lý yêu cầu hết hạn {}: {}",
                        request.getRequestCode(), e.getMessage());
            }
        }
    }

    /**
     * Get withdrawal request detail (for user)
     */
    @Transactional(readOnly = true)
    public WithdrawalRequestResponse getWithdrawalRequestDetail(Long userId, Long requestId) {
        WithdrawalRequest request = withdrawalRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Yêu cầu không tồn tại"));

        // Verify ownership
        if (!request.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Không có quyền truy cập yêu cầu này");
        }

        String fullName = getUserFullName(request.getUser());
        String avatarUrl = getUserAvatarUrl(request.getUser());
        return WithdrawalRequestResponse.fromEntity(request, fullName, avatarUrl);
    }

    /**
     * Get withdrawal request detail for admin (full info)
     */
    @Transactional(readOnly = true)
    public WithdrawalRequestResponse getWithdrawalRequestDetailAdmin(Long requestId) {
        WithdrawalRequest request = withdrawalRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Yêu cầu không tồn tại"));

        String fullName = getUserFullName(request.getUser());
        String avatarUrl = getUserAvatarUrl(request.getUser());
        return WithdrawalRequestResponse.fromEntityForAdmin(request, fullName, avatarUrl);
    }

    /**
     * Cancel withdrawal request with optional reason (overloaded)
     * Chỉ có thể hủy request đang PENDING
     */
    @Transactional
    public WithdrawalRequestResponse cancelWithdrawalRequest(
            Long requestId,
            Long userId,
            String reason) {
        WithdrawalRequest request = withdrawalRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Yêu cầu không tồn tại"));

        // Verify ownership
        if (!request.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Không có quyền hủy yêu cầu này");
        }

        // Can only cancel PENDING requests (APPROVED is now immediately COMPLETED)
        if (request.getStatus() != WithdrawalRequest.WithdrawalStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể hủy yêu cầu đang chờ duyệt. Trạng thái hiện tại: " +
                    request.getStatus().getDisplayName());
        }

        // Unfreeze cash
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("Ví không tồn tại"));

        wallet.unfreezeCash(request.getAmount());
        walletRepository.save(wallet);

        // Cancel request
        request.cancel();
        if (reason != null) {
            request.setRejectionReason(reason);
        }
        WithdrawalRequest savedRequest = withdrawalRequestRepository.save(request);

        log.info("User {} đã hủy withdrawal request {} - Lý do: {}", userId, requestId, reason);

        String fullName = getUserFullName(savedRequest.getUser());
        String avatarUrl = getUserAvatarUrl(savedRequest.getUser());
        return WithdrawalRequestResponse.fromEntity(savedRequest, fullName, avatarUrl);
    }

    // ==================== HELPER METHODS ====================

    private void validateWithdrawalAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền rút phải lớn hơn 0");
        }

        if (amount.compareTo(MIN_WITHDRAWAL) < 0) {
            throw new IllegalArgumentException(
                    String.format("Số tiền rút tối thiểu là %s VNĐ", MIN_WITHDRAWAL));
        }

        if (amount.compareTo(MAX_WITHDRAWAL) > 0) {
            throw new IllegalArgumentException(
                    String.format("Số tiền rút tối đa là %s VNĐ", MAX_WITHDRAWAL));
        }
    }

    private BigDecimal calculateWithdrawalFee(BigDecimal amount) {
        BigDecimal fee = amount.multiply(WITHDRAWAL_FEE_PERCENT);

        if (fee.compareTo(MIN_FEE) < 0) {
            fee = MIN_FEE;
        } else if (fee.compareTo(MAX_FEE) > 0) {
            fee = MAX_FEE;
        }

        return fee.setScale(0, RoundingMode.UP); // Round up to nearest VNĐ
    }

    private Integer calculatePriority(BigDecimal amount) {
        // Priority 1-5 based on amount (higher amount = higher priority)
        if (amount.compareTo(new BigDecimal("10000000")) >= 0)
            return 1; // >= 10M
        if (amount.compareTo(new BigDecimal("5000000")) >= 0)
            return 2; // >= 5M
        if (amount.compareTo(new BigDecimal("1000000")) >= 0)
            return 3; // >= 1M
        if (amount.compareTo(new BigDecimal("500000")) >= 0)
            return 4; // >= 500K
        return 5; // < 500K
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return accountNumber;
        }
        int visibleDigits = 4;
        int maskedLength = accountNumber.length() - visibleDigits;
        return "*".repeat(maskedLength) + accountNumber.substring(maskedLength);
    }

    /**
     * Get user's avatar URL from their profile
     */
    private String getUserAvatarUrl(User user) {
        try {
            if (user.getAvatarUrl() != null) {
                return user.getAvatarUrl();
            }

            // Try to get from UserProfile if exists
            if (userProfileService.hasProfile(user.getId())) {
                var profile = userProfileService.getProfile(user.getId());
                if (profile.getAvatarMediaUrl() != null) {
                    return profile.getAvatarMediaUrl();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get avatar URL for user {}: {}", user.getId(), e.getMessage());
        }
        return null;
    }

    /**
     * Get user's full name from their profile based on availability
     */
    private String getUserFullName(User user) {
        if (user == null) {
            return "Unknown User";
        }
        try {
            // 1. Try to construct from User entity (firstName + lastName) if present
            String firstName = user.getFirstName();
            String lastName = user.getLastName();
            if ((firstName != null && !firstName.trim().isEmpty()) || 
                (lastName != null && !lastName.trim().isEmpty())) {
                String first = firstName != null ? firstName.trim() : "";
                String last = lastName != null ? lastName.trim() : "";
                String name = (first + " " + last).trim();
                if (!name.isEmpty()) {
                    return name;
                }
            }

            // 2. Try UserProfile
            if (userProfileService.hasProfile(user.getId())) {
                var profile = userProfileService.getProfile(user.getId());
                if (profile.getFullName() != null && !profile.getFullName().trim().isEmpty()) {
                    return profile.getFullName().trim();
                }
            }

            // 3. Try MentorProfile
            var mentorProfile = mentorProfileRepository.findByUserId(user.getId());
            if (mentorProfile.isPresent()) {
                var profile = mentorProfile.get();
                if (profile.getFullName() != null && !profile.getFullName().trim().isEmpty()) {
                    return profile.getFullName().trim();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get full name for user {}: {}", user.getId(), e.getMessage());
        }

        // Fallback to email prefix or "Unknown User"
        if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
            return user.getEmail().trim();
        }
        return "Unknown User";
    }

    // ========== Ban Cascade Methods ==========

    /**
     * Cancel all PENDING withdrawals for user + unfreeze money back to wallet.
     * Used when: ban mentor → cancel all pending withdrawals.
     */
    @Override
    @Transactional
    public int cancelPendingByUserId(Long userId, String reason) {
        List<WithdrawalRequest> pending = withdrawalRequestRepository
                        .findByWallet_User_IdAndStatus(userId, WithdrawalRequest.WithdrawalStatus.PENDING);
        int count = 0;
        for (WithdrawalRequest req : pending) {
                // Unfreeze: return money to available balance
                Wallet wallet = walletRepository.findByUserIdWithLock(userId).orElse(null);
                if (wallet != null) {
                        wallet.unfreezeCash(req.getAmount());
                        walletRepository.save(wallet);
                }
                req.cancel();
                req.setReason(reason);
                withdrawalRequestRepository.save(req);
                log.info("Cancelled pending withdrawal {} for user {}", req.getRequestCode(), userId);
                count++;
        }
        log.info("Cancelled {} pending withdrawals for user {}", count, userId);
        return count;
    }
}
