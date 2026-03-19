package com.exe.skillverse_backend.business_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for job boost analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobBoostAnalyticsResponse {

    private Long boostId;
    private Long jobId;
    private String jobTitle;

    // Summary stats
    private Integer totalImpressions;
    private Integer totalClicks;
    private Integer totalApplications;

    // Rates
    private Double clickThroughRate;
    private Double applicationConversionRate;

    // Time period
    private LocalDateTime boostStartedAt;
    private LocalDateTime boostExpiresAt;
    private Long totalBoostDurationMinutes;

    // Daily breakdown (for charts)
    private List<DailyBoostStats> dailyStats;

    // Performance metrics
    private Double costPerClick;
    private Double costPerApplication;

    // Comparison to non-boosted average
    private Double impressionIncreasePercent;
    private Double applicationIncreasePercent;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyBoostStats {
        private String date;
        private Integer impressions;
        private Integer clicks;
        private Integer applications;
    }
}
