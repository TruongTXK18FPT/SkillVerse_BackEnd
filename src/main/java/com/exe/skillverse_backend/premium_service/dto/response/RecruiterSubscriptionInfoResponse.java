package com.exe.skillverse_backend.premium_service.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecruiterSubscriptionInfoResponse {

    private boolean hasSubscription;
    private String planName;
    private String planDisplayName;
    private BigDecimal planPrice;
    private Integer durationMonths;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Long daysRemaining;
    private boolean autoRenew;

    // Full-time Job posting quota
    private Integer jobPostingLimit;
    private Integer jobPostingUsed;
    private Integer jobPostingRemaining;
    private boolean jobPostingUnlimited;
    private String jobPostingResetInfo;

    // Short-term Job posting quota
    private Integer shortTermJobPostingLimit;
    private Integer shortTermJobPostingUsed;
    private Integer shortTermJobPostingRemaining;
    private boolean shortTermJobPostingUnlimited;
    private String shortTermJobPostingResetInfo;

    // Job Boost quota
    private Integer jobBoostLimit;
    private Integer jobBoostUsed;
    private Integer jobBoostRemaining;
    private String jobBoostResetInfo;

    // Features
    private boolean canHighlightJobs;
    private boolean canUseAICandidateSuggestion;
    private boolean hasPremiumCompanyProfile;
    private boolean hasAnalyticsDashboard;
    private boolean hasCandidateDatabaseAccess;
    private boolean hasAutomatedOutreach;
    private boolean hasApiAccess;
    private boolean hasPrioritySupport;
}
