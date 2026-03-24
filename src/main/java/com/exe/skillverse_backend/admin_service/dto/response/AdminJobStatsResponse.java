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
public class AdminJobStatsResponse {
    private long totalJobs;
    private long draftCount;
    private long pendingApprovalCount;
    private long publishedCount;
    private long inProgressCount;
    private long completedCount;
    private long paidCount;
    private long cancelledCount;
    private long disputedCount;
    private long closedCount;
    private long rejectedCount;
    private Map<String, Long> byStatus;
    private Map<String, Long> byUrgency;
}
