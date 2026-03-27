package com.exe.skillverse_backend.admin_service.controller;

import com.exe.skillverse_backend.admin_service.dto.request.ResolveDisputeAdminRequest;
import com.exe.skillverse_backend.admin_service.dto.response.AdminJobStatsResponse;
import com.exe.skillverse_backend.admin_service.service.AdminShortTermJobService;
import com.exe.skillverse_backend.business_service.dto.response.ShortTermJobResponse;
import com.exe.skillverse_backend.business_service.entity.Dispute;
import com.exe.skillverse_backend.business_service.entity.Dispute.DisputeStatus;
import com.exe.skillverse_backend.business_service.entity.JobStatusAuditLog;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermJobStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/short-term-jobs")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Admin Short-Term Job Management", description = "Full admin panel for short-term job management")
public class AdminShortTermJobController {

    private final AdminShortTermJobService adminShortTermJobService;

    // ==================== EXISTING APPROVAL ENDPOINTS ====================

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
    @Operation(summary = "Get pending short-term jobs", description = "List all short-term jobs waiting for approval")
    public ResponseEntity<?> getPendingJobs() {
        return ResponseEntity.ok(adminShortTermJobService.getPendingJobs());
    }

    @PostMapping("/{jobId}/approve")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
    @Operation(summary = "Approve short-term job", description = "Approve a short-term job posting")
    public ResponseEntity<ShortTermJobResponse> approveJob(@PathVariable Long jobId) {
        return ResponseEntity.ok(adminShortTermJobService.approveJob(jobId));
    }

    @PostMapping("/{jobId}/reject")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
    @Operation(summary = "Reject short-term job", description = "Reject a short-term job posting")
    public ResponseEntity<ShortTermJobResponse> rejectJob(
            @PathVariable Long jobId,
            @RequestParam(required = false, defaultValue = "Khong dat yeu cau") String reason) {
        return ResponseEntity.ok(adminShortTermJobService.rejectJob(jobId, reason));
    }

    // ==================== FULL JOB MANAGEMENT ====================

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
    @Operation(summary = "Get all short-term jobs", description = "Paginated list of all short-term jobs with optional status filter")
    public ResponseEntity<Page<ShortTermJobResponse>> getAllJobs(
            @RequestParam(required = false) ShortTermJobStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(adminShortTermJobService.getAllJobs(status, pageable));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
    @Operation(summary = "Get job statistics", description = "Dashboard statistics for all short-term jobs")
    public ResponseEntity<AdminJobStatsResponse> getJobStats() {
        return ResponseEntity.ok(adminShortTermJobService.getJobStats());
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
    @Operation(summary = "Get job detail", description = "Get detailed information about a specific job")
    public ResponseEntity<ShortTermJobResponse> getJobDetail(@PathVariable Long jobId) {
        return ResponseEntity.ok(adminShortTermJobService.getJobDetail(jobId));
    }

    @DeleteMapping("/{jobId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
    @Operation(summary = "Delete job", description = "Soft delete a job (sets status to CANCELLED)")
    public ResponseEntity<ShortTermJobResponse> deleteJob(
            @PathVariable Long jobId,
            @RequestParam(required = false) String reason,
            Authentication authentication) {
        Long adminId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(adminShortTermJobService.deleteJob(adminId, jobId, reason));
    }

    @PostMapping("/{jobId}/ban")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
    @Operation(summary = "Ban job", description = "Ban a job and close it")
    public ResponseEntity<ShortTermJobResponse> banJob(
            @PathVariable Long jobId,
            @RequestParam(required = false, defaultValue = "Vi pham quy dinh") String reason,
            Authentication authentication) {
        Long adminId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(adminShortTermJobService.banJob(adminId, jobId, reason));
    }

    @PostMapping("/{jobId}/unban")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
    @Operation(summary = "Unban job", description = "Unban a previously banned job")
    public ResponseEntity<ShortTermJobResponse> unbanJob(
            @PathVariable Long jobId,
            Authentication authentication) {
        Long adminId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(adminShortTermJobService.unbanJob(adminId, jobId));
    }

    // ==================== DISPUTE MANAGEMENT ====================

    @GetMapping("/disputes")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
    @Operation(summary = "Get all disputes", description = "Paginated list of all disputes with optional status filter")
    public ResponseEntity<Page<Dispute>> getAllDisputes(
            @RequestParam(required = false) DisputeStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(adminShortTermJobService.getAllDisputes(status, pageable));
    }

    @GetMapping("/disputes/{disputeId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
    @Operation(summary = "Get dispute detail", description = "Get detailed information about a specific dispute")
    public ResponseEntity<Dispute> getDisputeDetail(@PathVariable Long disputeId) {
        return ResponseEntity.ok(adminShortTermJobService.getDisputeDetail(disputeId));
    }

    @GetMapping("/disputes/{disputeId}/audit-logs")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
    @Operation(summary = "Get dispute audit logs", description = "Get job/application audit trail relevant to a dispute")
    public ResponseEntity<java.util.List<JobStatusAuditLog>> getDisputeAuditLogs(@PathVariable Long disputeId) {
        return ResponseEntity.ok(adminShortTermJobService.getDisputeAuditLogs(disputeId));
    }

    @PostMapping("/disputes/{disputeId}/resolve")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
    @Operation(summary = "Resolve dispute", description = "Admin resolves a dispute with the chosen resolution")
    public ResponseEntity<Dispute> resolveDispute(
            @PathVariable Long disputeId,
            @RequestBody ResolveDisputeAdminRequest request,
            Authentication authentication) {
        Long adminId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(adminShortTermJobService.resolveDispute(adminId, disputeId, request));
    }
}
