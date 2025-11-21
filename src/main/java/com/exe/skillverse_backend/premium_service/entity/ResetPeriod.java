package com.exe.skillverse_backend.premium_service.entity;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Enum defining reset periods for usage tracking
 * Determines how often usage counts are reset to zero
 */
public enum ResetPeriod {

    /**
     * Reset every hour (for strict rate limiting)
     * Usage resets at the start of each hour (XX:00:00)
     */
    HOURLY("Hourly", "Mỗi giờ"),

    /**
     * Reset every day at midnight
     * Usage resets at 00:00:00 each day
     */
    DAILY("Daily", "Hàng ngày"),

    /**
     * Reset every month on the 1st
     * Usage resets at 00:00:00 on the 1st of each month
     */
    MONTHLY("Monthly", "Hàng tháng"),

    /**
     * Never reset (lifetime limit)
     * Usage accumulates forever and never resets
     */
    NEVER("Never", "Không bao giờ"),

    /**
     * Custom 8-hour window (for FREE_TIER chatbot)
     * Resets 8 hours after first usage in the period
     * Special case: used for free tier AI chatbot with 10 requests per 8-hour
     * window
     */
    CUSTOM_8_HOURS("8 Hours", "8 giờ");

    private final String displayName;
    private final String displayNameVi;

    ResetPeriod(String displayName, String displayNameVi) {
        this.displayName = displayName;
        this.displayNameVi = displayNameVi;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDisplayNameVi() {
        return displayNameVi;
    }

    /**
     * Calculate the next reset time based on current time and this period
     * 
     * @param currentPeriodStart The start of the current period
     * @return The next reset timestamp
     */
    public LocalDateTime calculateNextReset(LocalDateTime currentPeriodStart) {
        LocalDateTime now = LocalDateTime.now();

        switch (this) {
            case HOURLY:
                // Next hour boundary
                return now.plusHours(1).truncatedTo(ChronoUnit.HOURS);

            case DAILY:
                // Next day at midnight
                return now.plusDays(1).truncatedTo(ChronoUnit.DAYS);

            case MONTHLY:
                // Next month, 1st day at midnight
                return now.plusMonths(1).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);

            case CUSTOM_8_HOURS:
                // 8 hours from period start
                return currentPeriodStart.plusHours(8);

            case NEVER:
            default:
                // Far future (100 years)
                return now.plusYears(100);
        }
    }

    /**
     * Calculate the start of the current period based on this reset period
     * 
     * @return The start timestamp of the current period
     */
    public LocalDateTime calculatePeriodStart() {
        LocalDateTime now = LocalDateTime.now();

        switch (this) {
            case HOURLY:
                // Start of current hour
                return now.truncatedTo(ChronoUnit.HOURS);

            case DAILY:
                // Start of current day (midnight)
                return now.truncatedTo(ChronoUnit.DAYS);

            case MONTHLY:
                // Start of current month (1st day at midnight)
                return now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);

            case CUSTOM_8_HOURS:
                // For custom 8-hour window, start is when first usage occurs
                // This will be set dynamically when first usage is recorded
                return now;

            case NEVER:
            default:
                // Far past (account creation or plan start)
                return now.minusYears(100);
        }
    }

    /**
     * Check if a period has expired and needs reset
     * 
     * @param periodEnd The end timestamp of the current period
     * @return true if period has expired
     */
    public boolean isPeriodExpired(LocalDateTime periodEnd) {
        return LocalDateTime.now().isAfter(periodEnd);
    }
}
