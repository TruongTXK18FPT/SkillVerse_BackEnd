package com.exe.skillverse_backend.business_service.controller;

import com.exe.skillverse_backend.business_service.dto.request.ApplyJobRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateApplicationStatusRequest;
import com.exe.skillverse_backend.business_service.dto.response.JobApplicationResponse;
import com.exe.skillverse_backend.business_service.service.JobApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@Slf4j
@RequiredArgsConstructor
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;

    /**
     * POST /api/jobs/{jobId}/apply - Apply to a job (USER only)
     */
    @PostMapping("/{jobId}/apply")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<JobApplicationResponse> applyToJob(
            @PathVariable Long jobId,
            @Valid @RequestBody ApplyJobRequest request,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("POST /api/jobs/{}/apply - User ID {} applying to job", jobId, userId);

        JobApplicationResponse response = jobApplicationService.applyToJob(userId, jobId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/jobs/my-applications - Get all applications for current user
     * Note: Both USER and RECRUITER can access (USER to see their applications, RECRUITER might accidentally check but will get empty list)
     */
    @GetMapping("/my-applications")
    @PreAuthorize("hasAnyAuthority('USER', 'RECRUITER')")
    public ResponseEntity<List<JobApplicationResponse>> getMyApplications(Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("GET /api/jobs/my-applications - Fetching applications for user ID: {}", userId);

        List<JobApplicationResponse> response = jobApplicationService.getMyApplications(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/jobs/{jobId}/applicants - Get all applicants for a job (RECRUITER
     * only)
     */
    @GetMapping("/{jobId}/applicants")
    @PreAuthorize("hasAuthority('RECRUITER')")
    public ResponseEntity<List<JobApplicationResponse>> getJobApplicants(
            @PathVariable Long jobId,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("GET /api/jobs/{}/applicants - Fetching applicants by recruiter user ID: {}", jobId, userId);

        List<JobApplicationResponse> response = jobApplicationService.getJobApplicants(userId, jobId);
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/jobs/applications/{applicationId}/status - Update application
     * status (RECRUITER only)
     */
    @PatchMapping("/applications/{applicationId}/status")
    @PreAuthorize("hasAuthority('RECRUITER')")
    public ResponseEntity<JobApplicationResponse> updateApplicationStatus(
            @PathVariable Long applicationId,
            @Valid @RequestBody UpdateApplicationStatusRequest request,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("PATCH /api/jobs/applications/{}/status - Updating status to {} by user ID: {}",
                applicationId, request.getStatus(), userId);

        JobApplicationResponse response = jobApplicationService.updateApplicationStatus(userId, applicationId, request);
        return ResponseEntity.ok(response);
    }
}
