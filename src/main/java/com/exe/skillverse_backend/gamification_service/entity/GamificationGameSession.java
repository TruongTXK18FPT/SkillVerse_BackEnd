package com.exe.skillverse_backend.gamification_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity tracking user mini-game play sessions
 */
@Entity
@Table(name = "gamification_game_sessions", indexes = {
    @Index(name = "idx_user_game_session", columnList = "user_id, played_at"),
    @Index(name = "idx_game_def_session", columnList = "game_def_id"),
    @Index(name = "idx_session_status", columnList = "session_status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GamificationGameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "game_def_id", nullable = false)
    private Long gameDefId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "game_def_id", insertable = false, updatable = false)
    private GamificationMiniGameDefinition gameDefinition;

    @Column(name = "session_status", nullable = false, length = 30)
    @Builder.Default
    private String sessionStatus = "IN_PROGRESS"; // IN_PROGRESS, COMPLETED, FAILED, ABANDONED

    @Column(name = "score_achieved")
    private Integer scoreAchieved;

    @Column(name = "coins_earned", nullable = false)
    @Builder.Default
    private Integer coinsEarned = 0;

    @Column(name = "xp_earned", nullable = false)
    @Builder.Default
    private Integer xpEarned = 0;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false; // Server-side verification status

    @Column(name = "verification_data", columnDefinition = "TEXT")
    private String verificationData; // Anti-cheat data

    @Builder.Default
    @Column(name = "played_at", nullable = false)
    private LocalDateTime playedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "session_data", columnDefinition = "TEXT")
    private String sessionData; // JSON for game-specific session data
}
