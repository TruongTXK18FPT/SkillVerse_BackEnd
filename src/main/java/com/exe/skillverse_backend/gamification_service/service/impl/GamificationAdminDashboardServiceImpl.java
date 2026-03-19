package com.exe.skillverse_backend.gamification_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.gamification_service.dto.response.AdminGamificationStatsResponse;
import com.exe.skillverse_backend.gamification_service.dto.response.LeaderboardEntryResponse;
import com.exe.skillverse_backend.gamification_service.dto.response.UserActivityTrackingResponse;
import com.exe.skillverse_backend.gamification_service.service.GamificationAdminDashboardService;
import com.exe.skillverse_backend.user_service.entity.UserProfile;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import com.exe.skillverse_backend.wallet_service.entity.Wallet;
import com.exe.skillverse_backend.wallet_service.repository.WalletRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.exe.skillverse_backend.gamification_service.entity.GamificationBadgeDefinition;
import com.exe.skillverse_backend.gamification_service.entity.GamificationGameSession;
import com.exe.skillverse_backend.gamification_service.entity.GamificationMiniGameDefinition;
import com.exe.skillverse_backend.gamification_service.entity.GamificationUserBadge;
import com.exe.skillverse_backend.gamification_service.entity.GamificationUserWallet;
import com.exe.skillverse_backend.gamification_service.repository.GamificationBadgeDefinitionRepository;
import com.exe.skillverse_backend.gamification_service.repository.GamificationCoinTransactionRepository;
import com.exe.skillverse_backend.gamification_service.repository.GamificationGameSessionRepository;
import com.exe.skillverse_backend.gamification_service.repository.GamificationMiniGameDefinitionRepository;
import com.exe.skillverse_backend.gamification_service.repository.GamificationUserBadgeRepository;
import com.exe.skillverse_backend.gamification_service.repository.GamificationUserWalletRepository;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GamificationAdminDashboardServiceImpl implements GamificationAdminDashboardService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final GamificationUserWalletRepository walletRepository;
    private final WalletRepository mainWalletRepository;
    private final GamificationUserBadgeRepository userBadgeRepository;
    private final GamificationBadgeDefinitionRepository badgeDefRepository;
    private final GamificationMiniGameDefinitionRepository gameDefRepository;
    private final GamificationGameSessionRepository gameSessionRepository;
    private final GamificationCoinTransactionRepository transactionRepository;

    @Override
    public AdminGamificationStatsResponse getDashboardStats() {
        log.info("Generating admin gamification dashboard stats");
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        LocalDateTime startOfWeek = now.minusDays(7);
        
        // Basic counts
        long totalUsers = userRepository.count();
        long activeUsers = walletRepository.countByLastActivityDateAfter(startOfWeek);
        
        // Coin statistics
        Long totalCoinsDistributed = transactionRepository.sumCoinsByTransactionType("EARN");
        Long totalCoinsSpent = transactionRepository.sumCoinsByTransactionType("SPEND");
        Long coinsDistributedToday = transactionRepository.sumCoinsByTransactionTypeAndDateAfter("EARN", startOfDay);
        Long coinsDistributedThisWeek = transactionRepository.sumCoinsByTransactionTypeAndDateAfter("EARN", startOfWeek);
        
        // Badge statistics
        long totalBadgesAwarded = userBadgeRepository.count();
        Long badgesAwardedToday = userBadgeRepository.countByEarnedAtAfter(startOfDay);
        Long badgesAwardedThisWeek = userBadgeRepository.countByEarnedAtAfter(startOfWeek);
        
        // Game statistics
        long totalGameSessions = gameSessionRepository.count();
        Long gameSessionsToday = gameSessionRepository.countByPlayedAtAfter(startOfDay);
        Long gameSessionsThisWeek = gameSessionRepository.countByPlayedAtAfter(startOfWeek);
        
        return AdminGamificationStatsResponse.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .totalCoinsDistributed(totalCoinsDistributed != null ? totalCoinsDistributed : 0L)
                .totalCoinsSpent(totalCoinsSpent != null ? totalCoinsSpent : 0L)
                .totalBadgesAwarded(totalBadgesAwarded)
                .totalGameSessions(totalGameSessions)
                .coinsDistributedToday(coinsDistributedToday != null ? coinsDistributedToday : 0L)
                .coinsDistributedThisWeek(coinsDistributedThisWeek != null ? coinsDistributedThisWeek : 0L)
                .badgesAwardedToday(badgesAwardedToday != null ? badgesAwardedToday : 0L)
                .badgesAwardedThisWeek(badgesAwardedThisWeek != null ? badgesAwardedThisWeek : 0L)
                .gameSessionsToday(gameSessionsToday != null ? gameSessionsToday : 0L)
                .gameSessionsThisWeek(gameSessionsThisWeek != null ? gameSessionsThisWeek : 0L)
                .topCoinEarners(getTopCoinEarnersAsLeaderboard(10))
                .topXpEarners(getTopXpEarnersAsLeaderboard(10))
                .mostActiveUsers(getMostActiveUsers(10))
                .gameStats(getGameStats())
                .badgeStats(getBadgeStats())
                .activityTrends(getActivityTrends(LocalDate.now().minusDays(30), LocalDate.now()))
                .generatedAt(now)
                .build();
    }

    private List<LeaderboardEntryResponse> getTopCoinEarnersAsLeaderboard(int limit) {
        List<Wallet> topWallets = mainWalletRepository.findTopByCoinBalanceDesc(PageRequest.of(0, limit));
        List<LeaderboardEntryResponse> result = new ArrayList<>();
        int rank = 1;
        for (Wallet wallet : topWallets) {
            User user = wallet.getUser();
            GamificationUserWallet gamiWallet = walletRepository.findByUserId(user.getId()).orElse(null);
            UserProfile userProfile = userProfileRepository.findByUserId(user.getId()).orElse(null);

            String displayName;
            if (userProfile != null && userProfile.getFullName() != null && !userProfile.getFullName().isEmpty()) {
                displayName = userProfile.getFullName();
            } else {
                String fName = user.getFirstName();
                String lName = user.getLastName();
                if (fName != null && lName != null) {
                    displayName = fName + " " + lName;
                } else if (fName != null) {
                    displayName = fName;
                } else if (lName != null) {
                    displayName = lName;
                } else {
                    displayName = user.getEmail().split("@")[0];
                }
            }

            String avatarUrl = null;
            if (userProfile != null && userProfile.getAvatarMedia() != null) {
                avatarUrl = userProfile.getAvatarMedia().getUrl();
            } else if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                avatarUrl = user.getAvatarUrl();
            }
            
            result.add(LeaderboardEntryResponse.builder()
                    .userId(user.getId())
                    .userName(displayName)
                    .fullName(displayName)
                    .userAvatar(user.getAvatarUrl())
                    .avatarMediaUrl(avatarUrl)
                    .rankPosition(rank++)
                    .totalCoins(wallet.getCoinBalance().intValue())
                    .totalXp(gamiWallet != null ? gamiWallet.getTotalXp() : 0)
                    .streakDays(gamiWallet != null ? gamiWallet.getStreakDays() : 0)
                    .build());
        }
        return result;
    }

    private List<LeaderboardEntryResponse> getTopXpEarnersAsLeaderboard(int limit) {
        List<GamificationUserWallet> topWallets = walletRepository.findTopByTotalXpOrderByDesc(limit);
        List<LeaderboardEntryResponse> result = new ArrayList<>();
        int rank = 1;
        for (GamificationUserWallet wallet : topWallets) {
            User user = wallet.getUser();
            UserProfile userProfile = userProfileRepository.findByUserId(user.getId()).orElse(null);

            String displayName;
            if (userProfile != null && userProfile.getFullName() != null && !userProfile.getFullName().isEmpty()) {
                displayName = userProfile.getFullName();
            } else {
                String fName = user.getFirstName();
                String lName = user.getLastName();
                if (fName != null && lName != null) {
                    displayName = fName + " " + lName;
                } else if (fName != null) {
                    displayName = fName;
                } else if (lName != null) {
                    displayName = lName;
                } else {
                    displayName = user.getEmail().split("@")[0];
                }
            }

            String avatarUrl = null;
            if (userProfile != null && userProfile.getAvatarMedia() != null) {
                avatarUrl = userProfile.getAvatarMedia().getUrl();
            } else if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                avatarUrl = user.getAvatarUrl();
            }

            result.add(LeaderboardEntryResponse.builder()
                    .userId(user.getId())
                    .userName(displayName)
                    .fullName(displayName)
                    .userAvatar(user.getAvatarUrl())
                    .avatarMediaUrl(avatarUrl)
                    .rankPosition(rank++)
                    .totalCoins(wallet.getTotalCoins())
                    .totalXp(wallet.getTotalXp())
                    .streakDays(wallet.getStreakDays())
                    .build());
        }
        return result;
    }

    @Override
    public List<AdminGamificationStatsResponse.ActivityTrendData> getActivityTrends(LocalDate startDate, LocalDate endDate) {
        List<AdminGamificationStatsResponse.ActivityTrendData> trends = new ArrayList<>();
        
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            LocalDateTime dayStart = currentDate.atStartOfDay();
            LocalDateTime dayEnd = currentDate.plusDays(1).atStartOfDay();
            
            Long coinsDistributed = transactionRepository.sumCoinsByDateRange("EARN", dayStart, dayEnd);
            Long badgesAwarded = userBadgeRepository.countByEarnedAtBetween(dayStart, dayEnd);
            Long gameSessions = gameSessionRepository.countByPlayedAtBetween(dayStart, dayEnd);
            Long activeUsers = walletRepository.countByLastActivityDateBetween(dayStart, dayEnd);
            
            trends.add(AdminGamificationStatsResponse.ActivityTrendData.builder()
                    .date(currentDate.toString())
                    .coinsDistributed(coinsDistributed != null ? coinsDistributed : 0L)
                    .badgesAwarded(badgesAwarded != null ? badgesAwarded : 0L)
                    .gameSessions(gameSessions != null ? gameSessions : 0L)
                    .activeUsers(activeUsers != null ? activeUsers : 0L)
                    .newUsers(0L) // Would need user creation date tracking
                    .build());
            
            currentDate = currentDate.plusDays(1);
        }
        
        return trends;
    }

    @Override
    public List<AdminGamificationStatsResponse.UserActivitySummary> getTopCoinEarners(int limit) {
        List<Wallet> topWallets = mainWalletRepository.findTopByCoinBalanceDesc(PageRequest.of(0, limit));
        List<AdminGamificationStatsResponse.UserActivitySummary> result = new ArrayList<>();
        
        for (Wallet wallet : topWallets) {
            User user = wallet.getUser();
            GamificationUserWallet gamiWallet = walletRepository.findByUserId(user.getId()).orElse(null);
            UserProfile userProfile = userProfileRepository.findByUserId(user.getId()).orElse(null);

            String displayName;
            if (userProfile != null && userProfile.getFullName() != null && !userProfile.getFullName().isEmpty()) {
                displayName = userProfile.getFullName();
            } else {
                String fName = user.getFirstName();
                String lName = user.getLastName();
                if (fName != null && lName != null) {
                    displayName = fName + " " + lName;
                } else if (fName != null) {
                    displayName = fName;
                } else if (lName != null) {
                    displayName = lName;
                } else {
                    displayName = user.getEmail().split("@")[0];
                }
            }

            String avatarUrl = null;
            if (userProfile != null && userProfile.getAvatarMedia() != null) {
                avatarUrl = userProfile.getAvatarMedia().getUrl();
            } else if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                avatarUrl = user.getAvatarUrl();
            }

            Long badgeCount = userBadgeRepository.countByUserId(user.getId());
            Long gamesPlayed = gameSessionRepository.countByUserId(user.getId());
            
            result.add(AdminGamificationStatsResponse.UserActivitySummary.builder()
                    .userId(user.getId())
                    .userName(displayName)
                    .fullName(displayName)
                    .userAvatar(user.getAvatarUrl())
                    .avatarMediaUrl(avatarUrl)
                    .gamesPlayed(gamesPlayed.intValue())
                    .badgesEarned(badgeCount.intValue())
                    .totalCoins(wallet.getCoinBalance().intValue())
                    .totalXp(gamiWallet != null ? gamiWallet.getTotalXp() : 0)
                    .currentStreak(gamiWallet != null ? gamiWallet.getStreakDays() : 0)
                    .lastActivityAt(gamiWallet != null ? gamiWallet.getLastActivityDate() : null)
                    .build());
        }
        return result;
    }

    @Override
    public List<AdminGamificationStatsResponse.UserActivitySummary> getMostActiveUsers(int limit) {
        // Get oldest users (active longest on platform)
        List<User> oldestUsers = userRepository.findOldestUsers(PageRequest.of(0, limit));
        List<AdminGamificationStatsResponse.UserActivitySummary> result = new ArrayList<>();
        
        for (User user : oldestUsers) {
            GamificationUserWallet wallet = walletRepository.findByUserId(user.getId()).orElse(null);
            UserProfile userProfile = userProfileRepository.findByUserId(user.getId()).orElse(null);

            String displayName;
            if (userProfile != null && userProfile.getFullName() != null && !userProfile.getFullName().isEmpty()) {
                displayName = userProfile.getFullName();
            } else {
                String fName = user.getFirstName();
                String lName = user.getLastName();
                if (fName != null && lName != null) {
                    displayName = fName + " " + lName;
                } else if (fName != null) {
                    displayName = fName;
                } else if (lName != null) {
                    displayName = lName;
                } else {
                    displayName = user.getEmail().split("@")[0];
                }
            }

            String avatarUrl = null;
            if (userProfile != null && userProfile.getAvatarMedia() != null) {
                avatarUrl = userProfile.getAvatarMedia().getUrl();
            } else if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                avatarUrl = user.getAvatarUrl();
            }

            Long badgeCount = userBadgeRepository.countByUserId(user.getId());
            Long gamesPlayed = gameSessionRepository.countByUserId(user.getId());
            
            result.add(AdminGamificationStatsResponse.UserActivitySummary.builder()
                    .userId(user.getId())
                    .userName(displayName)
                    .fullName(displayName)
                    .userAvatar(user.getAvatarUrl())
                    .avatarMediaUrl(avatarUrl)
                    .gamesPlayed(gamesPlayed.intValue())
                    .badgesEarned(badgeCount.intValue())
                    .totalCoins(wallet != null ? wallet.getTotalCoins() : 0)
                    .totalXp(wallet != null ? wallet.getTotalXp() : 0)
                    .currentStreak(wallet != null ? wallet.getStreakDays() : 0)
                    .lastActivityAt(wallet != null ? wallet.getLastActivityDate() : null)
                    .build());
        }
        
        return result;
    }

    @Override
    public List<AdminGamificationStatsResponse.GameStatsSummary> getGameStats() {
        List<GamificationMiniGameDefinition> games = gameDefRepository.findAll();
        List<AdminGamificationStatsResponse.GameStatsSummary> stats = new ArrayList<>();
        
        for (GamificationMiniGameDefinition game : games) {
            Long totalSessions = gameSessionRepository.countByGameDefId(game.getGameDefId());
            Long completedSessions = gameSessionRepository.countByGameDefIdAndStatus(
                    game.getGameDefId(), "COMPLETED");
            Long failedSessions = gameSessionRepository.countByGameDefIdAndStatus(
                    game.getGameDefId(), "FAILED");
            Long totalCoins = gameSessionRepository.sumCoinsEarnedByGameDefinitionId(game.getGameDefId());
            Long totalXp = gameSessionRepository.sumXpEarnedByGameDefinitionId(game.getGameDefId());
            Double avgScore = gameSessionRepository.avgScoreByGameDefinitionId(game.getGameDefId());
            Long uniquePlayers = gameSessionRepository.countDistinctUsersByGameDefinitionId(game.getGameDefId());
            
            double completionRate = totalSessions > 0 ? 
                    (completedSessions.doubleValue() / totalSessions.doubleValue()) * 100 : 0;
            
            stats.add(AdminGamificationStatsResponse.GameStatsSummary.builder()
                    .gameDefId(game.getGameDefId())
                    .gameKey(game.getGameKey())
                    .gameTitle(game.getGameTitle())
                    .totalSessions(totalSessions)
                    .completedSessions(completedSessions)
                    .failedSessions(failedSessions)
                    .totalCoinsAwarded(totalCoins != null ? totalCoins : 0L)
                    .totalXpAwarded(totalXp != null ? totalXp : 0L)
                    .averageScore(avgScore != null ? avgScore : 0.0)
                    .completionRate(completionRate)
                    .uniquePlayers(uniquePlayers)
                    .build());
        }
        
        return stats;
    }

    @Override
    public List<AdminGamificationStatsResponse.BadgeStatsSummary> getBadgeStats() {
        List<GamificationBadgeDefinition> badges = badgeDefRepository.findAll();
        long totalUsers = userRepository.count();
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        
        List<AdminGamificationStatsResponse.BadgeStatsSummary> stats = new ArrayList<>();
        
        for (GamificationBadgeDefinition badge : badges) {
            Long totalAwarded = userBadgeRepository.countByBadgeDefId(badge.getBadgeDefId());
            Long awardedThisWeek = userBadgeRepository.countByBadgeDefinitionIdAndEarnedAtAfter(
                    badge.getBadgeDefId(), weekAgo);
            double awardRate = totalUsers > 0 ? 
                    (totalAwarded.doubleValue() / totalUsers) * 100 : 0;
            
            stats.add(AdminGamificationStatsResponse.BadgeStatsSummary.builder()
                    .badgeDefId(badge.getBadgeDefId())
                    .badgeKey(badge.getBadgeKey())
                    .badgeTitle(badge.getBadgeTitle())
                    .badgeCategory(badge.getBadgeCategory())
                    .badgeRarity(badge.getBadgeRarity())
                    .totalAwarded(totalAwarded)
                    .awardedThisWeek(awardedThisWeek)
                    .awardRate(awardRate)
                    .build());
        }
        
        return stats;
    }

    @Override
    public UserActivityTrackingResponse getUserActivityTracking(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        GamificationUserWallet wallet = walletRepository.findByUserId(userId).orElse(null);
        Long badgeCount = userBadgeRepository.countByUserId(userId);
        Long gamesPlayed = gameSessionRepository.countByUserId(userId);
        Long gamesWon = gameSessionRepository.countByUserIdAndSessionStatus(
                userId, "COMPLETED");
        
        // Build activity counters
        UserActivityTrackingResponse.ActivityCounters counters = UserActivityTrackingResponse.ActivityCounters.builder()
                .gamesPlayed(gamesPlayed.intValue())
                .gamesWon(gamesWon.intValue())
                .badgesEarned(badgeCount.intValue())
                .totalCoinsEarned(wallet != null ? wallet.getEarnedCoins() : 0)
                .totalCoinsSpent(wallet != null ? wallet.getSpentCoins() : 0)
                .currentStreak(wallet != null ? wallet.getStreakDays() : 0)
                .longestStreak(wallet != null ? wallet.getStreakDays() : 0)
                // Other counters would need integration with course/community services
                .coursesEnrolled(0)
                .coursesCompleted(0)
                .lessonsCompleted(0)
                .quizzesCompleted(0)
                .postsCreated(0)
                .commentsWritten(0)
                .build();
        
        // Get badge achievements
        List<GamificationUserBadge> userBadges = userBadgeRepository.findByUserId(userId);
        List<UserActivityTrackingResponse.AchievementProgress> achievements = userBadges.stream()
                .map(ub -> UserActivityTrackingResponse.AchievementProgress.builder()
                        .achievementKey(ub.getBadgeDefinition().getBadgeKey())
                        .achievementTitle(ub.getBadgeDefinition().getBadgeTitle())
                        .achievementDescription(ub.getBadgeDefinition().getBadgeDescription())
                        .achievementIcon(ub.getBadgeDefinition().getBadgeIcon())
                        .category(ub.getBadgeDefinition().getBadgeCategory())
                        .currentValue(1)
                        .targetValue(1)
                        .progressPercent(100.0)
                        .isCompleted(true)
                        .completedAt(ub.getEarnedAt())
                        .coinReward(ub.getCoinsAwarded())
                        .xpReward(ub.getXpAwarded())
                        .build())
                .collect(Collectors.toList());
        
        // Get recent activities (last 10 game sessions)
        List<GamificationGameSession> recentSessions = gameSessionRepository.findTop10ByUserIdOrderByPlayedAtDesc(userId);
        List<UserActivityTrackingResponse.RecentActivity> recentActivities = recentSessions.stream()
                .map(session -> UserActivityTrackingResponse.RecentActivity.builder()
                        .activityType("GAME_SESSION")
                        .activityDescription("Played " + session.getGameDefinition().getGameTitle())
                        .entityType("game")
                        .entityId(session.getGameDefinition().getGameDefId())
                        .coinsEarned(session.getCoinsEarned() != null ? session.getCoinsEarned() : 0)
                        .xpEarned(session.getXpEarned() != null ? session.getXpEarned() : 0)
                        .occurredAt(session.getPlayedAt())
                        .build())
                .collect(Collectors.toList());
        
        UserProfile userProfile = userProfileRepository.findByUserId(userId).orElse(null);

        String displayName;
        if (userProfile != null && userProfile.getFullName() != null && !userProfile.getFullName().isEmpty()) {
            displayName = userProfile.getFullName();
        } else {
            String fName = user.getFirstName();
            String lName = user.getLastName();
            if (fName != null && lName != null) {
                displayName = fName + " " + lName;
            } else if (fName != null) {
                displayName = fName;
            } else if (lName != null) {
                displayName = lName;
            } else {
                displayName = user.getEmail().split("@")[0];
            }
        }

        String avatarUrl = null;
        if (userProfile != null && userProfile.getAvatarMedia() != null) {
            avatarUrl = userProfile.getAvatarMedia().getUrl();
        } else if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
            avatarUrl = user.getAvatarUrl();
        }

        return UserActivityTrackingResponse.builder()
                .userId(userId)
                .userName(displayName)
                .fullName(displayName)
                .userAvatar(user.getAvatarUrl())
                .avatarMediaUrl(avatarUrl)
                .counters(counters)
                .achievements(achievements)
                .recentActivities(recentActivities)
                .milestones(new ArrayList<>())
                .build();
    }

    @Override
    public Page<AdminGamificationStatsResponse.UserActivitySummary> getAllUserActivities(Pageable pageable) {
        Page<GamificationUserWallet> wallets = walletRepository.findAll(pageable);
        
        List<AdminGamificationStatsResponse.UserActivitySummary> summaries = wallets.getContent().stream()
                .map(this::mapToUserActivitySummary)
                .collect(Collectors.toList());
        
        return new PageImpl<>(summaries, pageable, wallets.getTotalElements());
    }

    @Override
    @Transactional
    public int bulkAwardBadges(String badgeKey) {
        // Implementation for bulk awarding badges based on criteria
        log.info("Bulk awarding badge: {}", badgeKey);
        // This would check all users against badge criteria and award
        return 0;
    }

    @Override
    @Transactional
    public int recalculateAchievements() {
        // Implementation for recalculating all achievements
        log.info("Recalculating all achievements");
        return 0;
    }

    private AdminGamificationStatsResponse.UserActivitySummary mapToUserActivitySummary(GamificationUserWallet wallet) {
        User user = wallet.getUser();
        Long badgeCount = userBadgeRepository.countByUserId(user.getId());
        Long gamesPlayed = gameSessionRepository.countByUserId(user.getId());
        
        UserProfile userProfile = userProfileRepository.findByUserId(user.getId()).orElse(null);

        String displayName;
        if (userProfile != null && userProfile.getFullName() != null && !userProfile.getFullName().isEmpty()) {
            displayName = userProfile.getFullName();
        } else {
            String fName = user.getFirstName();
            String lName = user.getLastName();
            if (fName != null && lName != null) {
                displayName = fName + " " + lName;
            } else if (fName != null) {
                displayName = fName;
            } else if (lName != null) {
                displayName = lName;
            } else {
                displayName = user.getEmail().split("@")[0];
            }
        }

        String avatarUrl = null;
        if (userProfile != null && userProfile.getAvatarMedia() != null) {
            avatarUrl = userProfile.getAvatarMedia().getUrl();
        } else if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
            avatarUrl = user.getAvatarUrl();
        }
        
        return AdminGamificationStatsResponse.UserActivitySummary.builder()
                .userId(user.getId())
                .userName(displayName)
                .fullName(displayName)
                .userAvatar(user.getAvatarUrl())
                .avatarMediaUrl(avatarUrl)
                .gamesPlayed(gamesPlayed.intValue())
                .badgesEarned(badgeCount.intValue())
                .totalCoins(wallet.getTotalCoins())
                .totalXp(wallet.getTotalXp())
                .currentStreak(wallet.getStreakDays())
                .lastActivityAt(wallet.getLastActivityDate())
                .build();
    }
}
