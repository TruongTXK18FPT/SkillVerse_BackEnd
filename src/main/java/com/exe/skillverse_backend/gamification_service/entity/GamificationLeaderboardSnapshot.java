package com.exe.skillverse_backend.gamification_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity for leaderboard snapshots - periodically saved rankings
 */
@Entity
@Table(name = "gamification_leaderboard_snapshots", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "leaderboard_period", "snapshot_date"}),
       indexes = {
           @Index(name = "idx_leaderboard_period", columnList = "leaderboard_period, snapshot_date"),
           @Index(name = "idx_leaderboard_rank", columnList = "leaderboard_period, rank_position")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GamificationLeaderboardSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_id")
    private Long snapshotId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "leaderboard_period", nullable = false, length = 30)
    private String leaderboardPeriod; // week, month, all

    @Column(name = "leaderboard_type", nullable = false, length = 30)
    private String leaderboardType; // learning, community, coins

    @Column(name = "rank_position", nullable = false)
    private Integer rankPosition;

    @Column(name = "score_value", nullable = false)
    private Integer scoreValue; // Coins, XP, or other metric

    @Column(name = "total_coins")
    private Integer totalCoins;

    @Column(name = "total_xp")
    private Integer totalXp;

    @Column(name = "badges_count")
    private Integer badgesCount;

    @Column(name = "streak_days")
    private Integer streakDays;

    @Column(name = "contributions_count")
    private Integer contributionsCount;

    @Column(name = "skins_count")
    private Integer skinsCount;

    @Builder.Default
    @Column(name = "snapshot_date", nullable = false)
    private LocalDateTime snapshotDate = LocalDateTime.now();
}
