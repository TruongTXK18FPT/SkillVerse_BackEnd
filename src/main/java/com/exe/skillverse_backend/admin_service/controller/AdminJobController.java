package com.exe.skillverse_backend.admin_service.controller;

import com.exe.skillverse_backend.admin_service.dto.response.AdminFullTimeJobStatsResponse;
import com.exe.skillverse_backend.admin_service.service.AdminJobService;
import com.exe.skillverse_backend.business_service.dto.response.JobPostingResponse;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/jobs")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Admin Job Management", description = "Endpoints for admin to manage job postings")
public class AdminJobController {

    private final AdminJobService adminJobService;

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
    @Operation(summary = "Get pending jobs", description = "List all jobs waiting for approval")
    public ResponseEntity<List<JobPostingResponse>> getPendingJobs() {
        return ResponseEntity.ok(adminJobService.getPendingJobs());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
    @Operation(summary = "Get all full-time jobs", description = "Paginated list of all full-time jobs with optional status filter")
    public ResponseEntity<Page<JobPostingResponse>> getAllJobs(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(adminJobService.getAllJobs(status, pageable));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
    @Operation(summary = "Get full-time job statistics", description = "Dashboard statistics for all full-time jobs")
    public ResponseEntity<AdminFullTimeJobStatsResponse> getJobStats() {
        return ResponseEntity.ok(adminJobService.getJobStats());
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
    @Operation(summary = "Get full-time job detail", description = "Detailed information for a full-time job posting")
    public ResponseEntity<JobPostingResponse> getJobDetail(@PathVariable Long jobId) {
        return ResponseEntity.ok(adminJobService.getJobDetail(jobId));
    }

    @PostMapping("/{jobId}/approve")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
    @Operation(summary = "Approve job", description = "Approve a job posting")
    public ResponseEntity<JobPostingResponse> approveJob(@PathVariable Long jobId) {
        return ResponseEntity.ok(adminJobService.approveJob(jobId));
    }

    @PostMapping("/{jobId}/close")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
    @Operation(summary = "Close full-time job", description = "Close a full-time job posting from the admin panel")
    public ResponseEntity<JobPostingResponse> closeJob(
            @PathVariable Long jobId,
            @RequestParam(required = false) String reason,
            Authentication authentication) {
        Long adminId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(adminJobService.closeJob(adminId, jobId, reason));
    }

    @PostMapping("/{jobId}/reject")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
    @Operation(summary = "Reject job", description = "Reject a job posting and refund fee")
    public ResponseEntity<JobPostingResponse> rejectJob(
            @PathVariable Long jobId,
            @RequestParam(required = false, defaultValue = "Policy violation") String reason) {
        return ResponseEntity.ok(adminJobService.rejectJob(jobId, reason));
    }
}
