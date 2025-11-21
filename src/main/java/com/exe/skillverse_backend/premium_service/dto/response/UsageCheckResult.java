package com.exe.skillverse_backend.premium_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO representing the result of a usage limit check
 * Indicates whether a user can use a feature and provides context
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageCheckResult {

    /**
     * Whether the user is allowed to use the feature
     */
    private Boolean allowed;

    /**
     * Reason why usage is allowed or denied
     */
    private String reason;

    /**
     * Reason in Vietnamese
     */
    private String reasonVi;

    /**
     * Current usage count in this period
     */
    private Integer currentUsage;

    /**
     * Maximum allowed usage (null = unlimited)
     */
    private Integer limit;

    /**
     * Remaining usage before hitting limit (null = unlimited)
     */
    private Integer remaining;

    /**
     * When the usage counter will reset
     */
    private LocalDateTime resetAt;

    /**
     * Whether this feature is unlimited for the user's plan
     */
    private Boolean isUnlimited;

    /**
     * Human-readable time until reset (e.g., "3 giờ 15 phút")
     */
    private String timeUntilReset;

    /**
     * Create a success result (usage allowed)
     */
    public static UsageCheckResult allowed(Integer currentUsage, Integer limit, LocalDateTime resetAt,
            String timeUntilReset) {
        Integer remaining = limit != null ? Math.max(0, limit - currentUsage) : null;
        return UsageCheckResult.builder()
                .allowed(true)
                .reason("Usage allowed")
                .reasonVi("Được phép sử dụng")
                .currentUsage(currentUsage)
                .limit(limit)
                .remaining(remaining)
                .resetAt(resetAt)
                .isUnlimited(limit == null)
                .timeUntilReset(timeUntilReset)
                .build();
    }

    /**
     * Create an unlimited result (no limits)
     */
    public static UsageCheckResult unlimited(Integer currentUsage) {
        return UsageCheckResult.builder()
                .allowed(true)
                .reason("Unlimited usage")
                .reasonVi("Không giới hạn")
                .currentUsage(currentUsage)
                .limit(null)
                .remaining(null)
                .resetAt(null)
                .isUnlimited(true)
                .timeUntilReset("Không giới hạn")
                .build();
    }

    /**
     * Create a denied result (limit exceeded)
     */
    public static UsageCheckResult denied(String reason, String reasonVi, Integer currentUsage,
            Integer limit, LocalDateTime resetAt, String timeUntilReset) {
        return UsageCheckResult.builder()
                .allowed(false)
                .reason(reason)
                .reasonVi(reasonVi)
                .currentUsage(currentUsage)
                .limit(limit)
                .remaining(0)
                .resetAt(resetAt)
                .isUnlimited(false)
                .timeUntilReset(timeUntilReset)
                .build();
    }

    /**
     * Create a denied result with default message
     */
    public static UsageCheckResult limitExceeded(Integer currentUsage, Integer limit,
            LocalDateTime resetAt, String timeUntilReset) {
        String reason = String.format("Limit exceeded (%d/%d). Resets in %s", currentUsage, limit, timeUntilReset);
        String reasonVi = String.format("Đã vượt giới hạn (%d/%d). Làm mới sau %s", currentUsage, limit,
                timeUntilReset);
        return denied(reason, reasonVi, currentUsage, limit, resetAt, timeUntilReset);
    }
}
