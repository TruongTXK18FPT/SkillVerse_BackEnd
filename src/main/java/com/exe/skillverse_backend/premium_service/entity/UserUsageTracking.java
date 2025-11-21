package com.exe.skillverse_backend.premium_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity tracking user's usage of premium features
 * Tracks usage counts and manages reset periods
 */
@Entity
@Table(name = "user_usage_tracking", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_feature", columnNames = { "user_id", "feature_type" })
}, indexes = {
        @Index(name = "idx_usage_user_id", columnList = "user_id"),
        @Index(name = "idx_usage_feature_type", columnList = "feature_type"),
        @Index(name = "idx_usage_period", columnList = "current_period_start, current_period_end")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserUsageTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User whose usage is being tracked
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Type of feature being tracked
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "feature_type", nullable = false, length = 50)
    private FeatureType featureType;

    /**
     * Current usage count in this period
     */
    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private Integer usageCount = 0;

    /**
     * When the usage was last reset
     */
    @Column(name = "last_reset_at", nullable = false)
    private LocalDateTime lastResetAt;

    /**
     * Start of the current tracking period
     */
    @Column(name = "current_period_start", nullable = false)
    private LocalDateTime currentPeriodStart;

    /**
     * End of the current tracking period
     */
    @Column(name = "current_period_end", nullable = false)
    private LocalDateTime currentPeriodEnd;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Check if the current period has expired and needs reset
     */
    public boolean needsReset() {
        return LocalDateTime.now().isAfter(currentPeriodEnd);
    }

    /**
     * Reset usage count and start new period
     * 
     * @param resetPeriod The reset period configuration
     */
    public void resetUsage(ResetPeriod resetPeriod) {
        this.usageCount = 0;
        this.lastResetAt = LocalDateTime.now();
        this.currentPeriodStart = resetPeriod.calculatePeriodStart();
        this.currentPeriodEnd = resetPeriod.calculateNextReset(this.currentPeriodStart);
    }

    /**
     * Increment usage count by 1
     */
    public void incrementUsage() {
        this.usageCount++;
    }

    /**
     * Increment usage count by a specific amount
     * 
     * @param amount Amount to increment by
     */
    public void incrementUsage(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Cannot increment usage by negative amount");
        }
        this.usageCount += amount;
    }

    /**
     * Check if usage has reached the limit
     * 
     * @param limit The limit to check against (null = unlimited)
     * @return true if limit is reached
     */
    public boolean hasReachedLimit(Integer limit) {
        if (limit == null) {
            return false; // Unlimited
        }
        return this.usageCount >= limit;
    }

    /**
     * Get remaining usage before hitting limit
     * 
     * @param limit The limit to check against (null = unlimited)
     * @return Remaining usage count, or null if unlimited
     */
    public Integer getRemainingUsage(Integer limit) {
        if (limit == null) {
            return null; // Unlimited
        }
        int remaining = limit - this.usageCount;
        return Math.max(0, remaining);
    }

    /**
     * Get time remaining until next reset
     * 
     * @return Duration until next reset in seconds
     */
    public long getSecondsUntilReset() {
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(currentPeriodEnd)) {
            return 0;
        }
        return java.time.Duration.between(now, currentPeriodEnd).getSeconds();
    }

    /**
     * Get formatted time until reset (for display)
     * 
     * @return Human-readable time until reset
     */
    public String getFormattedTimeUntilReset() {
        long seconds = getSecondsUntilReset();

        if (seconds <= 0) {
            return "Đã hết hạn";
        }

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;

        if (hours > 24) {
            long days = hours / 24;
            return days + " ngày";
        } else if (hours > 0) {
            return hours + " giờ " + minutes + " phút";
        } else {
            return minutes + " phút";
        }
    }

    /**
     * Initialize a new tracking record with proper period boundaries
     * 
     * @param user        User to track
     * @param featureType Feature to track
     * @param resetPeriod Reset period configuration
     * @return New tracking record
     */
    public static UserUsageTracking initializeTracking(User user, FeatureType featureType, ResetPeriod resetPeriod) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime periodStart = resetPeriod.calculatePeriodStart();
        LocalDateTime periodEnd = resetPeriod.calculateNextReset(periodStart);

        return UserUsageTracking.builder()
                .user(user)
                .featureType(featureType)
                .usageCount(0)
                .lastResetAt(now)
                .currentPeriodStart(periodStart)
                .currentPeriodEnd(periodEnd)
                .build();
    }

    /**
     * Check and reset if period has expired
     * 
     * @param resetPeriod The reset period configuration
     * @return true if reset was performed
     */
    public boolean checkAndResetIfExpired(ResetPeriod resetPeriod) {
        if (needsReset()) {
            resetUsage(resetPeriod);
            return true;
        }
        return false;
    }
}
