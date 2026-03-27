package com.exe.skillverse_backend.admin_service.controller;

import com.exe.skillverse_backend.admin_service.service.AdminJobService;
import com.exe.skillverse_backend.business_service.dto.response.JobPostingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @PostMapping("/{jobId}/approve")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
    @Operation(summary = "Approve job", description = "Approve a job posting")
    public ResponseEntity<JobPostingResponse> approveJob(@PathVariable Long jobId) {
        return ResponseEntity.ok(adminJobService.approveJob(jobId));
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
