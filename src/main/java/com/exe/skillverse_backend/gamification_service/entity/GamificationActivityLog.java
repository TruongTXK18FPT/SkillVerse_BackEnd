package com.exe.skillverse_backend.gamification_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity tracking user activities for gamification
 */
@Entity
@Table(name = "gamification_activity_logs", indexes = {
    @Index(name = "idx_user_activity", columnList = "user_id, activity_timestamp"),
    @Index(name = "idx_activity_type", columnList = "activity_type"),
    @Index(name = "idx_verification_status", columnList = "is_verified")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GamificationActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "activity_log_id")
    private Long activityLogId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "activity_type", nullable = false, length = 50)
    private String activityType; // STUDY, QUIZ, ROADMAP, CONTRIBUTION, MINIGAME

    @Column(name = "activity_action", nullable = false, length = 50)
    private String activityAction; // start, complete, submit, win, etc.

    @Column(name = "target_type", length = 50)
    private String targetType; // lesson, quiz, roadmap, etc.

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "activity_data", columnDefinition = "TEXT")
    private String activityData; // JSON with activity details

    @Column(name = "coins_awarded")
    @Builder.Default
    private Integer coinsAwarded = 0;

    @Column(name = "xp_awarded")
    @Builder.Default
    private Integer xpAwarded = 0;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "verification_notes", columnDefinition = "TEXT")
    private String verificationNotes;

    @Builder.Default
    @Column(name = "activity_timestamp", nullable = false)
    private LocalDateTime activityTimestamp = LocalDateTime.now();

    @Column(name = "duration_minutes")
    private Integer durationMinutes;
}
