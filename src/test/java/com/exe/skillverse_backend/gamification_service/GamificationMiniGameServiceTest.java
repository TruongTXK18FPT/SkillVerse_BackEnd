package com.exe.skillverse_backend.gamification_service;

import com.exe.skillverse_backend.gamification_service.dto.request.CompleteGameSessionRequest;
import com.exe.skillverse_backend.gamification_service.dto.request.MiniGameDefinitionRequest;
import com.exe.skillverse_backend.gamification_service.dto.request.StartGameSessionRequest;
import com.exe.skillverse_backend.gamification_service.dto.response.GameSessionResponse;
import com.exe.skillverse_backend.gamification_service.dto.response.MiniGameDefinitionResponse;
import com.exe.skillverse_backend.gamification_service.entity.GamificationMiniGameDefinition;
import com.exe.skillverse_backend.gamification_service.repository.GamificationMiniGameDefinitionRepository;
import com.exe.skillverse_backend.gamification_service.repository.GamificationGameSessionRepository;
import com.exe.skillverse_backend.gamification_service.service.GamificationMiniGameService;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.exe.skillverse_backend.gamification_service.service.impl.GamificationMiniGameServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GamificationMiniGameServiceTest {

    @Mock
    private GamificationMiniGameDefinitionRepository gameDefRepository;

    @Mock
    private GamificationGameSessionRepository gameSessionRepository;

    @Mock
    private WalletService walletService;

    @InjectMocks
    private GamificationMiniGameServiceImpl miniGameService;

    private GamificationMiniGameDefinition testGame;
    private Long testUserId = 1L;
    private Long testAdminId = 100L;

    @BeforeEach
    void setUp() {
        testGame = GamificationMiniGameDefinition.builder()
                .gameDefId(1L)
                .gameKey("test-game")
                .gameTitle("Test Game")
                .gameDescription("A test mini-game")
                .gameIcon("🎮")
                .gameType("quiz")
                .difficultyLevel("medium")
                .baseCoinReward(50)
                .maxCoinReward(200)
                .xpReward(30)
                .cooldownMinutes(60)
                .maxPlaysPerDay(5)
                .isActive(true)
                .isPremiumOnly(false)
                .build();
    }

    @Test
    void testCreateGameDefinition_Success() {
        // Given
        MiniGameDefinitionRequest request = MiniGameDefinitionRequest.builder()
                .gameKey("new-game")
                .gameTitle("New Game")
                .gameType("quiz")
                .difficultyLevel("easy")
                .baseCoinReward(30)
                .maxCoinReward(100)
                .xpReward(20)
                .cooldownMinutes(30)
                .isActive(true)
                .isPremiumOnly(false)
                .premiumCoinMultiplier(1.0)
                .build();

        when(gameDefRepository.existsByGameKey("new-game")).thenReturn(false);
        when(gameDefRepository.save(any(GamificationMiniGameDefinition.class)))
                .thenAnswer(i -> {
                    GamificationMiniGameDefinition game = i.getArgument(0);
                    game.setGameDefId(2L);
                    return game;
                });

        // When
        MiniGameDefinitionResponse response = miniGameService.createGameDefinition(request, testAdminId);

        // Then
        assertNotNull(response);
        assertEquals("new-game", response.getGameKey());
        assertEquals("New Game", response.getGameTitle());
        assertEquals(30, response.getBaseCoinReward());
        verify(gameDefRepository).save(any(GamificationMiniGameDefinition.class));
    }

    @Test
    void testCreateGameDefinition_DuplicateKey_ThrowsException() {
        // Given
        MiniGameDefinitionRequest request = MiniGameDefinitionRequest.builder()
                .gameKey("existing-game")
                .gameTitle("Existing Game")
                .gameType("quiz")
                .difficultyLevel("easy")
                .baseCoinReward(30)
                .maxCoinReward(100)
                .xpReward(20)
                .cooldownMinutes(30)
                .isActive(true)
                .isPremiumOnly(false)
                .premiumCoinMultiplier(1.0)
                .build();

        when(gameDefRepository.existsByGameKey("existing-game")).thenReturn(true);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            miniGameService.createGameDefinition(request, testAdminId);
        });

        verify(gameDefRepository, never()).save(any());
    }

    @Test
    void testStartGameSession_Success() {
        // Given
        StartGameSessionRequest request = new StartGameSessionRequest();
        request.setGameKey("test-game");

        when(gameDefRepository.findByGameKey("test-game")).thenReturn(Optional.of(testGame));
        when(gameSessionRepository.findLastSessionByUserAndGame(any(), any())).thenReturn(Optional.empty());
        when(gameSessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // When
        GameSessionResponse response = miniGameService.startGameSession(testUserId, request);

        // Then
        assertNotNull(response);
        assertEquals(testUserId, response.getUserId());
        assertEquals("IN_PROGRESS", response.getSessionStatus());
        verify(gameSessionRepository).save(any());
    }

    @Test
    void testCompleteGameSession_AwardsCoins() {
        // Given
        CompleteGameSessionRequest request = CompleteGameSessionRequest.builder()
                .sessionId(1L)
                .sessionStatus("COMPLETED")
                .scoreAchieved(80)
                .durationSeconds(120)
                .build();

        when(gameSessionRepository.findById(1L)).thenReturn(Optional.of(createTestSession()));
        when(gameSessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // When
        GameSessionResponse response = miniGameService.completeGameSession(testUserId, request);

        // Then
        assertNotNull(response);
        assertEquals("COMPLETED", response.getSessionStatus());
        assertTrue(response.getCoinsEarned() > 0);
        verify(walletService).addCoins(eq(testUserId), anyLong(), any(), anyString(), eq("MINIGAME"), anyString());
    }

    @Test
    void testUpdateGameDefinition_Success() {
        // Given
        MiniGameDefinitionRequest request = MiniGameDefinitionRequest.builder()
                .gameKey("test-game")
                .gameTitle("Updated Game")
                .gameDescription("Updated description")
                .gameIcon("🎯")
                .gameType("quiz")
                .difficultyLevel("hard")
                .baseCoinReward(100)
                .maxCoinReward(300)
                .xpReward(50)
                .cooldownMinutes(90)
                .maxPlaysPerDay(3)
                .isActive(true)
                .isPremiumOnly(false)
                .premiumCoinMultiplier(1.5)
                .build();

        when(gameDefRepository.findById(1L)).thenReturn(Optional.of(testGame));
        when(gameDefRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // When
        MiniGameDefinitionResponse response = miniGameService.updateGameDefinition(1L, request);

        // Then
        assertNotNull(response);
        assertEquals("Updated Game", response.getGameTitle());
        assertEquals(100, response.getBaseCoinReward());
        assertEquals("hard", response.getDifficultyLevel());
        verify(gameDefRepository).save(any());
    }

    @Test
    void testDeleteGameDefinition_Success() {
        // When
        miniGameService.deleteGameDefinition(1L);

        // Then
        verify(gameDefRepository).deleteById(1L);
    }

    private com.exe.skillverse_backend.gamification_service.entity.GamificationGameSession createTestSession() {
        return com.exe.skillverse_backend.gamification_service.entity.GamificationGameSession.builder()
                .sessionId(1L)
                .userId(testUserId)
                .gameDefId(testGame.getGameDefId())
                .gameDefinition(testGame)
                .sessionStatus("IN_PROGRESS")
                .coinsEarned(0)
                .xpEarned(0)
                .isVerified(false)
                .build();
    }
}
