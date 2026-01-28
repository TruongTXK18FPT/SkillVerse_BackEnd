package com.exe.skillverse_backend.gamification_service.config;

import com.exe.skillverse_backend.gamification_service.entity.GamificationMiniGameDefinition;
import com.exe.skillverse_backend.gamification_service.repository.GamificationMiniGameDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class GamificationDataInitializer implements CommandLineRunner {

    private final GamificationMiniGameDefinitionRepository gameDefRepository;

    @Override
    public void run(String... args) {
        initializeGames();
    }

    private void initializeGames() {
        if (gameDefRepository.count() > 0) {
            log.info("✅ Games already initialized ({} games found)", gameDefRepository.count());
            return;
        }

        log.info("🎮 Initializing mini-games...");

        // Tic-Tac-Toe Game
        GamificationMiniGameDefinition ticTacToe = GamificationMiniGameDefinition.builder()
                .gameKey("tic-tac-toe")
                .gameTitle("Tic Tac Toe")
                .gameDescription("Classic Tic Tac Toe game. Beat the AI to earn coins!")
                .gameType("game")
                .difficultyLevel("easy")
                .baseCoinReward(5)
                .maxCoinReward(10)
                .xpReward(5)
                .isPremiumOnly(false)
                .isActive(true)
                .cooldownMinutes(0)
                .maxPlaysPerDay(10)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Meowl Adventure Game
        GamificationMiniGameDefinition meowlAdventure = GamificationMiniGameDefinition.builder()
                .gameKey("meowl-adventure")
                .gameTitle("Meowl Adventure")
                .gameDescription("Guide Meowl through obstacles! Collect coins and avoid dangers.")
                .gameType("game")
                .difficultyLevel("medium")
                .baseCoinReward(10)
                .maxCoinReward(20)
                .xpReward(10)
                .isPremiumOnly(false)
                .isActive(true)
                .cooldownMinutes(0)
                .maxPlaysPerDay(10)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Memory Match Game (Premium)
        GamificationMiniGameDefinition memoryMatch = GamificationMiniGameDefinition.builder()
                .gameKey("memory-match")
                .gameTitle("Memory Match")
                .gameDescription("Match pairs of cards. Premium members get bonus coins!")
                .gameType("game")
                .difficultyLevel("medium")
                .baseCoinReward(15)
                .maxCoinReward(30)
                .xpReward(15)
                .isPremiumOnly(true)
                .isActive(true)
                .cooldownMinutes(5)
                .maxPlaysPerDay(20)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Quiz Challenge (Premium)
        GamificationMiniGameDefinition quizChallenge = GamificationMiniGameDefinition.builder()
                .gameKey("quiz-challenge")
                .gameTitle("Quiz Challenge")
                .gameDescription("Answer questions correctly to earn big rewards!")
                .gameType("quiz")
                .difficultyLevel("hard")
                .baseCoinReward(25)
                .maxCoinReward(50)
                .xpReward(25)
                .isPremiumOnly(true)
                .isActive(true)
                .cooldownMinutes(10)
                .maxPlaysPerDay(15)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        gameDefRepository.save(ticTacToe);
        gameDefRepository.save(meowlAdventure);
        gameDefRepository.save(memoryMatch);
        gameDefRepository.save(quizChallenge);

        log.info("✅ Initialized {} mini-games successfully", gameDefRepository.count());
    }
}
