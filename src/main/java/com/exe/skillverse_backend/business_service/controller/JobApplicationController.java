package com.exe.skillverse_backend.business_service.controller;

import com.exe.skillverse_backend.business_service.dto.request.ApplyJobRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateApplicationStatusRequest;
import com.exe.skillverse_backend.business_service.dto.response.JobApplicationResponse;
import com.exe.skillverse_backend.business_service.service.JobApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    @PreAuthorize("hasRole('USER')")
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
     */
    @GetMapping("/my-applications")
    @PreAuthorize("hasRole('USER')")
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
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<Page<JobApplicationResponse>> getJobApplicants(
            @PathVariable Long jobId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "3") int size,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("GET /api/jobs/{}/applicants - Fetching applicants by recruiter user ID: {}", jobId, userId);

        Pageable pageable = PageRequest.of(page, size);
        Page<JobApplicationResponse> response = jobApplicationService.getJobApplicants(userId, jobId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/jobs/applications/{applicationId}/status - Update application
     * status (RECRUITER only)
     */
    @PatchMapping("/applications/{applicationId}/status")
    @PreAuthorize("hasRole('RECRUITER')")
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
