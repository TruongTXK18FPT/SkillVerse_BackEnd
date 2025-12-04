package com.exe.skillverse_backend.wallet_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.wallet_service.entity.Wallet;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.wallet_service.entity.WithdrawalRequest;
import com.exe.skillverse_backend.wallet_service.repository.WalletRepository;
import com.exe.skillverse_backend.wallet_service.repository.WalletTransactionRepository;
import com.exe.skillverse_backend.wallet_service.repository.WithdrawalRequestRepository;
import com.exe.skillverse_backend.wallet_service.dto.response.WithdrawalRequestResponse;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Service for Withdrawal Request management
 * Handles 3-step withdrawal flow: Create → Admin Approve → Complete Transfer
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final UserProfileService userProfileService;
    private final WalletEmailService walletEmailService;
    private final NotificationService notificationService;

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

        return WithdrawalRequestResponse.fromEntity(savedRequest);
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
                approvedRequest.getRequestId().toString()
            );
        } catch (Exception e) {
            log.error("❌ Failed to send withdrawal approved email/notification for {}: {}", approvedRequest.getRequestCode(), e.getMessage());
        }

        String avatarUrl = getUserAvatarUrl(approvedRequest.getUser());
        return WithdrawalRequestResponse.fromEntityForAdmin(approvedRequest, avatarUrl);
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
                rejectedRequest.getRequestId().toString()
            );
        } catch (Exception e) {
            log.error("Failed to send rejection notification", e);
        }

        String avatarUrl = getUserAvatarUrl(rejectedRequest.getUser());
        return WithdrawalRequestResponse.fromEntityForAdmin(rejectedRequest, avatarUrl);
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

        String avatarUrl = getUserAvatarUrl(updatedRequest.getUser());
        return WithdrawalRequestResponse.fromEntityForAdmin(updatedRequest, avatarUrl);
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

        return WithdrawalRequestResponse.fromEntity(cancelledRequest);
    }

    /**
     * Get user's withdrawal requests
     */
    @Transactional(readOnly = true)
    public Page<WithdrawalRequestResponse> getMyWithdrawalRequests(Long userId, Pageable pageable) {
        Page<WithdrawalRequest> requests = withdrawalRequestRepository
                .findByUser_IdOrderByCreatedAtDesc(userId, pageable);

        return requests.map(WithdrawalRequestResponse::fromEntity);
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

        return WithdrawalRequestResponse.fromEntity(request);
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
                String avatarUrl = getUserAvatarUrl(request.getUser());
                return WithdrawalRequestResponse.fromEntityForAdmin(request, avatarUrl);
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
            String avatarUrl = getUserAvatarUrl(request.getUser());
            return WithdrawalRequestResponse.fromEntityForAdmin(request, avatarUrl);
        });
    }

    /**
     * Admin: Get withdrawal request detail
     */
    @Transactional(readOnly = true)
    public WithdrawalRequestResponse getWithdrawalRequestForAdmin(Long requestId) {
        WithdrawalRequest request = withdrawalRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Yêu cầu không tồn tại"));

        String avatarUrl = getUserAvatarUrl(request.getUser());
        return WithdrawalRequestResponse.fromEntityForAdmin(request, avatarUrl);
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

        return WithdrawalRequestResponse.fromEntity(request);
    }

    /**
     * Get withdrawal request detail for admin (full info)
     */
    @Transactional(readOnly = true)
    public WithdrawalRequestResponse getWithdrawalRequestDetailAdmin(Long requestId) {
        WithdrawalRequest request = withdrawalRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Yêu cầu không tồn tại"));

        return WithdrawalRequestResponse.fromEntityForAdmin(request);
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

        return WithdrawalRequestResponse.fromEntity(savedRequest);
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

        return fee.setScale(0, java.math.RoundingMode.UP); // Round up to nearest VNĐ
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
}
