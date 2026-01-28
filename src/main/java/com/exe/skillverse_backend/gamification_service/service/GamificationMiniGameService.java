package com.exe.skillverse_backend.gamification_service.service;

import com.exe.skillverse_backend.gamification_service.dto.request.CompleteGameSessionRequest;
import com.exe.skillverse_backend.gamification_service.dto.request.MiniGameDefinitionRequest;
import com.exe.skillverse_backend.gamification_service.dto.request.StartGameSessionRequest;
import com.exe.skillverse_backend.gamification_service.dto.response.GameSessionResponse;
import com.exe.skillverse_backend.gamification_service.dto.response.MiniGameDefinitionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service interface for managing mini-games and game sessions
 */
public interface GamificationMiniGameService {

    /**
     * Get all available mini-games for user
     */
    List<MiniGameDefinitionResponse> getAvailableGames(Long userId, String userPlan);

    /**
     * Get specific game definition
     */
    MiniGameDefinitionResponse getGameDefinition(String gameKey, Long userId);

    /**
     * Start a new game session
     */
    GameSessionResponse startGameSession(Long userId, StartGameSessionRequest request);

    /**
     * Complete a game session and award coins
     */
    GameSessionResponse completeGameSession(Long userId, CompleteGameSessionRequest request);

    /**
     * Get user game session history
     */
    Page<GameSessionResponse> getUserGameHistory(Long userId, Pageable pageable);

    /**
     * Check if user can play game now (cooldown, daily limits)
     */
    boolean canPlayGame(Long userId, String gameKey);

    /**
     * Admin: Create mini-game definition
     */
    MiniGameDefinitionResponse createGameDefinition(MiniGameDefinitionRequest request, Long adminId);

    /**
     * Admin: Update mini-game definition
     */
    MiniGameDefinitionResponse updateGameDefinition(Long gameDefId, MiniGameDefinitionRequest request);

    /**
     * Admin: Delete mini-game definition
     */
    void deleteGameDefinition(Long gameDefId);

    /**
     * Get all game definitions (admin)
     */
    List<MiniGameDefinitionResponse> getAllGameDefinitions();

    /**
     * Admin: Toggle game active status
     */
    MiniGameDefinitionResponse toggleGameStatus(Long gameDefId, boolean active);

    /**
     * Admin: Update game rewards
     */
    MiniGameDefinitionResponse updateGameRewards(Long gameDefId, Integer baseCoinReward, Integer maxCoinReward, Integer xpReward);

    /**
     * Admin: Update game cooldown
     */
    MiniGameDefinitionResponse updateGameCooldown(Long gameDefId, Integer cooldownMinutes, Integer maxPlaysPerDay, Integer maxCoinsPerDay);
}
