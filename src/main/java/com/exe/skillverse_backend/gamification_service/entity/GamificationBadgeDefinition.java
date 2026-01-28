package com.exe.skillverse_backend.gamification_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity for admin-managed badge definitions
 */
@Entity
@Table(name = "gamification_badge_definitions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GamificationBadgeDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "badge_def_id")
    private Long badgeDefId;

    @Column(name = "badge_key", nullable = false, unique = true, length = 100)
    private String badgeKey; // Unique identifier like "speed-learner"

    @Column(name = "badge_title", nullable = false, length = 200)
    private String badgeTitle;

    @Column(name = "badge_description", columnDefinition = "TEXT")
    private String badgeDescription;

    @Column(name = "badge_icon", length = 20)
    private String badgeIcon; // Emoji or icon class

    @Column(name = "badge_category", nullable = false, length = 50)
    private String badgeCategory; // learning, community, events, coins

    @Column(name = "badge_rarity", nullable = false, length = 30)
    private String badgeRarity; // common, rare, epic, legendary

    @Column(name = "criteria_description", columnDefinition = "TEXT")
    private String criteriaDescription; // Human-readable criteria

    @Column(name = "criteria_config", columnDefinition = "TEXT")
    private String criteriaConfig; // JSON configuration for automated checking

    @Column(name = "coin_reward", nullable = false)
    @Builder.Default
    private Integer coinReward = 0;

    @Column(name = "xp_reward", nullable = false)
    @Builder.Default
    private Integer xpReward = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "display_order")
    private Integer displayOrder;

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
