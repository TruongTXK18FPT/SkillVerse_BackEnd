package com.exe.skillverse_backend.gamification_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.gamification_service.dto.response.LeaderboardEntryResponse;
import com.exe.skillverse_backend.gamification_service.dto.response.LeaderboardResponse;
import com.exe.skillverse_backend.gamification_service.entity.GamificationLeaderboardSnapshot;
import com.exe.skillverse_backend.gamification_service.entity.GamificationUserWallet;
import com.exe.skillverse_backend.gamification_service.repository.GamificationLeaderboardSnapshotRepository;
import com.exe.skillverse_backend.gamification_service.repository.GamificationUserBadgeRepository;
import com.exe.skillverse_backend.gamification_service.repository.GamificationUserWalletRepository;
import com.exe.skillverse_backend.gamification_service.service.GamificationLeaderboardService;
import com.exe.skillverse_backend.gamification_service.service.DailyCheckInService;
import com.exe.skillverse_backend.skin_service.repository.UserSkinRepository;
import com.exe.skillverse_backend.skin_service.entity.UserSkin;
import com.exe.skillverse_backend.wallet_service.entity.Wallet;
import com.exe.skillverse_backend.wallet_service.repository.WalletRepository;
import com.exe.skillverse_backend.community_service.repository.PostRepository;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import com.exe.skillverse_backend.user_service.entity.UserProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GamificationLeaderboardServiceImpl implements GamificationLeaderboardService {

    private final GamificationLeaderboardSnapshotRepository leaderboardRepository;
    private final GamificationUserWalletRepository walletRepository;
    private final GamificationUserBadgeRepository badgeRepository;
    private final UserRepository userRepository;
    private final WalletRepository mainWalletRepository;
    private final com.exe.skillverse_backend.gamification_service.repository.GamificationActivityLogRepository activityRepository;
    private final UserSkinRepository userSkinRepository;
    private final PostRepository postRepository;
    private final UserProfileRepository userProfileRepository;
    private final DailyCheckInService dailyCheckInService;

    @Override
    @Transactional(readOnly = true)
    public LeaderboardResponse getLeaderboard(String period, String type, Long currentUserId, Pageable pageable) {
        log.info("Getting leaderboard for period: {}, type: {}, userId: {}", period, type, currentUserId);

        // Try to get from snapshot first
        Page<GamificationLeaderboardSnapshot> snapshotPage = leaderboardRepository.findLatestLeaderboard(period, type, pageable);

        if (snapshotPage.hasContent()) {
            return buildLeaderboardFromSnapshot(snapshotPage, period, type, currentUserId);
        }

        // Fallback to realtime calculation
        return calculateRealtimeLeaderboard(period, type, currentUserId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public LeaderboardEntryResponse getUserLeaderboardPosition(Long userId, String period, String type) {
        log.info("Getting user {} position for period: {}, type: {}", userId, period, type);

        // Try snapshot first
        Optional<GamificationLeaderboardSnapshot> snapshotOpt = leaderboardRepository.findUserLatestRank(userId, period, type);

        if (snapshotOpt.isPresent()) {
            return mapSnapshotToEntryResponse(snapshotOpt.get(), true);
        }

        // Calculate realtime position
        return calculateRealtimeUserPosition(userId, period, type);
    }

    @Override
    @Transactional
    public void updateLeaderboardSnapshot(String period, String type) {
        log.info("Updating leaderboard snapshot for period: {}, type: {}", period, type);

        LocalDateTime now = LocalDateTime.now();
        
        // 1. Prepare Data Sources
        Map<Long, Integer> contributionCounts = new HashMap<>();
        Map<Long, Integer> skinCounts = new HashMap<>();
        Map<Long, Integer> coinBalances = new HashMap<>();
        Map<Long, Integer> longestStreaks = new HashMap<>();
        
        // Community Counts (All Time)
        if ("community".equalsIgnoreCase(type)) {
             LocalDateTime since = LocalDateTime.of(2020, 1, 1, 0, 0);
             List<Object[]> counts = activityRepository.countVerifiedActivitiesByTypeSinceGroupedByUserId("CONTRIBUTION", since);
             for (Object[] row : counts) {
                 contributionCounts.put((Long) row[0], ((Number) row[1]).intValue());
             }
             List<Object[]> postCounts = postRepository.countPostsGroupedByUserId();
             for (Object[] row : postCounts) {
                 Long uId = (Long) row[0];
                 if (uId != null) {
                     Integer count = ((Number) row[1]).intValue();
                     contributionCounts.put(uId, contributionCounts.getOrDefault(uId, 0) + count);
                 }
             }
        }

        // Skin Counts
        if ("skins".equalsIgnoreCase(type) || "inventory".equalsIgnoreCase(type)) {
            List<UserSkin> allUserSkins = userSkinRepository.findAll();
            for (UserSkin us : allUserSkins) {
                if (us.getUser() != null) {
                    skinCounts.put(us.getUser().getId(), skinCounts.getOrDefault(us.getUser().getId(), 0) + 1);
                }
            }
        }

        // Coin Balances
        if ("coins".equalsIgnoreCase(type)) {
             List<Wallet> mainWallets = mainWalletRepository.findAll();
             for (Wallet w : mainWallets) {
                 if (w.getUser() != null) {
                     coinBalances.put(w.getUser().getId(), w.getCoinBalance() != null ? w.getCoinBalance().intValue() : 0);
                 }
             }
        }

        // Longest Streak from Daily Check-ins
        if ("streak".equalsIgnoreCase(type)) {
            List<GamificationUserWallet> allWallets = walletRepository.findAll();
            for (GamificationUserWallet wallet : allWallets) {
                try {
                    int longest = dailyCheckInService.calculateLongestStreak(wallet.getUserId());
                    longestStreaks.put(wallet.getUserId(), longest);
                } catch (Exception e) {
                    log.warn("Failed to calculate longest streak for user {}: {}", wallet.getUserId(), e.getMessage());
                    longestStreaks.put(wallet.getUserId(), 0);
                }
            }
        }

        // 2. Aggregate All Unique Users
        Set<Long> allUserIds = new HashSet<>();
        
        // From Gamification Wallets
        List<GamificationUserWallet> dbWallets = walletRepository.findAll();
        Map<Long, GamificationUserWallet> walletMap = dbWallets.stream()
            .collect(Collectors.toMap(GamificationUserWallet::getUserId, w -> w, (w1, w2) -> w1));
        allUserIds.addAll(walletMap.keySet());
        
        // From External Sources
        allUserIds.addAll(contributionCounts.keySet());
        allUserIds.addAll(skinCounts.keySet());
        allUserIds.addAll(coinBalances.keySet());
        allUserIds.addAll(longestStreaks.keySet());

        // 3. Build Unified Wallet List
        List<GamificationUserWallet> unifiedWallets = new ArrayList<>();
        for (Long userId : allUserIds) {
            GamificationUserWallet wallet = walletMap.get(userId);
            if (wallet == null) {
                wallet = new GamificationUserWallet();
                wallet.setUserId(userId);
                wallet.setTotalXp(0);
                wallet.setTotalCoins(0);
                wallet.setStreakDays(0);
            }
            unifiedWallets.add(wallet);
        }

        // 4. Sort
        List<GamificationUserWallet> sortedWallets = sortWalletsByType(unifiedWallets, type, contributionCounts, skinCounts, coinBalances, longestStreaks);

        // 5. Save Snapshots
        int rank = 1;
        for (GamificationUserWallet wallet : sortedWallets) {
            Integer scoreValue = getScoreByType(wallet, type, contributionCounts, skinCounts, coinBalances, longestStreaks);
            Integer badgesCount = walletMap.containsKey(wallet.getUserId()) ? 
                badgeRepository.countBadgesByUserId(wallet.getUserId()).intValue() : 0;
            Integer contributions = contributionCounts.getOrDefault(wallet.getUserId(), 0);
            Integer skins = skinCounts.getOrDefault(wallet.getUserId(), 0);
            Integer coins = coinBalances.getOrDefault(wallet.getUserId(), wallet.getTotalCoins());

            GamificationLeaderboardSnapshot snapshot = GamificationLeaderboardSnapshot.builder()
                    .userId(wallet.getUserId())
                    .user(userRepository.getReferenceById(wallet.getUserId()))
                    .leaderboardPeriod(period)
                    .leaderboardType(type)
                    .rankPosition(rank)
                    .scoreValue(scoreValue)
                    .totalCoins(coins)
                    .totalXp(wallet.getTotalXp())
                    .badgesCount(badgesCount)
                    .streakDays(wallet.getStreakDays())
                    .contributionsCount(contributions)
                    .skinsCount(skins)
                    .snapshotDate(now)
                    .build();

            leaderboardRepository.save(snapshot);
            rank++;
        }
        log.info("Updated leaderboard snapshot with {} entries", rank - 1);
    }

    @Override
    @Transactional(readOnly = true)
    public LeaderboardResponse calculateRealtimeLeaderboard(String period, String type, Long currentUserId, Pageable pageable) {
        log.info("Calculating realtime leaderboard for period: {}, type: {}", period, type);

        // 1. Prepare Data Sources - Always fetch all data for complete response
        Map<Long, Integer> contributionCounts = new HashMap<>();
        Map<Long, Integer> skinCounts = new HashMap<>();
        Map<Long, Integer> coinBalances = new HashMap<>();
        Map<Long, Integer> longestStreaks = new HashMap<>();

        // Community Counts (All Time) - Always fetch for response
        LocalDateTime since = LocalDateTime.of(2020, 1, 1, 0, 0);
        try {
            List<Object[]> counts = activityRepository.countVerifiedActivitiesByTypeSinceGroupedByUserId("CONTRIBUTION", since);
            for (Object[] row : counts) {
                if (row[0] != null) {
                    contributionCounts.put((Long) row[0], ((Number) row[1]).intValue());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch contribution counts: {}", e.getMessage());
        }
        
        try {
            List<Object[]> postCounts = postRepository.countPostsGroupedByUserId();
            for (Object[] row : postCounts) {
                Long uId = (Long) row[0];
                if (uId != null) {
                    Integer count = ((Number) row[1]).intValue();
                    contributionCounts.put(uId, contributionCounts.getOrDefault(uId, 0) + count);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch post counts: {}", e.getMessage());
        }

        // Skin Counts - Always fetch for response
        try {
            List<UserSkin> allUserSkins = userSkinRepository.findAll();
            for (UserSkin us : allUserSkins) {
                if (us.getUser() != null) {
                    skinCounts.put(us.getUser().getId(), skinCounts.getOrDefault(us.getUser().getId(), 0) + 1);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch skin counts: {}", e.getMessage());
        }

        // Coin Balances - Always fetch for response
        try {
            List<Wallet> mainWallets = mainWalletRepository.findAll();
            for (Wallet w : mainWallets) {
                if (w.getUser() != null) {
                    coinBalances.put(w.getUser().getId(), w.getCoinBalance() != null ? w.getCoinBalance().intValue() : 0);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch coin balances: {}", e.getMessage());
        }

        // Longest Streaks - Calculate from daily check-ins for each user
        try {
            // Get all user IDs that have gamification wallets
            List<GamificationUserWallet> allWallets = walletRepository.findAll();
            for (GamificationUserWallet wallet : allWallets) {
                try {
                    int longest = dailyCheckInService.calculateLongestStreak(wallet.getUserId());
                    if (longest > 0) {
                        longestStreaks.put(wallet.getUserId(), longest);
                    }
                } catch (Exception e) {
                    log.warn("Failed to calculate longest streak for user {}: {}", wallet.getUserId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch longest streaks: {}", e.getMessage());
        }

        // 2. Aggregate All Unique Users
        Set<Long> allUserIds = new HashSet<>();
        List<GamificationUserWallet> dbWallets = walletRepository.findAll();
        Map<Long, GamificationUserWallet> walletMap = dbWallets.stream()
            .collect(Collectors.toMap(GamificationUserWallet::getUserId, w -> w, (w1, w2) -> w1));
        allUserIds.addAll(walletMap.keySet());
        allUserIds.addAll(contributionCounts.keySet());
        allUserIds.addAll(skinCounts.keySet());
        allUserIds.addAll(coinBalances.keySet());
        
        // Add users with streak data
        allUserIds.addAll(longestStreaks.keySet());
        
        // IMPORTANT: Always include current user if authenticated
        if (currentUserId != null && currentUserId > 0) {
            allUserIds.add(currentUserId);
        }

        // 3. Build Unified Wallet List
        List<GamificationUserWallet> unifiedWallets = new ArrayList<>();
        for (Long userId : allUserIds) {
            GamificationUserWallet wallet = walletMap.get(userId);
            if (wallet == null) {
                wallet = new GamificationUserWallet();
                wallet.setUserId(userId);
                wallet.setTotalXp(0);
                wallet.setTotalCoins(0);
                wallet.setStreakDays(0);
                // Mark as transient/active for filtering if they have counts
                if (contributionCounts.containsKey(userId) || skinCounts.containsKey(userId) || coinBalances.containsKey(userId)) {
                    wallet.setUpdatedAt(LocalDateTime.now());
                }
            }
            unifiedWallets.add(wallet);
        }
        
        // 4. Filter by period if needed (skip for skins/community/inventory/streak/learning)
        // Learning (XP) and Streak are cumulative, so period filtering is not strictly applicable for "total" view
        List<GamificationUserWallet> filteredWallets = unifiedWallets;
        if (!"skins".equalsIgnoreCase(type) && !"community".equalsIgnoreCase(type) && !"inventory".equalsIgnoreCase(type)
                && !"streak".equalsIgnoreCase(type) && !"learning".equalsIgnoreCase(type)) {
             filteredWallets = filterWalletsByPeriod(unifiedWallets, period);
        }

        // 5. Sort by type
        List<GamificationUserWallet> sortedWallets = sortWalletsByType(filteredWallets, type, contributionCounts, skinCounts, coinBalances, longestStreaks);

        // 6. Build response entries
        List<LeaderboardEntryResponse> entries = new ArrayList<>();
        LeaderboardEntryResponse currentUserEntry = null;
        int currentUserRank = 0;

        for (int i = 0; i < sortedWallets.size(); i++) {
            GamificationUserWallet wallet = sortedWallets.get(i);
            int rank = i + 1;
            Long walletUserId = wallet.getUserId();
            boolean isCurrentUser = walletUserId != null && walletUserId.equals(currentUserId);
            
            Integer scoreValue = getScoreByType(wallet, type, contributionCounts, skinCounts, coinBalances, longestStreaks);
            Integer contributions = contributionCounts.getOrDefault(walletUserId, 0);
            Integer skins = skinCounts.getOrDefault(walletUserId, 0);
            Integer walletCoins = wallet.getTotalCoins() != null ? wallet.getTotalCoins() : 0;
            Integer coins = coinBalances.getOrDefault(walletUserId, walletCoins);

            LeaderboardEntryResponse entry = buildEntryFromWallet(wallet, rank, isCurrentUser, scoreValue, contributions, skins, coins);

            if (isCurrentUser) {
                currentUserEntry = entry;
                currentUserRank = rank;
            }

            // Only add to list if within page range
            if (pageable.isUnpaged()) {
                // If unpaged, add all entries
                entries.add(entry);
            } else {
                int start = pageable.getPageNumber() * pageable.getPageSize();
                int end = start + pageable.getPageSize();
                if (i >= start && i < end) {
                    entries.add(entry);
                }
            }
        }

        // Calculate coins to next rank
        Integer coinsToNextRank = 0;
        if (currentUserRank > 1 && currentUserEntry != null) {
            GamificationUserWallet nextRankWallet = sortedWallets.get(currentUserRank - 2);
            coinsToNextRank = getScoreByType(nextRankWallet, type, contributionCounts, skinCounts, coinBalances, longestStreaks) - currentUserEntry.getScoreValue();
        }

        return LeaderboardResponse.builder()
                .leaderboardPeriod(period)
                .leaderboardType(type)
                .currentUserPosition(currentUserEntry)
                .topEntries(entries)
                .totalParticipants(sortedWallets.size())
                .coinsToNextRank(Math.max(0, coinsToNextRank))
                .build();
    }

    // === Private Helper Methods ===

    private LeaderboardResponse buildLeaderboardFromSnapshot(Page<GamificationLeaderboardSnapshot> snapshotPage,
                                                              String period, String type, Long currentUserId) {
        List<LeaderboardEntryResponse> entries = snapshotPage.getContent().stream()
                .map(s -> mapSnapshotToEntryResponse(s, s.getUserId().equals(currentUserId)))
                .collect(Collectors.toList());

        // Get current user position
        LeaderboardEntryResponse currentUserEntry = entries.stream()
                .filter(LeaderboardEntryResponse::getIsCurrentUser)
                .findFirst()
                .orElseGet(() -> getUserLeaderboardPosition(currentUserId, period, type));

        // Calculate coins to next rank
        Integer coinsToNextRank = 0;
        if (currentUserEntry != null && currentUserEntry.getRankPosition() > 1) {
             // Can't easily calculate without full list, but we can try if the previous rank is in the page
             Optional<LeaderboardEntryResponse> prev = entries.stream()
                     .filter(e -> e.getRankPosition() == currentUserEntry.getRankPosition() - 1)
                     .findFirst();
             if (prev.isPresent()) {
                 coinsToNextRank = prev.get().getScoreValue() - currentUserEntry.getScoreValue();
             }
        }

        return LeaderboardResponse.builder()
                .leaderboardPeriod(period)
                .leaderboardType(type)
                .currentUserPosition(currentUserEntry)
                .topEntries(entries)
                .totalParticipants((int) snapshotPage.getTotalElements())
                .coinsToNextRank(Math.max(0, coinsToNextRank))
                .build();
    }

    private LeaderboardEntryResponse mapSnapshotToEntryResponse(GamificationLeaderboardSnapshot snapshot, boolean isCurrentUser) {
        User user = snapshot.getUser();
        String userName = getUserName(user);
        String avatar = getUserAvatar(user);

        return LeaderboardEntryResponse.builder()
                .userId(snapshot.getUserId())
                .userName(userName)
                .userAvatar(avatar)
                .rankPosition(snapshot.getRankPosition())
                .scoreValue(snapshot.getScoreValue())
                .totalCoins(snapshot.getTotalCoins())
                .totalXp(snapshot.getTotalXp())
                .badgesCount(snapshot.getBadgesCount())
                .streakDays(snapshot.getStreakDays())
                .contributionsCount(snapshot.getContributionsCount())
                .skinsCount(snapshot.getSkinsCount())
                .isCurrentUser(isCurrentUser)
                .rankChange(0)
                .build();
    }

    private LeaderboardEntryResponse calculateRealtimeUserPosition(Long userId, String period, String type) {
        // Reuse calculateRealtimeLeaderboard but just find the user
        // This is inefficient for just one user but ensures consistency.
        // For optimization, we would need a dedicated method that doesn't build the full response list.
        // Given the requirement to sort correctly, calculating the full sorted list is unavoidable unless we use DB queries.
        
        LeaderboardResponse fullLeaderboard = calculateRealtimeLeaderboard(period, type, userId, Pageable.unpaged());
        
        if (fullLeaderboard.getCurrentUserPosition() != null) {
            return fullLeaderboard.getCurrentUserPosition();
        }

        // User not found in leaderboard, return default entry
        return LeaderboardEntryResponse.builder()
                .userId(userId)
                .userName("Unknown")
                .rankPosition(0)
                .scoreValue(0)
                .totalCoins(0)
                .totalXp(0)
                .badgesCount(0)
                .streakDays(0)
                .contributionsCount(0)
                .skinsCount(0)
                .isCurrentUser(true)
                .rankChange(0)
                .build();
    }

    private String getUserName(User user) {
        if (user == null) return "Unknown User";
        
        // Try to fetch UserProfile for fullName
        try {
            Optional<UserProfile> profileOpt = userProfileRepository.findByUserId(user.getId());
            if (profileOpt.isPresent() && profileOpt.get().getFullName() != null && !profileOpt.get().getFullName().trim().isEmpty()) {
                return profileOpt.get().getFullName();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch profile for user {}", user.getId());
        }

        String first = user.getFirstName();
        String last = user.getLastName();
        
        if (first == null && last == null) {
            return user.getEmail() != null ? user.getEmail().split("@")[0] : "User " + user.getId();
        }
        
        StringBuilder fullName = new StringBuilder();
        if (first != null && !first.trim().isEmpty() && !first.equalsIgnoreCase("null")) {
            fullName.append(first);
        }
        
        if (last != null && !last.trim().isEmpty() && !last.equalsIgnoreCase("null")) {
            if (fullName.length() > 0) fullName.append(" ");
            fullName.append(last);
        }
        
        String result = fullName.toString().trim();
        return result.isEmpty() ? (user.getEmail() != null ? user.getEmail().split("@")[0] : "User " + user.getId()) : result;
    }

    private String getUserAvatar(User user) {
        if (user == null) return null;
        
        // Try to fetch UserProfile for avatarMedia
        try {
            Optional<UserProfile> profileOpt = userProfileRepository.findByUserId(user.getId());
            if (profileOpt.isPresent() && profileOpt.get().getAvatarMedia() != null) {
                return profileOpt.get().getAvatarMedia().getUrl();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch profile avatar for user {}", user.getId());
        }
        
        return user.getAvatarUrl();
    }

    private LeaderboardEntryResponse buildEntryFromWallet(GamificationUserWallet wallet, int rank, boolean isCurrentUser, Integer scoreValue, Integer contributionsCount, Integer skinsCount, Integer currentCoins) {
        Long userId = wallet.getUserId();
        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        String userName = getUserName(user);
        String avatar = getUserAvatar(user);
        Long badgesCountLong = userId != null ? badgeRepository.countBadgesByUserId(userId) : 0L;
        Integer badgesCount = badgesCountLong != null ? badgesCountLong.intValue() : 0;

        Integer totalCoins = currentCoins != null ? currentCoins : (wallet.getTotalCoins() != null ? wallet.getTotalCoins() : 0);
        Integer totalXp = wallet.getTotalXp() != null ? wallet.getTotalXp() : 0;
        Integer streakDays = wallet.getStreakDays() != null ? wallet.getStreakDays() : 0;

        return LeaderboardEntryResponse.builder()
                .userId(userId)
                .userName(userName)
                .userAvatar(avatar)
                .rankPosition(rank)
                .scoreValue(scoreValue != null ? scoreValue : 0)
                .totalCoins(totalCoins)
                .totalXp(totalXp)
                .badgesCount(badgesCount)
                .streakDays(streakDays)
                .contributionsCount(contributionsCount != null ? contributionsCount : 0)
                .skinsCount(skinsCount != null ? skinsCount : 0)
                .isCurrentUser(isCurrentUser)
                .rankChange(0)
                .build();
    }

    private LocalDateTime getSinceDate(String period) {
        switch (period.toLowerCase()) {
            case "week":
                return LocalDateTime.now().minus(7, ChronoUnit.DAYS);
            case "month":
                return LocalDateTime.now().minus(30, ChronoUnit.DAYS);
            case "all":
            default:
                return LocalDateTime.of(2020, 1, 1, 0, 0);
        }
    }

    private List<GamificationUserWallet> filterWalletsByPeriod(List<GamificationUserWallet> wallets, String period) {
        if ("all".equalsIgnoreCase(period)) {
            return wallets;
        }
        
        LocalDateTime since = getSinceDate(period);

        return wallets.stream()
                .filter(w -> {
                    LocalDateTime activityDate = w.getLastActivityDate() != null ? w.getLastActivityDate() : w.getUpdatedAt();
                    return activityDate != null && activityDate.isAfter(since);
                })
                .collect(Collectors.toList());
    }

    private List<GamificationUserWallet> sortWalletsByType(List<GamificationUserWallet> wallets, String type, Map<Long, Integer> contributionCounts, Map<Long, Integer> skinCounts, Map<Long, Integer> coinBalances, Map<Long, Integer> longestStreaks) {
        Comparator<GamificationUserWallet> comparator;

        switch (type.toLowerCase()) {
            case "learning":
                comparator = Comparator.comparing(
                    w -> w.getTotalXp() != null ? w.getTotalXp() : 0, 
                    Comparator.reverseOrder());
                break;
            case "community":
                comparator = (w1, w2) -> {
                    Integer c1 = contributionCounts.getOrDefault(w1.getUserId(), 0);
                    Integer c2 = contributionCounts.getOrDefault(w2.getUserId(), 0);
                    int result = c2.compareTo(c1);
                    if (result == 0) {
                         Integer coin1 = coinBalances.getOrDefault(w1.getUserId(), w1.getTotalCoins() != null ? w1.getTotalCoins() : 0);
                         Integer coin2 = coinBalances.getOrDefault(w2.getUserId(), w2.getTotalCoins() != null ? w2.getTotalCoins() : 0);
                         return coin2.compareTo(coin1);
                    }
                    return result;
                };
                break;
            case "skins":
            case "inventory":
                comparator = (w1, w2) -> {
                    Integer s1 = skinCounts.getOrDefault(w1.getUserId(), 0);
                    Integer s2 = skinCounts.getOrDefault(w2.getUserId(), 0);
                    int result = s2.compareTo(s1);
                    if (result == 0) {
                         Integer coin1 = coinBalances.getOrDefault(w1.getUserId(), w1.getTotalCoins() != null ? w1.getTotalCoins() : 0);
                         Integer coin2 = coinBalances.getOrDefault(w2.getUserId(), w2.getTotalCoins() != null ? w2.getTotalCoins() : 0);
                         return coin2.compareTo(coin1);
                    }
                    return result;
                };
                break;
            case "coins":
                comparator = (w1, w2) -> {
                    Integer coin1 = coinBalances.getOrDefault(w1.getUserId(), w1.getTotalCoins() != null ? w1.getTotalCoins() : 0);
                    Integer coin2 = coinBalances.getOrDefault(w2.getUserId(), w2.getTotalCoins() != null ? w2.getTotalCoins() : 0);
                    return coin2.compareTo(coin1);
                };
                break;
            case "streak":
                 comparator = (w1, w2) -> {
                     Integer s1 = longestStreaks.getOrDefault(w1.getUserId(), w1.getStreakDays() != null ? w1.getStreakDays() : 0);
                     Integer s2 = longestStreaks.getOrDefault(w2.getUserId(), w2.getStreakDays() != null ? w2.getStreakDays() : 0);
                     return s2.compareTo(s1);
                 };
                 break;
            default: // fallback
                 comparator = Comparator.comparing(
                    w -> w.getTotalCoins() != null ? w.getTotalCoins() : 0, 
                    Comparator.reverseOrder());
        }

        return wallets.stream()
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    private Integer getScoreByType(GamificationUserWallet wallet, String type, Map<Long, Integer> contributionCounts, Map<Long, Integer> skinCounts, Map<Long, Integer> coinBalances, Map<Long, Integer> longestStreaks) {
        Integer defaultCoins = wallet.getTotalCoins() != null ? wallet.getTotalCoins() : 0;
        switch (type.toLowerCase()) {
            case "learning":
                return wallet.getTotalXp() != null ? wallet.getTotalXp() : 0;
            case "community":
                return contributionCounts.getOrDefault(wallet.getUserId(), 0);
            case "skins":
            case "inventory":
                return skinCounts.getOrDefault(wallet.getUserId(), 0);
            case "coins":
                return coinBalances.getOrDefault(wallet.getUserId(), defaultCoins);
            case "streak":
                // Return longest streak from pre-calculated map
                return longestStreaks.getOrDefault(wallet.getUserId(), wallet.getStreakDays() != null ? wallet.getStreakDays() : 0);
            default:
                return defaultCoins;
        }
    }
}
