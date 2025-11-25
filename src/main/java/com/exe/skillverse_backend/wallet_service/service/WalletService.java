package com.exe.skillverse_backend.wallet_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.payment_service.dto.response.CreatePaymentResponse;
import com.exe.skillverse_backend.wallet_service.dto.response.WalletResponse;
import com.exe.skillverse_backend.wallet_service.dto.response.WalletTransactionResponse;
import com.exe.skillverse_backend.wallet_service.entity.Wallet;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.wallet_service.repository.WalletRepository;
import com.exe.skillverse_backend.wallet_service.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Service for Wallet management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

        private final WalletRepository walletRepository;
        private final WalletTransactionRepository transactionRepository;
        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;

        /**
         * Tạo ví mới cho user (tự động khi register)
         */
        @Transactional
        public Wallet createWallet(Long userId) {
                // Check if wallet already exists
                if (walletRepository.existsByUser_Id(userId)) {
                        throw new IllegalStateException("Ví đã tồn tại cho user này");
                }

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));

                Wallet wallet = Wallet.builder()
                                .user(user)
                                .status(Wallet.WalletStatus.ACTIVE)
                                .build();

                Wallet savedWallet = walletRepository.save(wallet);
                log.info("Đã tạo ví mới cho user {}", userId);

                return savedWallet;
        }

        /**
         * Lấy thông tin ví của user
         */
        @Transactional(readOnly = true)
        public WalletResponse getWalletByUserId(Long userId) {
                Wallet wallet = walletRepository.findByUser_Id(userId)
                                .orElseThrow(() -> new IllegalArgumentException("Ví không tồn tại"));

                return WalletResponse.fromEntity(wallet);
        }

        /**
         * Lấy hoặc tạo ví (nếu chưa có)
         */
        @Transactional
        public Wallet getOrCreateWallet(Long userId) {
                return walletRepository.findByUser_Id(userId)
                                .orElseGet(() -> createWallet(userId));
        }

        /**
         * Nạp tiền vào ví Cash (từ PayOS callback)
         */
        @Transactional
        public WalletTransaction depositCash(
                        Long userId,
                        BigDecimal amount,
                        String paymentReferenceId,
                        String description) {
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0");
                }

                // Idempotency check: prevent duplicate deposits for same payment reference
                // This prevents duplicate cash deposits when both webhook + verification
                // endpoint are called
                if (paymentReferenceId != null && !paymentReferenceId.isEmpty()) {
                        boolean alreadyProcessed = transactionRepository.existsByReferenceIdAndReferenceTypeAndStatus(
                                        paymentReferenceId,
                                        "PAYMENT",
                                        WalletTransaction.TransactionStatus.COMPLETED);

                        if (alreadyProcessed) {
                                log.warn("⚠️ Deposit already processed for payment reference: {}. Ignoring duplicate.",
                                                paymentReferenceId);
                                // Return the existing transaction instead of creating duplicate
                                return transactionRepository
                                                .findByReferenceIdAndReferenceType(paymentReferenceId, "PAYMENT")
                                                .orElse(null);
                        }
                }

                // Lock wallet to prevent race conditions
                Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                                .orElseThrow(() -> new IllegalArgumentException("Ví không tồn tại"));

                // Deposit to wallet
                wallet.depositCash(amount);
                walletRepository.save(wallet);

                // Create transaction record
                WalletTransaction transaction = WalletTransaction.builder()
                                .wallet(wallet)
                                .transactionType(WalletTransaction.TransactionType.DEPOSIT_CASH)
                                .currencyType(WalletTransaction.CurrencyType.CASH)
                                .cashAmount(amount)
                                .cashBalanceAfter(wallet.getCashBalance())
                                .description(description != null ? description : "Nạp tiền vào ví")
                                .referenceType("PAYMENT")
                                .referenceId(paymentReferenceId)
                                .status(WalletTransaction.TransactionStatus.COMPLETED)
                                .build();

                WalletTransaction savedTransaction = transactionRepository.save(transaction);
                log.info("✅ Đã nạp {} VNĐ vào ví user {} (Payment: {})", amount, userId, paymentReferenceId);

                return savedTransaction;
        }

        /**
         * Cộng Coin vào ví (khi mua hoặc kiếm được)
         */
        @Transactional
        public WalletTransaction addCoins(
                        Long userId,
                        Long coinAmount,
                        WalletTransaction.TransactionType transactionType,
                        String description,
                        String referenceType,
                        String referenceId) {
                if (coinAmount <= 0) {
                        throw new IllegalArgumentException("Số Coin phải lớn hơn 0");
                }

                Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                                .orElseThrow(() -> new IllegalArgumentException("Ví không tồn tại"));

                // Add coins based on transaction type
                if (transactionType == WalletTransaction.TransactionType.EARN_COINS ||
                                transactionType == WalletTransaction.TransactionType.BONUS_COINS ||
                                transactionType == WalletTransaction.TransactionType.REWARD_ACHIEVEMENT ||
                                transactionType == WalletTransaction.TransactionType.DAILY_LOGIN_BONUS) {
                        wallet.earnCoins(coinAmount);
                } else {
                        wallet.addCoins(coinAmount);
                }

                walletRepository.save(wallet);

                // Create transaction record
                WalletTransaction transaction = WalletTransaction.builder()
                                .wallet(wallet)
                                .transactionType(transactionType)
                                .currencyType(WalletTransaction.CurrencyType.COIN)
                                .coinAmount(coinAmount)
                                .coinBalanceAfter(wallet.getCoinBalance())
                                .description(description)
                                .referenceType(referenceType)
                                .referenceId(referenceId)
                                .status(WalletTransaction.TransactionStatus.COMPLETED)
                                .build();

                WalletTransaction savedTransaction = transactionRepository.save(transaction);
                log.info("Đã cộng {} Coins vào ví user {} (type: {})", coinAmount, userId, transactionType);

                return savedTransaction;
        }

        /**
         * Trừ Coin từ ví (khi chi tiêu)
         */
        @Transactional
        public WalletTransaction deductCoins(
                        Long userId,
                        Long coinAmount,
                        WalletTransaction.TransactionType transactionType,
                        String description,
                        String referenceType,
                        String referenceId) {
                if (coinAmount <= 0) {
                        throw new IllegalArgumentException("Số Coin phải lớn hơn 0");
                }

                Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                                .orElseThrow(() -> new IllegalArgumentException("Ví không tồn tại"));

                // Check sufficient balance
                if (wallet.getCoinBalance() < coinAmount) {
                        throw new IllegalStateException(
                                        String.format("Số dư Coin không đủ. Có: %d, Cần: %d", wallet.getCoinBalance(),
                                                        coinAmount));
                }

                wallet.deductCoins(coinAmount);
                walletRepository.save(wallet);

                // Create transaction record
                WalletTransaction transaction = WalletTransaction.builder()
                                .wallet(wallet)
                                .transactionType(transactionType)
                                .currencyType(WalletTransaction.CurrencyType.COIN)
                                .coinAmount(coinAmount)
                                .coinBalanceAfter(wallet.getCoinBalance())
                                .description(description)
                                .referenceType(referenceType)
                                .referenceId(referenceId)
                                .status(WalletTransaction.TransactionStatus.COMPLETED)
                                .build();

                WalletTransaction savedTransaction = transactionRepository.save(transaction);
                log.info("Đã trừ {} Coins từ ví user {} (type: {})", coinAmount, userId, transactionType);

                return savedTransaction;
        }

        /**
         * Thiết lập/Cập nhật mã PIN giao dịch
         */
        @Transactional
        public void setTransactionPin(Long userId, String pin) {
                if (pin == null || !pin.matches("^[0-9]{6}$")) {
                        throw new IllegalArgumentException("Mã PIN phải là 6 chữ số");
                }

                Wallet wallet = walletRepository.findByUser_Id(userId)
                                .orElseThrow(() -> new IllegalArgumentException("Ví không tồn tại"));

                String encodedPin = passwordEncoder.encode(pin);
                wallet.setTransactionPin(encodedPin);
                walletRepository.save(wallet);

                log.info("Đã thiết lập mã PIN cho ví user {}", userId);
        }

        /**
         * Xác thực mã PIN giao dịch
         */
        @Transactional(readOnly = true)
        public boolean verifyTransactionPin(Long userId, String pin) {
                Wallet wallet = walletRepository.findByUser_Id(userId)
                                .orElseThrow(() -> new IllegalArgumentException("Ví không tồn tại"));

                if (wallet.getTransactionPin() == null) {
                        throw new IllegalStateException("Chưa thiết lập mã PIN");
                }

                return passwordEncoder.matches(pin, wallet.getTransactionPin());
        }

        /**
         * Cập nhật thông tin ngân hàng
         */
        @Transactional
        public void updateBankAccount(
                        Long userId,
                        String bankName,
                        String bankAccountNumber,
                        String bankAccountName) {
                Wallet wallet = walletRepository.findByUser_Id(userId)
                                .orElseThrow(() -> new IllegalArgumentException("Ví không tồn tại"));

                wallet.setBankName(bankName);
                wallet.setBankAccountNumber(bankAccountNumber);
                wallet.setBankAccountName(bankAccountName);

                walletRepository.save(wallet);
                log.info("Đã cập nhật thông tin ngân hàng cho ví user {}", userId);
        }

        /**
         * Bật/Tắt yêu cầu 2FA cho giao dịch
         */
        @Transactional
        public void toggle2FA(Long userId, boolean enabled) {
                Wallet wallet = walletRepository.findByUser_Id(userId)
                                .orElseThrow(() -> new IllegalArgumentException("Ví không tồn tại"));

                wallet.setRequire2FA(enabled);
                walletRepository.save(wallet);

                log.info("Đã {} 2FA cho ví user {}", enabled ? "bật" : "tắt", userId);
        }

        /**
         * Get transaction history (for controller)
         */
        @Transactional(readOnly = true)
        public Page<WalletTransactionResponse> getTransactionHistory(
                        Long userId,
                        Pageable pageable) {
                Wallet wallet = walletRepository.findByUser_Id(userId)
                                .orElseThrow(() -> new IllegalArgumentException("Ví không tồn tại"));

                Page<WalletTransaction> transactions = transactionRepository.findByWalletOrderByCreatedAtDesc(wallet,
                                pageable);

                return transactions.map(WalletTransactionResponse::fromEntity);
        }

        /**
         * Get transaction detail
         */
        @Transactional(readOnly = true)
        public WalletTransactionResponse getTransactionDetail(
                        Long userId,
                        Long transactionId) {
                WalletTransaction transaction = transactionRepository.findById(transactionId)
                                .orElseThrow(() -> new IllegalArgumentException("Giao dịch không tồn tại"));

                if (!transaction.getWallet().getUser().getId().equals(userId)) {
                        throw new IllegalArgumentException("Không có quyền truy cập giao dịch này");
                }

                return WalletTransactionResponse.fromEntity(transaction);
        }

        /**
         * Get wallet statistics
         */
        @Transactional(readOnly = true)
        public Map<String, Object> getWalletStatistics(Long userId) {
                Wallet wallet = walletRepository.findByUser_Id(userId)
                                .orElseThrow(() -> new IllegalArgumentException("Ví không tồn tại"));

                return Map.of(
                                "totalDeposited", wallet.getTotalDeposited(),
                                "totalWithdrawn", wallet.getTotalWithdrawn(),
                                "totalCoinsEarned", wallet.getTotalCoinsEarned(),
                                "totalCoinsSpent", wallet.getTotalCoinsSpent(),
                                "currentCashBalance", wallet.getCashBalance(),
                                "currentCoinBalance", wallet.getCoinBalance(),
                                "availableCashBalance", wallet.getAvailableCashBalance(),
                                "frozenCashBalance", wallet.getFrozenCashBalance());
        }

        /**
         * Create deposit payment (for controller)
         */
        @Transactional
        public CreatePaymentResponse createDepositPayment(
                        Long userId,
                        BigDecimal amount,
                        String paymentMethod,
                        String returnUrl,
                        String cancelUrl) {
                // This would integrate with PaymentService
                // For now, throw not implemented
                throw new UnsupportedOperationException("Deposit payment integration not yet implemented");
        }

        /**
         * Get global wallet statistics (admin)
         */
        @Transactional(readOnly = true)
        public Map<String, Object> getGlobalStatistics() {
                List<Wallet> allWallets = walletRepository.findAll();

                BigDecimal totalCashInSystem = allWallets.stream()
                                .map(Wallet::getCashBalance)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                Long totalCoinsInSystem = allWallets.stream()
                                .map(Wallet::getCoinBalance)
                                .reduce(0L, Long::sum);

                BigDecimal totalFrozenCash = allWallets.stream()
                                .map(Wallet::getFrozenCashBalance)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                return Map.of(
                                "totalWallets", allWallets.size(),
                                "activeWallets",
                                allWallets.stream().filter(w -> w.getStatus() == Wallet.WalletStatus.ACTIVE).count(),
                                "totalCashInSystem", totalCashInSystem,
                                "totalCoinsInSystem", totalCoinsInSystem,
                                "totalFrozenCash", totalFrozenCash);
        }

        /**
         * Get daily statistics (admin)
         */
        @Transactional(readOnly = true)
        public Map<String, Object> getDailyStatistics(String startDate, String endDate) {
                // TODO: Implement with actual date range queries
                // This would require additional repository methods
                return Map.of(
                                "message", "Daily statistics not yet implemented",
                                "startDate", startDate != null ? startDate : "today",
                                "endDate", endDate != null ? endDate : "today");
        }

        /**
         * Deduct cash from wallet (for premium purchase, etc.)
         */
        @Transactional
        public WalletTransaction deductCash(
                        Long userId,
                        BigDecimal cashAmount,
                        String description,
                        String referenceType,
                        String referenceId) {
                if (cashAmount.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("Deduct amount must be greater than 0");
                }

                Wallet wallet = walletRepository.findByUser_Id(userId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Wallet not found for user: " + userId));

                // Check if sufficient balance
                if (wallet.getCashBalance().compareTo(cashAmount) < 0) {
                        throw new IllegalArgumentException(String.format(
                                        "Insufficient balance. Available: %s VND, Required: %s VND",
                                        wallet.getCashBalance(), cashAmount));
                }

                // Deduct amount from cash balance
                BigDecimal newBalance = wallet.getCashBalance().subtract(cashAmount);
                wallet.setCashBalance(newBalance);
                walletRepository.save(wallet);

                // Create debit transaction
                WalletTransaction transaction = WalletTransaction.builder()
                                .wallet(wallet)
                                .transactionType(WalletTransaction.TransactionType.PURCHASE_PREMIUM)
                                .cashAmount(cashAmount)
                                .currencyType(WalletTransaction.CurrencyType.CASH)
                                .cashBalanceAfter(newBalance)
                                .description(description)
                                .status(WalletTransaction.TransactionStatus.COMPLETED)
                                .referenceId(referenceId)
                                .referenceType(referenceType)
                                .build();

                WalletTransaction savedTransaction = transactionRepository.save(transaction);
                log.info("✅ Deducted {} VND from user {} wallet", cashAmount, userId);

                return savedTransaction;
        }

        /**
         * Process refund to wallet (for subscription cancellation, etc.)
         */
        @Transactional
        public WalletTransaction processRefund(
                        Long userId,
                        BigDecimal cashAmount,
                        String description,
                        String referenceId) {
                if (cashAmount.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("Refund amount must be greater than 0");
                }

                Wallet wallet = walletRepository.findByUser_Id(userId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Wallet not found for user: " + userId));

                // Add refund amount to cash balance
                BigDecimal newBalance = wallet.getCashBalance().add(cashAmount);
                wallet.setCashBalance(newBalance);
                walletRepository.save(wallet);

                // Create refund transaction
                WalletTransaction transaction = WalletTransaction.builder()
                                .wallet(wallet)
                                .transactionType(WalletTransaction.TransactionType.REFUND_CASH)
                                .cashAmount(cashAmount)
                                .currencyType(WalletTransaction.CurrencyType.CASH)
                                .cashBalanceAfter(newBalance)
                                .description(description)
                                .status(WalletTransaction.TransactionStatus.COMPLETED)
                                .referenceId(referenceId)
                                .referenceType("SUBSCRIPTION_REFUND")
                                .build();

                WalletTransaction savedTransaction = transactionRepository.save(transaction);
                log.info("✅ Processed refund of {} VND to user {}", cashAmount, userId);

                return savedTransaction;
        }
}
