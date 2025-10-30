package com.exe.skillverse_backend.business_service.controller;

import com.exe.skillverse_backend.business_service.dto.request.CreateJobRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateJobRequest;
import com.exe.skillverse_backend.business_service.dto.response.JobPostingResponse;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import com.exe.skillverse_backend.business_service.service.JobPostingService;
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
public class JobPostingController {

    private final JobPostingService jobPostingService;

    /**
     * POST /api/jobs - Create new job posting (RECRUITER only)
     */
    @PostMapping
    @PreAuthorize("hasAuthority('RECRUITER')")
    public ResponseEntity<JobPostingResponse> createJob(
            @Valid @RequestBody CreateJobRequest request,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("POST /api/jobs - Creating job by user ID: {}", userId);

        JobPostingResponse response = jobPostingService.createJob(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * PUT /api/jobs/{id} - Update job posting (RECRUITER only, only if IN_PROGRESS
     * or CLOSED)
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('RECRUITER')")
    public ResponseEntity<JobPostingResponse> updateJob(
            @PathVariable Long id,
            @Valid @RequestBody UpdateJobRequest request,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("PUT /api/jobs/{} - Updating job by user ID: {}", id, userId);

        JobPostingResponse response = jobPostingService.updateJob(userId, id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/jobs/{id}/status - Change job status (RECRUITER only)
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('RECRUITER')")
    public ResponseEntity<JobPostingResponse> changeStatus(
            @PathVariable Long id,
            @RequestParam JobStatus status,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("PATCH /api/jobs/{}/status - Changing status to {} by user ID: {}", id, status, userId);

        JobPostingResponse response = jobPostingService.changeStatus(userId, id, status);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/jobs/my-jobs - Get all jobs for current recruiter
     */
    @GetMapping("/my-jobs")
    @PreAuthorize("hasAuthority('RECRUITER')")
    public ResponseEntity<List<JobPostingResponse>> getMyJobs(Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("GET /api/jobs/my-jobs - Fetching jobs for user ID: {}", userId);

        List<JobPostingResponse> response = jobPostingService.getMyJobs(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/jobs/public - Get all public jobs (status = OPEN)
     */
    @GetMapping("/public")
    public ResponseEntity<List<JobPostingResponse>> getPublicJobs() {

        log.info("GET /api/jobs/public - Fetching public jobs");

        List<JobPostingResponse> response = jobPostingService.getPublicJobs();
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/jobs/{id} - Get job details by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<JobPostingResponse> getJobDetails(@PathVariable Long id) {

        log.info("GET /api/jobs/{} - Fetching job details", id);

        JobPostingResponse response = jobPostingService.getJobDetails(id);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/jobs/{id} - Delete job (RECRUITER only, only if IN_PROGRESS)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('RECRUITER')")
    public ResponseEntity<Void> deleteJob(
            @PathVariable Long id,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("DELETE /api/jobs/{} - Deleting job by user ID: {}", id, userId);

        jobPostingService.deleteJob(userId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/jobs/{id}/reopen - Reopen closed job (RECRUITER only, hard delete
     * applications)
     */
    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAuthority('RECRUITER')")
    public ResponseEntity<JobPostingResponse> reopenJob(
            @PathVariable Long id,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("POST /api/jobs/{}/reopen - Reopening job by user ID: {}", id, userId);

        JobPostingResponse response = jobPostingService.reopenJob(userId, id);
        return ResponseEntity.ok(response);
    }
}
