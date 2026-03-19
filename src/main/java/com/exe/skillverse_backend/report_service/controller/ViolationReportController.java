package com.exe.skillverse_backend.report_service.controller;

import com.exe.skillverse_backend.report_service.dto.request.CreateViolationReportRequest;
import com.exe.skillverse_backend.report_service.dto.request.UpdateViolationReportRequest;
import com.exe.skillverse_backend.report_service.dto.response.ViolationReportResponse;
import com.exe.skillverse_backend.report_service.dto.response.ViolationReportStatsResponse;
import com.exe.skillverse_backend.report_service.service.ViolationReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Violation Report operations.
 * Provides endpoints for both users to submit reports and admins to manage them.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Violation Reports", description = "APIs for managing violation reports")
public class ViolationReportController {

    private final ViolationReportService reportService;

    // ==================== User Endpoints ====================

    /**
     * Create a new violation report
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create report", description = "Submit a new violation report against another user")
    public ResponseEntity<ViolationReportResponse> createReport(
            @RequestParam Long reporterId,
            @Valid @RequestBody CreateViolationReportRequest request) {
        log.info("User {} creating violation report against user {}", reporterId, request.getReportedUserId());
        ViolationReportResponse response = reportService.createReport(reporterId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get my submitted reports
     */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my reports", description = "Get all violation reports submitted by the authenticated user")
    public ResponseEntity<List<ViolationReportResponse>> getMyReports(@RequestParam Long userId) {
        List<ViolationReportResponse> reports = reportService.getReportsByReporter(userId);
        return ResponseEntity.ok(reports);
    }

    /**
     * Get a specific report by ID (user must be the reporter)
     */
    @GetMapping("/{reportId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get report by ID", description = "Get a specific report. Users can only view their own reports.")
    public ResponseEntity<ViolationReportResponse> getReportById(
            @PathVariable Long reportId,
            @RequestParam Long userId) {
        ViolationReportResponse report = reportService.getReportById(reportId, userId);
        return ResponseEntity.ok(report);
    }

    /**
     * Get report by tracking code
     */
    @GetMapping("/track/{reportCode}")
    @Operation(summary = "Track report", description = "Get report status by tracking code")
    public ResponseEntity<ViolationReportResponse> trackReport(@PathVariable String reportCode) {
        ViolationReportResponse report = reportService.getReportByCode(reportCode);
        return ResponseEntity.ok(report);
    }

    // ==================== Admin Endpoints ====================

    /**
     * Get all reports with filters (Admin)
     */
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all reports (Admin)", description = "Get all violation reports with optional filters")
    public ResponseEntity<Page<ViolationReportResponse>> getAllReports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ViolationReportResponse> reports = reportService.getAllReports(status, reportType, severity, pageable);
        return ResponseEntity.ok(reports);
    }

    /**
     * Get report by ID (Admin - no ownership check)
     */
    @GetMapping("/admin/{reportId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get report by ID (Admin)", description = "Get detailed report information")
    public ResponseEntity<ViolationReportResponse> getReportByIdAdmin(@PathVariable Long reportId) {
        ViolationReportResponse report = reportService.getReportByIdAdmin(reportId);
        return ResponseEntity.ok(report);
    }

    /**
     * Get reports assigned to admin
     */
    @GetMapping("/admin/assigned/{adminId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get assigned reports", description = "Get reports assigned to a specific admin")
    public ResponseEntity<Page<ViolationReportResponse>> getAssignedReports(
            @PathVariable Long adminId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ViolationReportResponse> reports = reportService.getAssignedReports(adminId, pageable);
        return ResponseEntity.ok(reports);
    }

    /**
     * Get pending critical reports
     */
    @GetMapping("/admin/critical")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get critical reports", description = "Get all pending high-severity reports")
    public ResponseEntity<List<ViolationReportResponse>> getCriticalReports() {
        List<ViolationReportResponse> reports = reportService.getPendingCriticalReports();
        return ResponseEntity.ok(reports);
    }

    /**
     * Get reports against a specific user
     */
    @GetMapping("/admin/against/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get reports against user", description = "Get all reports filed against a specific user")
    public ResponseEntity<List<ViolationReportResponse>> getReportsAgainstUser(@PathVariable Long userId) {
        List<ViolationReportResponse> reports = reportService.getReportsAgainstUser(userId);
        return ResponseEntity.ok(reports);
    }

    /**
     * Update a report (Admin)
     */
    @PutMapping("/admin/{reportId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update report (Admin)", description = "Update report status, severity, notes, or assignment")
    public ResponseEntity<ViolationReportResponse> updateReport(
            @PathVariable Long reportId,
            @RequestBody UpdateViolationReportRequest request) {
        ViolationReportResponse report = reportService.updateReport(reportId, request);
        return ResponseEntity.ok(report);
    }

    /**
     * Start investigating a report
     */
    @PostMapping("/admin/{reportId}/investigate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Investigate report", description = "Mark a report as under investigation and assign to admin")
    public ResponseEntity<ViolationReportResponse> investigateReport(
            @PathVariable Long reportId,
            @RequestParam Long adminId) {
        ViolationReportResponse report = reportService.investigateReport(reportId, adminId);
        return ResponseEntity.ok(report);
    }

    /**
     * Resolve a report
     */
    @PostMapping("/admin/{reportId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Resolve report", description = "Resolve a report with a specific action")
    public ResponseEntity<ViolationReportResponse> resolveReport(
            @PathVariable Long reportId,
            @RequestBody Map<String, String> body) {
        String resolutionAction = body.get("resolutionAction");
        String adminNotes = body.get("adminNotes");
        ViolationReportResponse report = reportService.resolveReport(reportId, resolutionAction, adminNotes);
        return ResponseEntity.ok(report);
    }

    /**
     * Dismiss a report
     */
    @PostMapping("/admin/{reportId}/dismiss")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Dismiss report", description = "Dismiss a report as invalid or unfounded")
    public ResponseEntity<ViolationReportResponse> dismissReport(
            @PathVariable Long reportId,
            @RequestBody Map<String, String> body) {
        String adminNotes = body.get("adminNotes");
        ViolationReportResponse report = reportService.dismissReport(reportId, adminNotes);
        return ResponseEntity.ok(report);
    }

    /**
     * Escalate a report
     */
    @PostMapping("/admin/{reportId}/escalate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Escalate report", description = "Escalate a report to higher authority")
    public ResponseEntity<ViolationReportResponse> escalateReport(
            @PathVariable Long reportId,
            @RequestBody Map<String, String> body) {
        String adminNotes = body.get("adminNotes");
        ViolationReportResponse report = reportService.escalateReport(reportId, adminNotes);
        return ResponseEntity.ok(report);
    }

    /**
     * Get report statistics (Admin dashboard)
     */
    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get report statistics", description = "Get violation report statistics for admin dashboard")
    public ResponseEntity<ViolationReportStatsResponse> getReportStats() {
        ViolationReportStatsResponse stats = reportService.getReportStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Delete a report (Admin)
     */
    @DeleteMapping("/admin/{reportId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete report (Admin)", description = "Delete a resolved or dismissed report")
    public ResponseEntity<Void> deleteReport(@PathVariable Long reportId) {
        reportService.deleteReport(reportId);
        return ResponseEntity.noContent().build();
    }
}
