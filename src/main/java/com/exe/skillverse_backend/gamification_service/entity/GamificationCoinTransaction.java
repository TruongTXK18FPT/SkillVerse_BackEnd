package com.exe.skillverse_backend.gamification_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing coin transaction history for audit trail
 */
@Entity
@Table(name = "gamification_coin_transactions", indexes = {
    @Index(name = "idx_user_transaction", columnList = "user_id, transaction_date"),
    @Index(name = "idx_transaction_type", columnList = "transaction_type"),
    @Index(name = "idx_source_type", columnList = "source_type, source_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GamificationCoinTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "coin_amount", nullable = false)
    private Integer coinAmount; // Positive for earning, negative for spending

    @Column(name = "xp_amount")
    private Integer xpAmount; // XP gained with this transaction

    @Column(name = "transaction_type", nullable = false, length = 50)
    private String transactionType; // EARN, SPEND, ADMIN_ADJUST, REWARD

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType; // STUDY, QUIZ, ROADMAP, MINIGAME, BADGE, ADMIN, etc.

    @Column(name = "source_id")
    private Long sourceId; // ID of the source (lesson_id, game_id, badge_id, etc.)

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = true; // For anti-cheat validation

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter; // Coin balance after this transaction

    @Builder.Default
    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate = LocalDateTime.now();

    @Column(name = "admin_id")
    private Long adminId; // If this was an admin adjustment

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata; // JSON for additional data
}
