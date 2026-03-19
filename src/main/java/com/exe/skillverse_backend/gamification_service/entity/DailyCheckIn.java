package com.exe.skillverse_backend.gamification_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Entity tracking daily check-ins for streak calculation
 * Independent of lesson completion - users can check in once per day
 */
@Entity
@Table(name = "daily_check_ins", 
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_checkin_date", columnNames = {"user_id", "check_in_date"})
    },
    indexes = {
        @Index(name = "idx_checkin_user", columnList = "user_id"),
        @Index(name = "idx_checkin_date", columnList = "check_in_date"),
        @Index(name = "idx_checkin_user_date", columnList = "user_id, check_in_date")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyCheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "check_in_id")
    private Long checkInId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_in_time", nullable = false)
    private LocalDateTime checkInTime;

    @Column(name = "coins_awarded")
    @Builder.Default
    private Integer coinsAwarded = 0;

    @Column(name = "xp_awarded")
    @Builder.Default
    private Integer xpAwarded = 0;

    @Column(name = "streak_day")
    @Builder.Default
    private Integer streakDay = 1; // Which day of the current streak this check-in represents

    @Column(name = "is_bonus_day")
    @Builder.Default
    private Boolean isBonusDay = false; // True if this is day 7 (weekly bonus)

    @PrePersist
    protected void onCreate() {
        if (checkInTime == null) {
            checkInTime = LocalDateTime.now();
        }
        if (checkInDate == null) {
            checkInDate = LocalDate.now();
        }
    }
}
