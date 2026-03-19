package com.exe.skillverse_backend.gamification_service.service.impl;

import com.exe.skillverse_backend.gamification_service.dto.request.CompleteGameSessionRequest;
import com.exe.skillverse_backend.gamification_service.dto.request.MiniGameDefinitionRequest;
import com.exe.skillverse_backend.gamification_service.dto.request.StartGameSessionRequest;
import com.exe.skillverse_backend.gamification_service.dto.response.GameSessionResponse;
import com.exe.skillverse_backend.gamification_service.dto.response.MiniGameDefinitionResponse;
import com.exe.skillverse_backend.gamification_service.service.GamificationMiniGameService;
import com.exe.skillverse_backend.gamification_service.service.GamificationWalletService;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.exe.skillverse_backend.gamification_service.entity.GamificationGameSession;
import com.exe.skillverse_backend.gamification_service.entity.GamificationMiniGameDefinition;
import com.exe.skillverse_backend.gamification_service.repository.GamificationGameSessionRepository;
import com.exe.skillverse_backend.gamification_service.repository.GamificationMiniGameDefinitionRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class GamificationMiniGameServiceImpl implements GamificationMiniGameService {

    private final GamificationMiniGameDefinitionRepository gameDefRepository;
    private final GamificationGameSessionRepository gameSessionRepository;
    private final WalletService walletService;
    private final GamificationWalletService gamificationWalletService;

    @Override
    @Transactional(readOnly = true)
    public List<MiniGameDefinitionResponse> getAvailableGames(Long userId, String userPlan) {
        List<GamificationMiniGameDefinition> games = userPlan == null || userPlan.equals("free")
                ? gameDefRepository.findByIsActiveTrueAndIsPremiumOnlyFalse()
                : gameDefRepository.findAvailableGamesForUser(userPlan);

        return games.stream()
                .map(game -> mapToGameResponse(game, userId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public MiniGameDefinitionResponse getGameDefinition(String gameKey, Long userId) {
        GamificationMiniGameDefinition game = gameDefRepository.findByGameKey(gameKey)
                .orElseThrow(() -> new RuntimeException("Game not found: " + gameKey));
        return mapToGameResponse(game, userId);
    }

    @Override
    @Transactional
    public GameSessionResponse startGameSession(Long userId, StartGameSessionRequest request) {
        GamificationMiniGameDefinition game = gameDefRepository.findByGameKey(request.getGameKey())
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (!canPlayGame(userId, request.getGameKey())) {
            throw new RuntimeException("Cannot play game now due to cooldown or daily limit");
        }

        GamificationGameSession session = GamificationGameSession.builder()
                .userId(userId)
                .gameDefId(game.getGameDefId())
                .sessionStatus("IN_PROGRESS")
                .sessionData(request.getSessionMetadata())
                .build();

        session = gameSessionRepository.save(session);
        log.info("Started game session {} for user {} playing {}", session.getSessionId(), userId, game.getGameTitle());

        return mapToSessionResponse(session);
    }

    @Override
    @Transactional
    public GameSessionResponse completeGameSession(Long userId, CompleteGameSessionRequest request) {
        GamificationGameSession session = gameSessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (!session.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized access to session");
        }

        if (!"IN_PROGRESS".equals(session.getSessionStatus())) {
            // Idempotency: if already completed, just return current state
            return mapToSessionResponse(session);
        }

        GamificationMiniGameDefinition game = session.getGameDefinition();

        // Calculate coins earned
        Integer coinsEarned = calculateCoinsForSession(game, request.getScoreAchieved());
        Integer xpEarned = game.getXpReward();

        session.setSessionStatus(request.getSessionStatus());
        session.setScoreAchieved(request.getScoreAchieved());
        session.setCoinsEarned(coinsEarned);
        session.setXpEarned(xpEarned);
        session.setCompletedAt(LocalDateTime.now());
        session.setDurationSeconds(request.getDurationSeconds());
        session.setSessionData(request.getSessionData());
        session.setVerificationData(request.getVerificationData());
        session.setIsVerified(true); // TODO: Implement anti-cheat verification

        session = gameSessionRepository.save(session);

        // Award coins if completed successfully
        if ("COMPLETED".equals(request.getSessionStatus()) && coinsEarned > 0) {
            gamificationWalletService.awardCoins(
                    userId,
                    coinsEarned,
                    xpEarned,
                    "MINIGAME",
                    game.getGameDefId(),
                    "Hoàn thành game: " + game.getGameTitle()
            );
            // Update streak for playing games
            gamificationWalletService.updateUserStreak(userId);
        }

        log.info("Completed session {} - Status: {}, Coins: {}", session.getSessionId(), 
                request.getSessionStatus(), coinsEarned);

        return mapToSessionResponse(session);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GameSessionResponse> getUserGameHistory(Long userId, Pageable pageable) {
        return gameSessionRepository.findByUserIdOrderByPlayedAtDesc(userId, pageable)
                .map(this::mapToSessionResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canPlayGame(Long userId, String gameKey) {
        GamificationMiniGameDefinition game = gameDefRepository.findByGameKey(gameKey)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        // Check last played time for cooldown
        var lastSession = gameSessionRepository.findLastSessionByUserAndGame(userId, game.getGameDefId());
        if (lastSession.isPresent()) {
            LocalDateTime lastPlayed = lastSession.get().getPlayedAt();
            long minutesSinceLastPlay = ChronoUnit.MINUTES.between(lastPlayed, LocalDateTime.now());
            
            if (minutesSinceLastPlay < game.getCooldownMinutes()) {
                return false;
            }
        }

        // Check daily play limit
        if (game.getMaxPlaysPerDay() != null) {
            LocalDateTime startOfDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
            Long playsToday = gameSessionRepository.countCompletedSessionsSince(userId, game.getGameDefId(), startOfDay);
            
            if (playsToday >= game.getMaxPlaysPerDay()) {
                return false;
            }
        }

        // Check daily coin limit
        if (game.getMaxCoinsPerDay() != null) {
            LocalDateTime startOfDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
            Integer coinsEarnedToday = gameSessionRepository.sumCoinsEarnedFromGameSince(userId, game.getGameDefId(), startOfDay);
            
            if (coinsEarnedToday >= game.getMaxCoinsPerDay()) {
                return false;
            }
        }

        return true;
    }

    @Override
    @Transactional
    public MiniGameDefinitionResponse createGameDefinition(MiniGameDefinitionRequest request, Long adminId) {
        if (gameDefRepository.existsByGameKey(request.getGameKey())) {
            throw new RuntimeException("Game with key already exists: " + request.getGameKey());
        }

        GamificationMiniGameDefinition game = GamificationMiniGameDefinition.builder()
                .gameKey(request.getGameKey())
                .gameTitle(request.getGameTitle())
                .gameDescription(request.getGameDescription())
                .gameIcon(request.getGameIcon())
                .gameType(request.getGameType())
                .difficultyLevel(request.getDifficultyLevel())
                .baseCoinReward(request.getBaseCoinReward())
                .maxCoinReward(request.getMaxCoinReward())
                .xpReward(request.getXpReward())
                .cooldownMinutes(request.getCooldownMinutes())
                .maxPlaysPerDay(request.getMaxPlaysPerDay())
                .maxCoinsPerDay(request.getMaxCoinsPerDay())
                .isActive(request.getIsActive())
                .isPremiumOnly(request.getIsPremiumOnly())
                .requiredPremiumPlan(request.getRequiredPremiumPlan())
                .premiumCoinMultiplier(request.getPremiumCoinMultiplier())
                .gameConfig(request.getGameConfig())
                .createdByAdminId(adminId)
                .build();

        game = gameDefRepository.save(game);
        log.info("Created game definition: {} by admin {}", game.getGameKey(), adminId);

        return mapToGameResponse(game, null);
    }

    @Override
    @Transactional
    public MiniGameDefinitionResponse updateGameDefinition(Long gameDefId, MiniGameDefinitionRequest request) {
        GamificationMiniGameDefinition game = gameDefRepository.findById(gameDefId)
                .orElseThrow(() -> new RuntimeException("Game definition not found"));

        game.setGameTitle(request.getGameTitle());
        game.setGameDescription(request.getGameDescription());
        game.setGameIcon(request.getGameIcon());
        game.setGameType(request.getGameType());
        game.setDifficultyLevel(request.getDifficultyLevel());
        game.setBaseCoinReward(request.getBaseCoinReward());
        game.setMaxCoinReward(request.getMaxCoinReward());
        game.setXpReward(request.getXpReward());
        game.setCooldownMinutes(request.getCooldownMinutes());
        game.setMaxPlaysPerDay(request.getMaxPlaysPerDay());
        game.setMaxCoinsPerDay(request.getMaxCoinsPerDay());
        game.setIsActive(request.getIsActive());
        game.setIsPremiumOnly(request.getIsPremiumOnly());
        game.setRequiredPremiumPlan(request.getRequiredPremiumPlan());
        game.setPremiumCoinMultiplier(request.getPremiumCoinMultiplier());
        game.setGameConfig(request.getGameConfig());

        game = gameDefRepository.save(game);
        return mapToGameResponse(game, null);
    }

    @Override
    @Transactional
    public void deleteGameDefinition(Long gameDefId) {
        gameDefRepository.deleteById(gameDefId);
        log.info("Deleted game definition: {}", gameDefId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MiniGameDefinitionResponse> getAllGameDefinitions() {
        return gameDefRepository.findAll().stream()
                .map(game -> mapToGameResponse(game, null))
                .collect(Collectors.toList());
    }

    // Helper methods
    private Integer calculateCoinsForSession(GamificationMiniGameDefinition game, Integer scoreAchieved) {
        if (scoreAchieved == null || scoreAchieved <= 0) {
            return 0;
        }
        
        // Simple linear calculation: score * multiplier (default 1) + base reward
        // This should be adjusted based on game type and difficulty
        int calculatedCoins = game.getBaseCoinReward() + (scoreAchieved / 10);
        
        // Cap at max reward
        return Math.min(calculatedCoins, game.getMaxCoinReward());
    }

    private MiniGameDefinitionResponse mapToGameResponse(GamificationMiniGameDefinition game, Long userId) {
        MiniGameDefinitionResponse response = MiniGameDefinitionResponse.builder()
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

        if (userId != null) {
            response.setAvailable(canPlayGame(userId, game.getGameKey()));
            
            gameSessionRepository.findLastSessionByUserAndGame(userId, game.getGameDefId())
                    .ifPresent(session -> response.setLastPlayed(session.getPlayedAt()));
        }

        return response;
    }

    private GameSessionResponse mapToSessionResponse(GamificationGameSession session) {
        return GameSessionResponse.builder()
                .sessionId(session.getSessionId())
                .userId(session.getUserId())
                .gameDefinition(session.getGameDefinition() != null ? 
                        mapToGameResponse(session.getGameDefinition(), session.getUserId()) : null)
                .sessionStatus(session.getSessionStatus())
                .scoreAchieved(session.getScoreAchieved())
                .coinsEarned(session.getCoinsEarned())
                .xpEarned(session.getXpEarned())
                .isVerified(session.getIsVerified())
                .playedAt(session.getPlayedAt())
                .completedAt(session.getCompletedAt())
                .durationSeconds(session.getDurationSeconds())
                .build();
    }

    @Override
    public MiniGameDefinitionResponse toggleGameStatus(Long gameDefId, boolean active) {
        GamificationMiniGameDefinition game = gameDefRepository.findById(gameDefId)
                .orElseThrow(() -> new RuntimeException("Game not found with ID: " + gameDefId));
        
        game.setIsActive(active);
        gameDefRepository.save(game);
        
        return mapToGameResponse(game, null);
    }

    @Override
    public MiniGameDefinitionResponse updateGameRewards(Long gameDefId, Integer baseCoinReward, 
                                                        Integer maxCoinReward, Integer xpReward) {
        GamificationMiniGameDefinition game = gameDefRepository.findById(gameDefId)
                .orElseThrow(() -> new RuntimeException("Game not found with ID: " + gameDefId));
        
        if (baseCoinReward != null) {
            game.setBaseCoinReward(baseCoinReward);
        }
        if (maxCoinReward != null) {
            game.setMaxCoinReward(maxCoinReward);
        }
        if (xpReward != null) {
            game.setXpReward(xpReward);
        }
        
        gameDefRepository.save(game);
        return mapToGameResponse(game, null);
    }

    @Override
    public MiniGameDefinitionResponse updateGameCooldown(Long gameDefId, Integer cooldownMinutes, 
                                                         Integer maxPlaysPerDay, Integer maxCoinsPerDay) {
        GamificationMiniGameDefinition game = gameDefRepository.findById(gameDefId)
                .orElseThrow(() -> new RuntimeException("Game not found with ID: " + gameDefId));
        
        if (cooldownMinutes != null) {
            game.setCooldownMinutes(cooldownMinutes);
        }
        if (maxPlaysPerDay != null) {
            game.setMaxPlaysPerDay(maxPlaysPerDay);
        }
        if (maxCoinsPerDay != null) {
            game.setMaxCoinsPerDay(maxCoinsPerDay);
        }
        
        gameDefRepository.save(game);
        return mapToGameResponse(game, null);
    }
}
