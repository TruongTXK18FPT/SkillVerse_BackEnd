package com.exe.skillverse_backend.gamification_service.service.impl;

import com.exe.skillverse_backend.gamification_service.dto.request.BadgeDefinitionRequest;
import com.exe.skillverse_backend.gamification_service.dto.response.BadgeDefinitionResponse;
import com.exe.skillverse_backend.gamification_service.dto.response.UserBadgeResponse;
import com.exe.skillverse_backend.gamification_service.service.GamificationBadgeService;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.exe.skillverse_backend.gamification_service.entity.GamificationBadgeDefinition;
import com.exe.skillverse_backend.gamification_service.entity.GamificationUserBadge;
import com.exe.skillverse_backend.gamification_service.repository.GamificationActivityLogRepository;
import com.exe.skillverse_backend.gamification_service.repository.GamificationBadgeDefinitionRepository;
import com.exe.skillverse_backend.gamification_service.repository.GamificationUserBadgeRepository;
import com.exe.skillverse_backend.gamification_service.repository.GamificationUserWalletRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class GamificationBadgeServiceImpl implements GamificationBadgeService {

    private final GamificationBadgeDefinitionRepository badgeDefRepository;
    private final GamificationUserBadgeRepository userBadgeRepository;
    private final WalletService walletService;
    private final GamificationActivityLogRepository activityRepository;
    private final GamificationUserWalletRepository gamificationWalletRepository;

    @Override
    @Transactional(readOnly = true)
    public List<BadgeDefinitionResponse> getAllBadgeDefinitions() {
        return badgeDefRepository.findAll().stream()
                .map(this::mapToBadgeDefResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BadgeDefinitionResponse> getActiveBadgeDefinitions() {
        return badgeDefRepository.findAllActiveOrderedByDisplayAndRarity().stream()
                .map(this::mapToBadgeDefResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BadgeDefinitionResponse> getBadgesByCategory(String category) {
        return badgeDefRepository.findByIsActiveTrueAndBadgeCategory(category).stream()
                .map(this::mapToBadgeDefResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserBadgeResponse> getUserBadges(Long userId, String category) {
        List<GamificationBadgeDefinition> allBadges = category == null || category.equals("all")
                ? badgeDefRepository.findByIsActiveTrue()
                : badgeDefRepository.findByIsActiveTrueAndBadgeCategory(category);

        List<GamificationUserBadge> earnedBadges = category == null || category.equals("all")
                ? userBadgeRepository.findByUserIdOrderByEarnedAtDesc(userId)
                : userBadgeRepository.findByUserIdAndBadgeCategory(userId, category);

        return allBadges.stream().map(badgeDef -> {
            UserBadgeResponse response = new UserBadgeResponse();
            response.setBadgeDefinition(mapToBadgeDefResponse(badgeDef));
            
            earnedBadges.stream()
                    .filter(ub -> ub.getBadgeDefId().equals(badgeDef.getBadgeDefId()))
                    .findFirst()
                    .ifPresentOrElse(
                            userBadge -> {
                                response.setUserBadgeId(userBadge.getUserBadgeId());
                                response.setUserId(userId);
                                response.setEarnedAt(userBadge.getEarnedAt());
                                response.setCoinsAwarded(userBadge.getCoinsAwarded());
                                response.setXpAwarded(userBadge.getXpAwarded());
                                response.setProgressData(userBadge.getProgressData());
                                response.setUnlocked(true);
                            },
                            () -> response.setUnlocked(false)
                    );
            
            return response;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<UserBadgeResponse> checkAndAwardBadges(Long userId) {
        List<UserBadgeResponse> newlyAwarded = new ArrayList<>();
        List<GamificationBadgeDefinition> allBadges = badgeDefRepository.findByIsActiveTrue();

        for (GamificationBadgeDefinition badge : allBadges) {
            if (!userBadgeRepository.existsByUserIdAndBadgeDefId(userId, badge.getBadgeDefId())) {
                if (checkBadgeCriteria(userId, badge)) {
                    UserBadgeResponse awarded = awardBadgeInternal(userId, badge);
                    newlyAwarded.add(awarded);
                }
            }
        }

        return newlyAwarded;
    }

    @Override
    @Transactional
    public UserBadgeResponse awardBadge(Long userId, String badgeKey) {
        GamificationBadgeDefinition badge = badgeDefRepository.findByBadgeKey(badgeKey)
                .orElseThrow(() -> new RuntimeException("Badge not found: " + badgeKey));

        if (userBadgeRepository.existsByUserIdAndBadgeDefId(userId, badge.getBadgeDefId())) {
            throw new RuntimeException("Badge already awarded to user");
        }

        return awardBadgeInternal(userId, badge);
    }

    @Override
    @Transactional
    public BadgeDefinitionResponse createBadgeDefinition(BadgeDefinitionRequest request, Long adminId) {
        if (badgeDefRepository.existsByBadgeKey(request.getBadgeKey())) {
            throw new RuntimeException("Badge key already exists: " + request.getBadgeKey());
        }

        GamificationBadgeDefinition badge = GamificationBadgeDefinition.builder()
                .badgeKey(request.getBadgeKey())
                .badgeTitle(request.getBadgeTitle())
                .badgeDescription(request.getBadgeDescription())
                .badgeIcon(request.getBadgeIcon())
                .badgeCategory(request.getBadgeCategory())
                .badgeRarity(request.getBadgeRarity())
                .criteriaDescription(request.getCriteriaDescription())
                .criteriaConfig(request.getCriteriaConfig())
                .coinReward(request.getCoinReward())
                .xpReward(request.getXpReward())
                .isActive(request.getIsActive())
                .displayOrder(request.getDisplayOrder())
                .createdByAdminId(adminId)
                .build();

        badge = badgeDefRepository.save(badge);
        log.info("Created badge: {} by admin {}", badge.getBadgeKey(), adminId);

        return mapToBadgeDefResponse(badge);
    }

    @Override
    @Transactional
    public BadgeDefinitionResponse updateBadgeDefinition(Long badgeDefId, BadgeDefinitionRequest request) {
        GamificationBadgeDefinition badge = badgeDefRepository.findById(badgeDefId)
                .orElseThrow(() -> new RuntimeException("Badge not found"));

        badge.setBadgeTitle(request.getBadgeTitle());
        badge.setBadgeDescription(request.getBadgeDescription());
        badge.setBadgeIcon(request.getBadgeIcon());
        badge.setBadgeCategory(request.getBadgeCategory());
        badge.setBadgeRarity(request.getBadgeRarity());
        badge.setCriteriaDescription(request.getCriteriaDescription());
        badge.setCriteriaConfig(request.getCriteriaConfig());
        badge.setCoinReward(request.getCoinReward());
        badge.setXpReward(request.getXpReward());
        badge.setIsActive(request.getIsActive());
        badge.setDisplayOrder(request.getDisplayOrder());

        badge = badgeDefRepository.save(badge);
        return mapToBadgeDefResponse(badge);
    }

    @Override
    @Transactional
    public void deleteBadgeDefinition(Long badgeDefId) {
        badgeDefRepository.deleteById(badgeDefId);
        log.info("Deleted badge definition: {}", badgeDefId);
    }

    @Override
    @Transactional(readOnly = true)
    public BadgeDefinitionResponse getBadgeByKey(String badgeKey) {
        return badgeDefRepository.findByBadgeKey(badgeKey)
                .map(this::mapToBadgeDefResponse)
                .orElseThrow(() -> new RuntimeException("Badge not found: " + badgeKey));
    }

    // Helper methods
    private boolean checkBadgeCriteria(Long userId, GamificationBadgeDefinition badge) {
        // Implementation of comprehensive criteria checking
        // Check based on badge key first for hardcoded logic
        switch (badge.getBadgeKey()) {
            case "coin-collector":
                return walletService.getWalletByUserId(userId).getCoinBalance() >= 1000;

            case "coin-master": // Higher tier
                return walletService.getWalletByUserId(userId).getCoinBalance() >= 5000;
                        
            case "streak-warrior":
                return gamificationWalletRepository.findByUserId(userId)
                        .map(w -> w.getStreakDays() >= 30)
                        .orElse(false);

            case "streak-master": // Higher tier
                return gamificationWalletRepository.findByUserId(userId)
                        .map(w -> w.getStreakDays() >= 100)
                        .orElse(false);

            case "speed-learner":
                // Check if user completed 5 lessons in one day
                LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
                Long lessonsToday = activityRepository.countVerifiedActivitiesByTypeSince(
                        userId, "STUDY", startOfDay);
                return lessonsToday >= 5;

            case "quiz-master":
                // Check if user has passed 3 quizzes (assuming verified QUIZ activity means passed)
                Long quizzesPassed = activityRepository.countVerifiedActivitiesByTypeSince(
                        userId, "QUIZ", LocalDateTime.MIN); // All time
                return quizzesPassed >= 3;

            case "helper-hero":
                Long contributions = activityRepository.countVerifiedActivitiesByTypeSince(
                        userId, "CONTRIBUTION", LocalDateTime.MIN);
                return contributions >= 50;

            case "event-enthusiast":
                // Check event participation
                Long events = activityRepository.countVerifiedActivitiesByTypeSince(
                        userId, "EVENT", LocalDateTime.MIN);
                return events >= 10;
                
            case "early-adopter":
                // Example: Check account creation date or ID
                return userId < 100; // First 100 users
                        
            default:
                // If no specific logic, check if criteriaConfig has auto-check rules
                // This is a placeholder for dynamic rule engine integration
                return false; 
        }
    }

    private UserBadgeResponse awardBadgeInternal(Long userId, GamificationBadgeDefinition badge) {
        GamificationUserBadge userBadge = GamificationUserBadge.builder()
                .userId(userId)
                .badgeDefId(badge.getBadgeDefId())
                .coinsAwarded(badge.getCoinReward())
                .xpAwarded(badge.getXpReward())
                .build();

        userBadge = userBadgeRepository.save(userBadge);

        // Award coins and XP
        if (badge.getCoinReward() > 0 || badge.getXpReward() > 0) {
            walletService.addCoins(
                    userId,
                    badge.getCoinReward().longValue(),
                    WalletTransaction.TransactionType.REWARD_ACHIEVEMENT,
                    "Nhận huy hiệu: " + badge.getBadgeTitle() + " (XP: " + badge.getXpReward() + ")",
                    "BADGE",
                    badge.getBadgeDefId().toString()
            );
        }

        log.info("Awarded badge {} to user {}", badge.getBadgeKey(), userId);

        return mapToUserBadgeResponse(userBadge, badge);
    }

    private BadgeDefinitionResponse mapToBadgeDefResponse(GamificationBadgeDefinition badge) {
        return BadgeDefinitionResponse.builder()
                .badgeDefId(badge.getBadgeDefId())
                .badgeKey(badge.getBadgeKey())
                .badgeTitle(badge.getBadgeTitle())
                .badgeDescription(badge.getBadgeDescription())
                .badgeIcon(badge.getBadgeIcon())
                .badgeCategory(badge.getBadgeCategory())
                .badgeRarity(badge.getBadgeRarity())
                .criteriaDescription(badge.getCriteriaDescription())
                .coinReward(badge.getCoinReward())
                .xpReward(badge.getXpReward())
                .isActive(badge.getIsActive())
                .displayOrder(badge.getDisplayOrder())
                .createdAt(badge.getCreatedAt())
                .updatedAt(badge.getUpdatedAt())
                .build();
    }

    private UserBadgeResponse mapToUserBadgeResponse(GamificationUserBadge userBadge, 
                                                      GamificationBadgeDefinition badge) {
        return UserBadgeResponse.builder()
                .userBadgeId(userBadge.getUserBadgeId())
                .userId(userBadge.getUserId())
                .badgeDefinition(mapToBadgeDefResponse(badge))
                .earnedAt(userBadge.getEarnedAt())
                .coinsAwarded(userBadge.getCoinsAwarded())
                .xpAwarded(userBadge.getXpAwarded())
                .progressData(userBadge.getProgressData())
                .unlocked(true)
                .build();
    }
}
