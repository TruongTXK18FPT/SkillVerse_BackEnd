package com.exe.skillverse_backend.gamification_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Entity representing a user's coin wallet in the gamification system
 */
@Entity
@Table(name = "gamification_user_wallets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GamificationUserWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wallet_id")
    private Long walletId;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Builder.Default
    @Column(name = "total_coins", nullable = false)
    private Integer totalCoins = 0;

    @Builder.Default
    @Column(name = "earned_coins", nullable = false)
    private Integer earnedCoins = 0; // Total coins earned historically

    @Builder.Default
    @Column(name = "spent_coins", nullable = false)
    private Integer spentCoins = 0; // Total coins spent

    @Builder.Default
    @Column(name = "total_xp", nullable = false)
    private Integer totalXp = 0;

    @Builder.Default
    @Column(name = "streak_days", nullable = false)
    private Integer streakDays = 0;

    @Column(name = "last_activity_date")
    private LocalDateTime lastActivityDate;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
