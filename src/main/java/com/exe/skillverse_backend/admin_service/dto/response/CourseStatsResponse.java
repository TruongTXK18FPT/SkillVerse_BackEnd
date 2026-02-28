package com.exe.skillverse_backend.admin_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for admin course dashboard statistics.
 * Returns counts of courses grouped by status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseStatsResponse {
    private long totalPending;
    private long totalApproved;   // PUBLIC
    private long totalRejected;
    private long totalSuspended;
    private long totalDraft;
    private long totalArchived;
    private long totalAll;
}
