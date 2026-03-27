package com.exe.skillverse_backend.wallet_service.service.impl;

import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.impl.NotificationServiceImpl;
import com.exe.skillverse_backend.wallet_service.entity.Wallet;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.wallet_service.repository.WalletRepository;
import com.exe.skillverse_backend.wallet_service.repository.WalletTransactionRepository;
import com.exe.skillverse_backend.wallet_service.service.CoinService;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for SkillCoin management
 * Handles wallet-first coin purchases.
 * Direct PayOS coin checkout is retained only as a blocked legacy path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoinServiceImpl implements CoinService {

        private final WalletService walletService;
        private final WalletRepository walletRepository;
        private final WalletTransactionRepository transactionRepository;
        private final NotificationServiceImpl notificationService;

        // Configuration
        // COIN_PRICE_VND moved to interface
        private static final Long MIN_COIN_PURCHASE = 1L;
        private static final Long MAX_COIN_PURCHASE = 100000L;

        /**
         * Coin packages with discount and bonus
         */
        private static final Map<String, CoinPackage> COIN_PACKAGES = new HashMap<>();

        static {
                // Match packages from CoinWallet.tsx
                COIN_PACKAGES.put("trial", new CoinPackage(25L, 0L, 2500));
                COIN_PACKAGES.put("starter", new CoinPackage(50L, 5L, 4500));
                COIN_PACKAGES.put("basic", new CoinPackage(100L, 10L, 8500));
                COIN_PACKAGES.put("student", new CoinPackage(250L, 30L, 20000));
                COIN_PACKAGES.put("popular", new CoinPackage(500L, 75L, 40000));
                COIN_PACKAGES.put("weekend", new CoinPackage(750L, 150L, 60000));
                COIN_PACKAGES.put("premium", new CoinPackage(1000L, 200L, 80000));
                COIN_PACKAGES.put("business", new CoinPackage(1500L, 300L, 120000));
                COIN_PACKAGES.put("mega", new CoinPackage(2500L, 600L, 190000));
                COIN_PACKAGES.put("flash", new CoinPackage(3000L, 1000L, 210000));
                COIN_PACKAGES.put("ultimate", new CoinPackage(5000L, 1500L, 350000));
                COIN_PACKAGES.put("legendary", new CoinPackage(10000L, 3500L, 650000));
        }

        /**
         * OPTION 1: Mua Coin bằng Cash trong ví
         */
        @Transactional
        public Map<String, Object> purchaseCoinsWithWalletCash(
                        Long userId,
                        Long coinAmount,
                        String packageId) {
                // 1. Validate coin amount
                validateCoinAmount(coinAmount);

                // 2. Get package info (if specified)
                Long totalCoins = coinAmount;
                Long bonusCoins = 0L;
                BigDecimal price;

                if (packageId != null && COIN_PACKAGES.containsKey(packageId)) {
                        CoinPackage pkg = COIN_PACKAGES.get(packageId);
                        totalCoins = pkg.baseCoins + pkg.bonusCoins;
                        bonusCoins = pkg.bonusCoins;
                        price = pkg.priceVnd;
                } else {
                        // Custom amount - no bonus
                        price = COIN_PRICE_VND.multiply(new BigDecimal(coinAmount));
                }

                // 3. Get wallet with lock
                Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                                .orElseThrow(() -> new IllegalArgumentException("Ví không tồn tại"));

                // 4. Check cash balance
                if (!wallet.hasAvailableCash(price)) {
                        throw new IllegalStateException(
                                        String.format("Số dư Cash không đủ. Có: %s VNĐ, Cần: %s VNĐ",
                                                        wallet.getAvailableCashBalance(), price));
                }

                // 5. Deduct cash
                wallet.deductCash(price);

                // 6. Add coins
                wallet.addCoins(totalCoins);

                walletRepository.save(wallet);

                // 7. Create CASH transaction (payment for coins)
                WalletTransaction cashTx = WalletTransaction.builder()
                                .wallet(wallet)
                                .transactionType(WalletTransaction.TransactionType.PURCHASE_COINS)
                                .currencyType(WalletTransaction.CurrencyType.CASH)
                                .cashAmount(price)
                                .cashBalanceAfter(wallet.getCashBalance())
                                .description(String.format("Mua %d SkillCoin%s",
                                                totalCoins,
                                                bonusCoins > 0 ? " (+" + bonusCoins + " bonus)" : ""))
                                .referenceType("COIN_PURCHASE")
                                .referenceId(packageId != null ? packageId : "custom")
                                .status(WalletTransaction.TransactionStatus.COMPLETED)
                                .build();

                transactionRepository.save(cashTx);

                // 8. Create COIN transaction (received coins)
                WalletTransaction coinTx = WalletTransaction.builder()
                                .wallet(wallet)
                                .transactionType(bonusCoins > 0 ? WalletTransaction.TransactionType.BONUS_COINS
                                                : WalletTransaction.TransactionType.PURCHASE_COINS)
                                .currencyType(WalletTransaction.CurrencyType.COIN)
                                .coinAmount(totalCoins)
                                .coinBalanceAfter(wallet.getCoinBalance())
                                .description(String.format("Nhận %d SkillCoin từ mua%s",
                                                totalCoins,
                                                bonusCoins > 0 ? " (+" + bonusCoins + " bonus)" : ""))
                                .referenceType("COIN_PURCHASE")
                                .referenceId(packageId != null ? packageId : "custom")
                                .status(WalletTransaction.TransactionStatus.COMPLETED)
                                .build();

                transactionRepository.save(coinTx);

                log.info("✅ User {} đã mua {} Coins (bonus: {}) bằng Cash: {} VNĐ",
                                userId, coinAmount, bonusCoins, price);

                // Send notification
                try {
                        notificationService.createNotification(
                                        userId,
                                        "Mua Coin thành công",
                                        String.format("Bạn đã mua thành công %d SkillCoin%s bằng số dư ví.",
                                                        totalCoins,
                                                        bonusCoins > 0 ? " (+" + bonusCoins + " bonus)" : ""),
                                        NotificationType.COIN_PURCHASE,
                                        packageId != null ? packageId : "custom");
                } catch (Exception e) {
                        log.error("Failed to send coin purchase notification", e);
                }

                return Map.of(
                                "success", true,
                                "coinsReceived", totalCoins,
                                "baseCoins", coinAmount,
                                "bonusCoins", bonusCoins,
                                "paidAmount", price,
                                "newCoinBalance", wallet.getCoinBalance(),
                                "newCashBalance", wallet.getCashBalance());
        }

        /**
         * Handle PayOS callback for coin purchase
         * Called by PaymentService when payment is successful
         */
        @Transactional
        public void handleCoinPurchaseCallback(
                        Long userId,
                        String paymentReferenceId,
                        Map<String, String> metadata) {
                Long totalCoins = Long.parseLong(metadata.getOrDefault("totalCoins", "0"));
                Long bonusCoins = Long.parseLong(metadata.getOrDefault("bonusCoins", "0"));

                if (totalCoins == 0) {
                        log.error("❌ Invalid coin purchase callback - totalCoins = 0");
                        return;
                }

                // Add coins to wallet
                walletService.addCoins(
                                userId,
                                totalCoins,
                                bonusCoins > 0 ? WalletTransaction.TransactionType.BONUS_COINS
                                                : WalletTransaction.TransactionType.PURCHASE_COINS,
                                String.format("Mua %d SkillCoin qua PayOS%s",
                                                totalCoins,
                                                bonusCoins > 0 ? " (+" + bonusCoins + " bonus)" : ""),
                                "PAYMENT",
                                paymentReferenceId);

                log.info("✅ Đã cộng {} Coins cho user {} từ PayOS payment: {}",
                                totalCoins, userId, paymentReferenceId);

                // Send notification
                try {
                        notificationService.createNotification(
                                        userId,
                                        "Nạp Coin thành công",
                                        String.format("Bạn đã nạp thành công %d SkillCoin%s qua cổng thanh toán.",
                                                        totalCoins,
                                                        bonusCoins > 0 ? " (+" + bonusCoins + " bonus)" : ""),
                                        NotificationType.COIN_PURCHASE,
                                        paymentReferenceId);
                } catch (Exception e) {
                        log.error("Failed to send coin purchase callback notification", e);
                }
        }

        /**
         * Get all available coin packages
         */
        public List<Map<String, Object>> getCoinPackages() {
                List<Map<String, Object>> packages = new ArrayList<>();

                COIN_PACKAGES.forEach((id, pkg) -> {
                        Map<String, Object> packageInfo = new HashMap<>();
                        packageInfo.put("id", id);
                        packageInfo.put("coins", pkg.baseCoins);
                        packageInfo.put("bonusCoins", pkg.bonusCoins);
                        packageInfo.put("totalCoins", pkg.baseCoins + pkg.bonusCoins);
                        packageInfo.put("price", pkg.priceVnd);
                        packageInfo.put("pricePerCoin", pkg.priceVnd.divide(
                                        new BigDecimal(pkg.baseCoins + pkg.bonusCoins), 2,
                                        RoundingMode.HALF_UP));

                        packages.add(packageInfo);
                });

                // Sort by price ascending
                packages.sort((a, b) -> ((BigDecimal) a.get("price")).compareTo((BigDecimal) b.get("price")));

                return packages;
        }

        /**
         * Calculate coin price (for custom amounts)
         */
        public BigDecimal calculateCoinPrice(Long coinAmount) {
                validateCoinAmount(coinAmount);
                return COIN_PRICE_VND.multiply(new BigDecimal(coinAmount));
        }

        /**
         * Refund coins (when cancelling course purchase, etc.)
         */
        @Transactional
        public void refundCoins(
                        Long userId,
                        Long coinAmount,
                        String reason,
                        String referenceType,
                        String referenceId) {
                if (coinAmount <= 0) {
                        throw new IllegalArgumentException("Số Coin hoàn trả phải lớn hơn 0");
                }

                walletService.addCoins(
                                userId,
                                coinAmount,
                                WalletTransaction.TransactionType.REFUND_COINS,
                                reason != null ? reason : "Hoàn trả Coin",
                                referenceType,
                                referenceId);

                log.info("🔄 Đã hoàn {} Coins cho user {} - Lý do: {}", coinAmount, userId, reason);
        }

        // ==================== HELPER METHODS ====================

        private void validateCoinAmount(Long coinAmount) {
                if (coinAmount == null || coinAmount <= 0) {
                        throw new IllegalArgumentException("Số lượng Coin phải lớn hơn 0");
                }

                if (coinAmount < MIN_COIN_PURCHASE) {
                        throw new IllegalArgumentException(
                                        String.format("Số lượng Coin tối thiểu là %d", MIN_COIN_PURCHASE));
                }

                if (coinAmount > MAX_COIN_PURCHASE) {
                        throw new IllegalArgumentException(
                                        String.format("Số lượng Coin tối đa là %d", MAX_COIN_PURCHASE));
                }
        }

        /**
         * Inner class for coin package definition
         */
        private static class CoinPackage {
                Long baseCoins;
                Long bonusCoins;
                BigDecimal priceVnd;

                CoinPackage(Long baseCoins, Long bonusCoins, int priceVnd) {
                        this.baseCoins = baseCoins;
                        this.bonusCoins = bonusCoins;
                        this.priceVnd = new BigDecimal(priceVnd);
                }
        }
}
