package com.exe.skillverse_backend.gamification_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity for admin-configured mini-game definitions
 */
@Entity
@Table(name = "gamification_minigame_definitions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GamificationMiniGameDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "game_def_id")
    private Long gameDefId;

    @Column(name = "game_key", nullable = false, unique = true, length = 100)
    private String gameKey; // e.g., "meowl-adventure", "tic-tac-toe"

    @Column(name = "game_title", nullable = false, length = 200)
    private String gameTitle;

    @Column(name = "game_description", columnDefinition = "TEXT")
    private String gameDescription;

    @Column(name = "game_icon", length = 20)
    private String gameIcon;

    @Column(name = "game_type", nullable = false, length = 50)
    private String gameType; // spin, quiz, hunt, help, game

    @Column(name = "difficulty_level", nullable = false, length = 30)
    private String difficultyLevel; // easy, medium, hard

    @Column(name = "base_coin_reward", nullable = false)
    @Builder.Default
    private Integer baseCoinReward = 0; // Base coins for completion

    @Column(name = "max_coin_reward", nullable = false)
    @Builder.Default
    private Integer maxCoinReward = 0; // Maximum possible coins

    @Column(name = "xp_reward", nullable = false)
    @Builder.Default
    private Integer xpReward = 0;

    @Column(name = "cooldown_minutes", nullable = false)
    @Builder.Default
    private Integer cooldownMinutes = 60; // Cooldown between plays

    @Column(name = "max_plays_per_day")
    private Integer maxPlaysPerDay; // Null = unlimited

    @Column(name = "max_coins_per_day")
    private Integer maxCoinsPerDay; // Daily coin limit from this game

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_premium_only", nullable = false)
    @Builder.Default
    private Boolean isPremiumOnly = false;

    @Column(name = "required_premium_plan", length = 30)
    private String requiredPremiumPlan; // basic, premium, pro

    @Column(name = "premium_coin_multiplier")
    @Builder.Default
    private Double premiumCoinMultiplier = 1.0;

    @Column(name = "game_config", columnDefinition = "TEXT")
    private String gameConfig; // JSON configuration for game-specific settings

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_by_admin_id")
    private Long createdByAdminId;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
