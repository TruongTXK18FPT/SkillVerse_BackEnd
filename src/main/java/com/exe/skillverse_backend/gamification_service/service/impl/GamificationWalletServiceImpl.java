package com.exe.skillverse_backend.gamification_service.service.impl;

import com.exe.skillverse_backend.gamification_service.dto.request.AdminCoinAdjustmentRequest;
import com.exe.skillverse_backend.gamification_service.dto.response.CoinTransactionResponse;
import com.exe.skillverse_backend.gamification_service.dto.response.UserWalletResponse;
import com.exe.skillverse_backend.gamification_service.entity.GamificationCoinTransaction;
import com.exe.skillverse_backend.gamification_service.entity.GamificationUserWallet;
import com.exe.skillverse_backend.gamification_service.repository.GamificationCoinTransactionRepository;
import com.exe.skillverse_backend.gamification_service.repository.GamificationUserWalletRepository;
import com.exe.skillverse_backend.gamification_service.service.GamificationWalletService;
import com.exe.skillverse_backend.wallet_service.dto.response.WalletResponse;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GamificationWalletServiceImpl implements GamificationWalletService {

    private final GamificationUserWalletRepository walletRepository;
    private final GamificationCoinTransactionRepository transactionRepository;
    private final WalletService walletService;

    @Override
    @Transactional
    public UserWalletResponse getOrCreateWallet(Long userId) {
        GamificationUserWallet gamificationWallet = walletRepository.findByUserId(userId)
                .orElseGet(() -> createWalletForUser(userId));
        
        WalletResponse mainWallet = walletService.getWalletByUserId(userId);
        
        return mapToWalletResponse(gamificationWallet, mainWallet);
    }

    @Override
    @Transactional(readOnly = true)
    public UserWalletResponse getUserWallet(Long userId) {
        GamificationUserWallet gamificationWallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Wallet not found for user: " + userId));
        
        WalletResponse mainWallet = walletService.getWalletByUserId(userId);
        
        return mapToWalletResponse(gamificationWallet, mainWallet);
    }

    @Override
    @Transactional
    public CoinTransactionResponse awardCoins(Long userId, Integer coinAmount, Integer xpAmount,
                                               String sourceType, Long sourceId, String description) {
        log.info("Awarding {} coins and {} XP to user {} from source {}", coinAmount, xpAmount, userId, sourceType);

        GamificationUserWallet wallet = getOrCreateWalletEntity(userId);

        // Update XP in Gamification Wallet
        if (xpAmount != null && xpAmount > 0) {
            wallet.setTotalXp(wallet.getTotalXp() + xpAmount);
        }
        // NOTE: We do NOT update lastActivityDate here to allow updateUserStreak to function correctly.
        // Callers must call updateUserStreak explicitly if this award constitutes a streak activity.
        walletRepository.save(wallet);

        // Award Coins to Main Wallet
        if (coinAmount > 0) {
            walletService.addCoins(
                userId, 
                coinAmount.longValue(), 
                WalletTransaction.TransactionType.REWARD_ACHIEVEMENT, 
                description, 
                sourceType, 
                sourceId != null ? sourceId.toString() : "GAMIFICATION"
            );
        }

        // Create transaction record for Gamification History (XP mainly)
        GamificationCoinTransaction transaction = GamificationCoinTransaction.builder()
                .userId(userId)
                .coinAmount(coinAmount)
                .xpAmount(xpAmount != null ? xpAmount : 0)
                .transactionType("EARN")
                .sourceType(sourceType)
                .sourceId(sourceId)
                .description(description)
                .isVerified(true)
                .balanceAfter(0) // No longer tracking coin balance here
                .build();

        transaction = transactionRepository.save(transaction);
        log.info("Gamification Transaction created: ID={}, User={}, XP={}", transaction.getTransactionId(), userId, xpAmount);

        return mapToTransactionResponse(transaction);
    }

    @Override
    @Transactional
    public CoinTransactionResponse deductCoins(Long userId, Integer coinAmount, String reason) {
        log.info("Deducting {} coins from user {} for reason: {}", coinAmount, userId, reason);

        // Deduct from Main Wallet
        walletService.deductCoins(
            userId, 
            coinAmount.longValue(), 
            WalletTransaction.TransactionType.SPEND_COINS, 
            reason, 
            "GAMIFICATION_SPEND", 
            "SPEND_" + System.currentTimeMillis()
        );

        GamificationUserWallet wallet = getOrCreateWalletEntity(userId);
        // NOTE: We do NOT update lastActivityDate here to allow updateUserStreak to function correctly.
        walletRepository.save(wallet);

        // Create transaction record
        GamificationCoinTransaction transaction = GamificationCoinTransaction.builder()
                .userId(userId)
                .coinAmount(-coinAmount)
                .xpAmount(0)
                .transactionType("SPEND")
                .sourceType("PURCHASE")
                .description(reason)
                .isVerified(true)
                .balanceAfter(0)
                .build();

        return mapToTransactionResponse(transactionRepository.save(transaction));
    }

    @Override
    @Transactional
    public CoinTransactionResponse adminAdjustCoins(AdminCoinAdjustmentRequest request, Long adminId) {
        log.info("Admin {} adjusting coins for user {}: {} coins", adminId, request.getUserId(), request.getCoinAmount());

        GamificationUserWallet wallet = getOrCreateWalletEntity(request.getUserId());

        // Update XP in Gamification Wallet
        if (request.getXpAmount() != null && request.getXpAmount() > 0) {
            wallet.setTotalXp(wallet.getTotalXp() + request.getXpAmount());
        }
        // NOTE: We do NOT update lastActivityDate here to allow updateUserStreak to function correctly.
        walletRepository.save(wallet);

        // Update Coins in Main Wallet
        if (request.getCoinAmount() != 0) {
            if (request.getCoinAmount() > 0) {
                walletService.addCoins(
                    request.getUserId(),
                    (long) request.getCoinAmount(),
                    WalletTransaction.TransactionType.ADMIN_ADJUSTMENT,
                    request.getReason(),
                    "ADMIN",
                    adminId.toString()
                );
            } else {
                walletService.deductCoins(
                    request.getUserId(),
                    (long) Math.abs(request.getCoinAmount()),
                    WalletTransaction.TransactionType.ADMIN_ADJUSTMENT,
                    request.getReason(),
                    "ADMIN",
                    adminId.toString()
                );
            }
        }

        // Create transaction record
        GamificationCoinTransaction transaction = GamificationCoinTransaction.builder()
                .userId(request.getUserId())
                .coinAmount(request.getCoinAmount())
                .xpAmount(request.getXpAmount() != null ? request.getXpAmount() : 0)
                .transactionType("ADMIN_ADJUST")
                .sourceType("ADMIN")
                .description(request.getReason())
                .isVerified(true)
                .balanceAfter(0)
                .adminId(adminId)
                .metadata(request.getMetadata())
                .build();

        return mapToTransactionResponse(transactionRepository.save(transaction));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CoinTransactionResponse> getUserTransactionHistory(Long userId, Pageable pageable) {
        return transactionRepository.findByUserIdOrderByTransactionDateDesc(userId, pageable)
                .map(this::mapToTransactionResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Integer getTotalCoinsEarned(Long userId, LocalDateTime since) {
        return transactionRepository.sumCoinsEarnedByUserSince(userId, since);
    }

    @Override
    @Transactional
    public void updateUserStreak(Long userId) {
        GamificationUserWallet wallet = getOrCreateWalletEntity(userId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastActivity = wallet.getLastActivityDate();

        if (lastActivity == null) {
            wallet.setStreakDays(1);
        } else {
            long daysSinceLastActivity = ChronoUnit.DAYS.between(lastActivity.toLocalDate(), now.toLocalDate());
            
            if (daysSinceLastActivity == 1) {
                // Continue streak
                wallet.setStreakDays(wallet.getStreakDays() + 1);
            } else if (daysSinceLastActivity > 1) {
                // Streak broken
                wallet.setStreakDays(1);
            }
            // If same day, no change
        }

        wallet.setLastActivityDate(now);
        walletRepository.save(wallet);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasSufficientCoins(Long userId, Integer requiredCoins) {
        WalletResponse wallet = walletService.getWalletByUserId(userId);
        return wallet.getCoinBalance() >= requiredCoins;
    }

    // Helper methods
    private GamificationUserWallet createWalletForUser(Long userId) {
        log.info("Creating new wallet for user {}", userId);
        GamificationUserWallet wallet = GamificationUserWallet.builder()
                .userId(userId)
                .totalCoins(0)
                .earnedCoins(0)
                .spentCoins(0)
                .totalXp(0)
                .streakDays(0)
                .lastActivityDate(LocalDateTime.now())
                .build();
        return walletRepository.save(wallet);
    }

    private GamificationUserWallet getOrCreateWalletEntity(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> createWalletForUser(userId));
    }

    private UserWalletResponse mapToWalletResponse(GamificationUserWallet gamificationWallet, WalletResponse mainWallet) {
        return UserWalletResponse.builder()
                .walletId(gamificationWallet.getWalletId())
                .userId(gamificationWallet.getUserId())
                .totalCoins(mainWallet.getCoinBalance().intValue()) // Use Main Wallet Balance
                .earnedCoins(mainWallet.getTotalCoinsEarned().intValue()) // Use Main Wallet Stats
                .spentCoins(mainWallet.getTotalCoinsSpent().intValue()) // Use Main Wallet Stats
                .totalXp(gamificationWallet.getTotalXp())
                .streakDays(gamificationWallet.getStreakDays())
                .lastActivityDate(gamificationWallet.getLastActivityDate())
                .updatedAt(gamificationWallet.getUpdatedAt())
                .build();
    }

    private CoinTransactionResponse mapToTransactionResponse(GamificationCoinTransaction transaction) {
        return CoinTransactionResponse.builder()
                .transactionId(transaction.getTransactionId())
                .userId(transaction.getUserId())
                .coinAmount(transaction.getCoinAmount())
                .xpAmount(transaction.getXpAmount())
                .transactionType(transaction.getTransactionType())
                .sourceType(transaction.getSourceType())
                .sourceId(transaction.getSourceId())
                .description(transaction.getDescription())
                .isVerified(transaction.getIsVerified())
                .balanceAfter(transaction.getBalanceAfter())
                .transactionDate(transaction.getTransactionDate())
                .build();
    }
}
