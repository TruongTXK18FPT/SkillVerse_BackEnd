package com.exe.skillverse_backend.gamification_service.service;

import com.exe.skillverse_backend.gamification_service.dto.request.AdminCoinAdjustmentRequest;
import com.exe.skillverse_backend.gamification_service.dto.response.CoinTransactionResponse;
import com.exe.skillverse_backend.gamification_service.dto.response.UserWalletResponse;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for managing user coin wallets and transactions
 */
public interface GamificationWalletService {

    /**
     * Get or create wallet for user
     */
    UserWalletResponse getOrCreateWallet(Long userId);

    /**
     * Get user wallet by user ID
     */
    UserWalletResponse getUserWallet(Long userId);

    /**
     * Award coins to user
     */
    CoinTransactionResponse awardCoins(Long userId, Integer coinAmount, Integer xpAmount,
                                       String sourceType, Long sourceId, String description);

    /**
     * Deduct coins from user
     */
    CoinTransactionResponse deductCoins(Long userId, Integer coinAmount, String reason);

    /**
     * Admin manual coin adjustment
     */
    CoinTransactionResponse adminAdjustCoins(AdminCoinAdjustmentRequest request, Long adminId);

    /**
     * Get transaction history for user
     */
    Page<CoinTransactionResponse> getUserTransactionHistory(Long userId, Pageable pageable);

    /**
     * Get total coins earned by user in a period
     */
    Integer getTotalCoinsEarned(Long userId, LocalDateTime since);

    /**
     * Update user streak based on activity
     */
    void updateUserStreak(Long userId);

    /**
     * Check if user has sufficient coins
     */
    boolean hasSufficientCoins(Long userId, Integer requiredCoins);
}
