package com.exe.skillverse_backend.gamification_service.service.impl;

import com.exe.skillverse_backend.gamification_service.dto.response.BadgeDefinitionResponse;
import com.exe.skillverse_backend.gamification_service.dto.response.CoinTransactionResponse;
import com.exe.skillverse_backend.gamification_service.dto.response.GamificationDashboardResponse;
import com.exe.skillverse_backend.gamification_service.dto.response.MiniGameDefinitionResponse;
import com.exe.skillverse_backend.gamification_service.dto.response.UserBadgeResponse;
import com.exe.skillverse_backend.gamification_service.dto.response.UserWalletResponse;
import com.exe.skillverse_backend.gamification_service.entity.GamificationBadgeDefinition;
import com.exe.skillverse_backend.gamification_service.entity.GamificationCoinTransaction;
import com.exe.skillverse_backend.gamification_service.entity.GamificationLeaderboardSnapshot;
import com.exe.skillverse_backend.gamification_service.entity.GamificationMiniGameDefinition;
import com.exe.skillverse_backend.gamification_service.entity.GamificationUserBadge;
import com.exe.skillverse_backend.gamification_service.entity.GamificationUserWallet;
import com.exe.skillverse_backend.gamification_service.repository.GamificationBadgeDefinitionRepository;
import com.exe.skillverse_backend.gamification_service.repository.GamificationCoinTransactionRepository;
import com.exe.skillverse_backend.gamification_service.repository.GamificationLeaderboardSnapshotRepository;
import com.exe.skillverse_backend.gamification_service.repository.GamificationMiniGameDefinitionRepository;
import com.exe.skillverse_backend.gamification_service.repository.GamificationUserBadgeRepository;
import com.exe.skillverse_backend.gamification_service.repository.GamificationUserWalletRepository;
import com.exe.skillverse_backend.gamification_service.service.GamificationDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of GamificationDashboardService
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GamificationDashboardServiceImpl implements GamificationDashboardService {

    private final GamificationUserWalletRepository walletRepository;
    private final GamificationUserBadgeRepository userBadgeRepository;
    private final GamificationBadgeDefinitionRepository badgeDefRepository;
    private final GamificationCoinTransactionRepository transactionRepository;
    private final GamificationMiniGameDefinitionRepository gameDefRepository;
    private final GamificationLeaderboardSnapshotRepository leaderboardRepository;

    @Override
    public GamificationDashboardResponse getUserDashboard(Long userId, String userPlan) {
        log.info("Fetching gamification dashboard for user: {}", userId);

        // Get user wallet
        GamificationUserWallet wallet = walletRepository.findByUserId(userId)
                .orElse(null);
        UserWalletResponse walletResponse = wallet != null ? mapWalletToResponse(wallet) : null;

        // Get badge statistics
        Long totalBadgesCount = badgeDefRepository.count();
        Long unlockedBadgesCount = userBadgeRepository.countByUserId(userId);

        // Get recent badges (last 5)
        List<GamificationUserBadge> recentBadges = userBadgeRepository
                .findByUserIdOrderByEarnedAtDesc(userId);
        List<UserBadgeResponse> recentBadgeResponses = recentBadges.stream()
                .limit(5)
                .map(this::mapUserBadgeToResponse)
                .collect(Collectors.toList());

        // Get recent transactions (last 10)
        LocalDateTime last30Days = LocalDateTime.now().minusDays(30);
        List<GamificationCoinTransaction> recentTransactions = transactionRepository
                .findByUserIdAndTransactionDateBetween(userId, last30Days, LocalDateTime.now());
        List<CoinTransactionResponse> transactionResponses = recentTransactions.stream()
                .limit(10)
                .map(this::mapTransactionToResponse)
                .collect(Collectors.toList());

        // Get available games
        List<GamificationMiniGameDefinition> games = gameDefRepository.findByIsActiveTrue();
        List<MiniGameDefinitionResponse> gameResponses = games.stream()
                .map(this::mapGameToResponse)
                .collect(Collectors.toList());

        // Get user rank from leaderboard
        Integer currentRank = getCurrentUserRank(userId);

        return GamificationDashboardResponse.builder()
                .wallet(walletResponse)
                .totalBadges(totalBadgesCount.intValue())
                .unlockedBadges(unlockedBadgesCount.intValue())
                .currentRank(currentRank)
                .recentBadges(recentBadgeResponses)
                .recentTransactions(transactionResponses)
                .availableGames(gameResponses)
                .build();
    }

    @Override
    public Map<String, Object> getUserStatistics(Long userId) {
        log.info("Fetching gamification statistics for user: {}", userId);

        Map<String, Object> stats = new HashMap<>();

        // Wallet stats
        GamificationUserWallet wallet = walletRepository.findByUserId(userId).orElse(null);
        if (wallet != null) {
            stats.put("totalCoins", wallet.getTotalCoins());
            stats.put("earnedCoins", wallet.getEarnedCoins());
            stats.put("spentCoins", wallet.getSpentCoins());
            stats.put("totalXp", wallet.getTotalXp());
            stats.put("streakDays", wallet.getStreakDays());
        }

        // Badge stats
        Long totalBadges = userBadgeRepository.countByUserId(userId);
        stats.put("totalBadges", totalBadges);

        // Transaction stats (calculate from wallet)
        stats.put("totalEarned", wallet != null ? wallet.getEarnedCoins() : 0);
        stats.put("totalSpent", wallet != null ? wallet.getSpentCoins() : 0);

        // Rank
        stats.put("currentRank", getCurrentUserRank(userId));

        return stats;
    }

    private UserWalletResponse mapWalletToResponse(GamificationUserWallet wallet) {
        return UserWalletResponse.builder()
                .userId(wallet.getUserId())
                .totalCoins(wallet.getTotalCoins())
                .earnedCoins(wallet.getEarnedCoins())
                .spentCoins(wallet.getSpentCoins())
                .totalXp(wallet.getTotalXp())
                .streakDays(wallet.getStreakDays())
                .lastActivityDate(wallet.getLastActivityDate())
                .build();
    }

    private UserBadgeResponse mapUserBadgeToResponse(GamificationUserBadge userBadge) {
        GamificationBadgeDefinition badgeDef = userBadge.getBadgeDefinition();
        
        BadgeDefinitionResponse badgeDefResponse = BadgeDefinitionResponse.builder()
                .badgeDefId(badgeDef.getBadgeDefId())
                .badgeKey(badgeDef.getBadgeKey())
                .badgeTitle(badgeDef.getBadgeTitle())
                .badgeDescription(badgeDef.getBadgeDescription())
                .badgeIcon(badgeDef.getBadgeIcon())
                .badgeCategory(badgeDef.getBadgeCategory())
                .badgeRarity(badgeDef.getBadgeRarity())
                .criteriaDescription(badgeDef.getCriteriaDescription())
                .coinReward(badgeDef.getCoinReward())
                .xpReward(badgeDef.getXpReward())
                .isActive(badgeDef.getIsActive())
                .displayOrder(badgeDef.getDisplayOrder())
                .build();
        
        return UserBadgeResponse.builder()
                .userBadgeId(userBadge.getUserBadgeId())
                .userId(userBadge.getUserId())
                .badgeDefinition(badgeDefResponse)
                .earnedAt(userBadge.getEarnedAt())
                .coinsAwarded(userBadge.getCoinsAwarded())
                .xpAwarded(userBadge.getXpAwarded())
                .unlocked(true)
                .build();
    }

    private CoinTransactionResponse mapTransactionToResponse(GamificationCoinTransaction transaction) {
        return CoinTransactionResponse.builder()
                .transactionId(transaction.getTransactionId())
                .userId(transaction.getUserId())
                .coinAmount(transaction.getCoinAmount())
                .transactionType(transaction.getTransactionType())
                .sourceType(transaction.getSourceType())
                .sourceId(transaction.getSourceId())
                .description(transaction.getDescription())
                .transactionDate(transaction.getTransactionDate())
                .build();
    }

    private MiniGameDefinitionResponse mapGameToResponse(GamificationMiniGameDefinition game) {
        return MiniGameDefinitionResponse.builder()
                .gameDefId(game.getGameDefId())
                .gameKey(game.getGameKey())
                .gameTitle(game.getGameTitle())
                .gameDescription(game.getGameDescription())
                .gameIcon(game.getGameIcon())
                .gameType(game.getGameType())
                .difficultyLevel(game.getDifficultyLevel())
                .baseCoinReward(game.getBaseCoinReward())
                .maxCoinReward(game.getMaxCoinReward())
                .xpReward(game.getXpReward())
                .cooldownMinutes(game.getCooldownMinutes())
                .maxPlaysPerDay(game.getMaxPlaysPerDay())
                .maxCoinsPerDay(game.getMaxCoinsPerDay())
                .isActive(game.getIsActive())
                .isPremiumOnly(game.getIsPremiumOnly())
                .requiredPremiumPlan(game.getRequiredPremiumPlan())
                .premiumCoinMultiplier(game.getPremiumCoinMultiplier())
                .createdAt(game.getCreatedAt())
                .build();
    }

    private Integer getCurrentUserRank(Long userId) {
        try {
            Optional<GamificationLeaderboardSnapshot> userRank = leaderboardRepository
                    .findUserLatestRank(userId, "WEEKLY", "COIN");
            
            if (userRank.isPresent()) {
                return userRank.get().getRankPosition();
            }
        } catch (Exception e) {
            log.warn("Could not fetch user rank: {}", e.getMessage());
        }
        return null;
    }
}
