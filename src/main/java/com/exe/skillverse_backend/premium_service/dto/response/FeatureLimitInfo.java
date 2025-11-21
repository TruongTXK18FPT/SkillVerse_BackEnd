package com.exe.skillverse_backend.premium_service.dto.response;

import com.exe.skillverse_backend.premium_service.entity.FeatureType;
import com.exe.skillverse_backend.premium_service.entity.ResetPeriod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO containing detailed information about a feature limit and current usage
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureLimitInfo {

    /**
     * Type of feature
     */
    private FeatureType featureType;

    /**
     * Display name in English
     */
    private String featureName;

    /**
     * Display name in Vietnamese
     */
    private String featureNameVi;

    /**
     * Maximum allowed usage per period (null = unlimited)
     */
    private Integer limit;

    /**
     * Current usage count in this period
     */
    private Integer currentUsage;

    /**
     * Remaining usage before hitting limit (null = unlimited)
     */
    private Integer remaining;

    /**
     * How often the usage resets
     */
    private ResetPeriod resetPeriod;

    /**
     * When the usage will reset next
     */
    private LocalDateTime nextResetAt;

    /**
     * Human-readable time until reset
     */
    private String timeUntilReset;

    /**
     * Whether this feature is unlimited
     */
    private Boolean isUnlimited;

    /**
     * Whether this feature is enabled (for boolean features)
     */
    private Boolean isEnabled;

    /**
     * Bonus multiplier (for multiplier features like coin earning)
     */
    private BigDecimal bonusMultiplier;

    /**
     * Usage percentage (0-100)
     */
    private Double usagePercentage;

    /**
     * Whether user is approaching the limit (>80%)
     */
    private Boolean approachingLimit;

    /**
     * Whether user has exceeded the limit
     */
    private Boolean limitExceeded;

    /**
     * Calculate usage percentage
     */
    public void calculateUsagePercentage() {
        if (isUnlimited || limit == null || limit == 0) {
            this.usagePercentage = 0.0;
            this.approachingLimit = false;
            this.limitExceeded = false;
        } else {
            this.usagePercentage = (currentUsage * 100.0) / limit;
            this.approachingLimit = usagePercentage >= 80.0;
            this.limitExceeded = currentUsage >= limit;
        }
    }

    /**
     * Calculate remaining usage
     */
    public void calculateRemaining() {
        if (isUnlimited || limit == null) {
            this.remaining = null;
        } else {
            this.remaining = Math.max(0, limit - currentUsage);
        }
    }
}
