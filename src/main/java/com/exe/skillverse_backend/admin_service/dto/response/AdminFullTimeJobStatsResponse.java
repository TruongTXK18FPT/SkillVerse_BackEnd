package com.exe.skillverse_backend.admin_service.dto.response;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminFullTimeJobStatsResponse {
    private long totalJobs;
    private long draftCount;
    private long pendingApprovalCount;
    private long openCount;
    private long closedCount;
    private long rejectedCount;
    private long remoteCount;
    private long onsiteCount;
    private long highlightedCount;
    private long negotiableCount;
    private long totalApplicants;
    private double averageApplicantsPerJob;
    private long totalBudgetFloor;
    private long totalBudgetCeiling;
    private Map<String, Long> byStatus;
    private Map<String, Long> byJobType;
    private Map<String, Long> byExperienceLevel;
    private Map<String, Long> byWorkMode;
    private Map<String, Long> applicationByStatus;
}
