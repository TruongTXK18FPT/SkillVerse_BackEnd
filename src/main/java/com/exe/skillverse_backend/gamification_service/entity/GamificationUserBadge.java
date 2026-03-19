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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Entity representing badges earned by users
 */
@Entity
@Table(name = "gamification_user_badges", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "badge_def_id"}),
       indexes = {
           @Index(name = "idx_user_badge_earned", columnList = "user_id, earned_at"),
           @Index(name = "idx_badge_def", columnList = "badge_def_id")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GamificationUserBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_badge_id")
    private Long userBadgeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "badge_def_id", nullable = false)
    private Long badgeDefId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "badge_def_id", insertable = false, updatable = false)
    private GamificationBadgeDefinition badgeDefinition;

    @Builder.Default
    @Column(name = "earned_at", nullable = false)
    private LocalDateTime earnedAt = LocalDateTime.now();

    @Column(name = "coins_awarded", nullable = false)
    private Integer coinsAwarded;

    @Column(name = "xp_awarded", nullable = false)
    private Integer xpAwarded;

    @Column(name = "progress_data", columnDefinition = "TEXT")
    private String progressData; // JSON for tracking progress toward badge
}
