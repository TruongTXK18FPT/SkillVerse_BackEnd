package com.exe.skillverse_backend.premium_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity defining feature limits for each premium plan
 * Allows flexible configuration of what each plan tier can access
 */
@Entity
@Table(name = "plan_feature_limits", uniqueConstraints = {
        @UniqueConstraint(name = "uk_plan_feature", columnNames = { "plan_id", "feature_type" })
}, indexes = {
        @Index(name = "idx_plan_id", columnList = "plan_id"),
        @Index(name = "idx_feature_type", columnList = "feature_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanFeatureLimits {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The premium plan this limit applies to
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private PremiumPlan plan;

    /**
     * Type of feature being limited
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "feature_type", nullable = false, length = 50)
    private FeatureType featureType;

    /**
     * Maximum usage allowed per reset period
     * null = unlimited (when isUnlimited = true)
     * For boolean features (PRIORITY_SUPPORT), 1 = enabled, 0 = disabled
     * For multiplier features (COIN_EARNING_MULTIPLIER), use bonusMultiplier
     * instead
     */
    @Column(name = "limit_value")
    private Integer limitValue;

    /**
     * How often the usage count resets
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reset_period", nullable = false, length = 20)
    @Builder.Default
    private ResetPeriod resetPeriod = ResetPeriod.MONTHLY;

    /**
     * Whether this feature is unlimited for this plan
     * When true, limitValue is ignored
     */
    @Column(name = "is_unlimited", nullable = false)
    @Builder.Default
    private Boolean isUnlimited = false;

    /**
     * Bonus multiplier for features like coin earning
     * 1.00 = normal (100%)
     * 1.50 = 50% bonus
     * 2.00 = double (100% bonus)
     * Only used for COIN_EARNING_MULTIPLIER feature type
     */
    @Column(name = "bonus_multiplier", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal bonusMultiplier = BigDecimal.ONE;

    /**
     * Optional description explaining this limit
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Whether this limit is currently active
     * Allows temporarily disabling limits without deleting them
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Get the effective limit value
     * Returns null if unlimited, otherwise returns limitValue
     */
    public Integer getEffectiveLimit() {
        return isUnlimited ? null : limitValue;
    }

    /**
     * Check if this is a boolean feature (enabled/disabled)
     */
    public boolean isBooleanFeature() {
        return featureType != null && featureType.isBooleanFeature();
    }

    /**
     * Check if this is a multiplier feature
     */
    public boolean isMultiplierFeature() {
        return featureType != null && featureType.isMultiplierFeature();
    }

    /**
     * Check if feature is enabled (for boolean features)
     */
    public boolean isFeatureEnabled() {
        if (!isBooleanFeature()) {
            throw new IllegalStateException("isFeatureEnabled() can only be called on boolean features");
        }
        return limitValue != null && limitValue > 0;
    }

    /**
     * Get the multiplier value (for multiplier features)
     */
    public BigDecimal getMultiplierValue() {
        if (!isMultiplierFeature()) {
            throw new IllegalStateException("getMultiplierValue() can only be called on multiplier features");
        }
        return bonusMultiplier != null ? bonusMultiplier : BigDecimal.ONE;
    }

    /**
     * Validate the limit configuration
     */
    @PrePersist
    @PreUpdate
    private void validateLimit() {
        // Unlimited features should have null limitValue
        if (isUnlimited && limitValue != null) {
            limitValue = null;
        }

        // Boolean features should have 0 or 1
        if (isBooleanFeature() && limitValue != null && limitValue != 0 && limitValue != 1) {
            throw new IllegalArgumentException("Boolean features must have limitValue of 0 or 1");
        }

        // Multiplier features should have bonusMultiplier >= 0
        if (isMultiplierFeature() && bonusMultiplier != null && bonusMultiplier.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Bonus multiplier cannot be negative");
        }

        // Non-unlimited features must have a positive limit
        if (!isUnlimited && !isBooleanFeature() && !isMultiplierFeature()) {
            if (limitValue == null || limitValue <= 0) {
                throw new IllegalArgumentException("Non-unlimited features must have a positive limit value");
            }
        }
    }
}
