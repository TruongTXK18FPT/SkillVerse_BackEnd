package com.exe.skillverse_backend.admin_service.controller;

import com.exe.skillverse_backend.admin_service.service.AdminShortTermJobService;
import com.exe.skillverse_backend.business_service.dto.response.ShortTermJobResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/short-term-jobs")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Admin Short-Term Job Management", description = "Endpoints for admin to approve/reject short-term job postings")
public class AdminShortTermJobController {

    private final AdminShortTermJobService adminShortTermJobService;

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN') or hasRole('RECRUITMENT_ADMIN')")
    @Operation(summary = "Get pending short-term jobs", description = "List all short-term jobs waiting for approval")
    public ResponseEntity<List<ShortTermJobResponse>> getPendingJobs() {
        return ResponseEntity.ok(adminShortTermJobService.getPendingJobs());
    }

    @PostMapping("/{jobId}/approve")
    @PreAuthorize("hasRole('ADMIN') or hasRole('RECRUITMENT_ADMIN')")
    @Operation(summary = "Approve short-term job", description = "Approve a short-term job posting, deduct fee, and publish")
    public ResponseEntity<ShortTermJobResponse> approveJob(@PathVariable Long jobId) {
        return ResponseEntity.ok(adminShortTermJobService.approveJob(jobId));
    }

    @PostMapping("/{jobId}/reject")
    @PreAuthorize("hasRole('ADMIN') or hasRole('RECRUITMENT_ADMIN')")
    @Operation(summary = "Reject short-term job", description = "Reject a short-term job posting")
    public ResponseEntity<ShortTermJobResponse> rejectJob(
            @PathVariable Long jobId,
            @RequestParam(required = false, defaultValue = "Không đạt yêu cầu") String reason) {
        return ResponseEntity.ok(adminShortTermJobService.rejectJob(jobId, reason));
    }
}
