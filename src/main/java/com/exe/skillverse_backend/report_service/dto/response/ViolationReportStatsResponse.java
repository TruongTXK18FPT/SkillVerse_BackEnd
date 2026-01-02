package com.exe.skillverse_backend.report_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response DTO for violation report statistics (Admin dashboard)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViolationReportStatsResponse {

    /**
     * Total number of reports
     */
    private long totalReports;

    /**
     * Number of new reports (pending)
     */
    private long newReports;

    /**
     * Number of reports being investigated
     */
    private long investigatingReports;

    /**
     * Number of resolved reports
     */
    private long resolvedReports;

    /**
     * Number of dismissed reports
     */
    private long dismissedReports;

    /**
     * Number of high severity (critical) reports
     */
    private long criticalReports;

    /**
     * Number of reports this week
     */
    private long reportsThisWeek;

    /**
     * Response rate percentage (resolved + dismissed / total)
     */
    private double responseRate;

    /**
     * Breakdown of reports by type
     */
    private Map<String, Long> reportsByType;

    /**
     * Breakdown of reports by severity
     */
    private Map<String, Long> reportsBySeverity;
}
