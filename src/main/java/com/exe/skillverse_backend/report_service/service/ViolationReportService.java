package com.exe.skillverse_backend.report_service.service;

import com.exe.skillverse_backend.report_service.dto.request.CreateViolationReportRequest;
import com.exe.skillverse_backend.report_service.dto.request.UpdateViolationReportRequest;
import com.exe.skillverse_backend.report_service.dto.response.ViolationReportResponse;
import com.exe.skillverse_backend.report_service.dto.response.ViolationReportStatsResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service interface for violation report operations
 */
public interface ViolationReportService {

    // ==================== User Operations ====================

    /**
     * Create a new violation report (User)
     *
     * @param reporterId the ID of the user submitting the report
     * @param request the report details
     * @return the created report response
     */
    ViolationReportResponse createReport(Long reporterId, CreateViolationReportRequest request);

    /**
     * Get report by ID (User can only view their own reports)
     *
     * @param reportId the report ID
     * @param userId the requesting user's ID
     * @return the report response
     */
    ViolationReportResponse getReportById(Long reportId, Long userId);

    /**
     * Get report by unique code (for tracking)
     *
     * @param reportCode the unique report code
     * @return the report response
     */
    ViolationReportResponse getReportByCode(String reportCode);

    /**
     * Get all reports submitted by a user
     *
     * @param reporterId the reporter's user ID
     * @return list of reports
     */
    List<ViolationReportResponse> getReportsByReporter(Long reporterId);

    /**
     * Get all reports against a specific user
     *
     * @param reportedUserId the reported user's ID
     * @return list of reports
     */
    List<ViolationReportResponse> getReportsAgainstUser(Long reportedUserId);

    // ==================== Admin Operations ====================

    /**
     * Get all reports with filters (Admin)
     *
     * @param status filter by status
     * @param reportType filter by report type
     * @param severity filter by severity
     * @param pageable pagination info
     * @return page of reports
     */
    Page<ViolationReportResponse> getAllReports(
            String status,
            String reportType,
            String severity,
            Pageable pageable);

    /**
     * Get report by ID (Admin - no ownership check)
     *
     * @param reportId the report ID
     * @return the report response
     */
    ViolationReportResponse getReportByIdAdmin(Long reportId);

    /**
     * Get reports assigned to a specific admin
     *
     * @param adminId the admin's user ID
     * @param pageable pagination info
     * @return page of assigned reports
     */
    Page<ViolationReportResponse> getAssignedReports(Long adminId, Pageable pageable);

    /**
     * Update a report (Admin)
     *
     * @param reportId the report ID
     * @param request the update details
     * @return the updated report response
     */
    ViolationReportResponse updateReport(Long reportId, UpdateViolationReportRequest request);

    /**
     * Investigate a report - mark as investigating and assign to admin
     *
     * @param reportId the report ID
     * @param adminId the admin to assign
     * @return the updated report response
     */
    ViolationReportResponse investigateReport(Long reportId, Long adminId);

    /**
     * Resolve a report with an action
     *
     * @param reportId the report ID
     * @param resolutionAction the action taken
     * @param adminNotes notes about the resolution
     * @return the updated report response
     */
    ViolationReportResponse resolveReport(Long reportId, String resolutionAction, String adminNotes);

    /**
     * Dismiss a report
     *
     * @param reportId the report ID
     * @param adminNotes reason for dismissal
     * @return the updated report response
     */
    ViolationReportResponse dismissReport(Long reportId, String adminNotes);

    /**
     * Escalate a report to higher authority
     *
     * @param reportId the report ID
     * @param adminNotes escalation notes
     * @return the updated report response
     */
    ViolationReportResponse escalateReport(Long reportId, String adminNotes);

    /**
     * Get violation report statistics (Admin dashboard)
     *
     * @return statistics response
     */
    ViolationReportStatsResponse getReportStats();

    /**
     * Delete a report (Admin - only for resolved/dismissed reports)
     *
     * @param reportId the report ID
     */
    void deleteReport(Long reportId);

    /**
     * Get all pending high-severity reports (priority queue)
     *
     * @return list of critical pending reports
     */
    List<ViolationReportResponse> getPendingCriticalReports();
}
