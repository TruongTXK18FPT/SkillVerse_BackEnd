package com.exe.skillverse_backend.business_service.controller;

import com.exe.skillverse_backend.business_service.dto.request.*;
import com.exe.skillverse_backend.business_service.dto.response.ShortTermApplicationResponse;
import com.exe.skillverse_backend.business_service.dto.response.ShortTermJobResponse;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermJobStatus;
import com.exe.skillverse_backend.business_service.service.ShortTermJobService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/short-term-jobs")
@Slf4j
@RequiredArgsConstructor
public class ShortTermJobController {

    private final ShortTermJobService shortTermJobService;

    // ==================== JOB POSTING (RECRUITER) ====================

    /**
     * POST /api/short-term-jobs - Create new short-term job posting
     */
    @PostMapping
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<ShortTermJobResponse> createJob(
            @Valid @RequestBody CreateShortTermJobRequest request,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("POST /api/short-term-jobs - Creating job by user ID: {}", userId);

        ShortTermJobResponse response = shortTermJobService.createJob(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * PUT /api/short-term-jobs/{id} - Update short-term job posting
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<ShortTermJobResponse> updateJob(
            @PathVariable Long id,
            @Valid @RequestBody UpdateShortTermJobRequest request,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("PUT /api/short-term-jobs/{} - Updating job by user ID: {}", id, userId);

        ShortTermJobResponse response = shortTermJobService.updateJob(userId, id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/short-term-jobs/{id}/status - Change job status
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<ShortTermJobResponse> changeJobStatus(
            @PathVariable Long id,
            @RequestParam ShortTermJobStatus status,
            @RequestParam(required = false) String reason,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("PATCH /api/short-term-jobs/{}/status - Changing status to {} by user ID: {}", id, status, userId);

        ShortTermJobResponse response = shortTermJobService.changeJobStatus(userId, id, status, reason);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/short-term-jobs/{id} - Delete job
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<Void> deleteJob(
            @PathVariable Long id,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("DELETE /api/short-term-jobs/{} - Deleting job by user ID: {}", id, userId);

        shortTermJobService.deleteJob(userId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/short-term-jobs/my-jobs - Get all jobs for current recruiter
     */
    @GetMapping("/my-jobs")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<List<ShortTermJobResponse>> getMyJobs(Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("GET /api/short-term-jobs/my-jobs - Fetching jobs for user ID: {}", userId);

        List<ShortTermJobResponse> response = shortTermJobService.getMyJobs(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/short-term-jobs/my-jobs/paged - Get my jobs with pagination
     */
    @GetMapping("/my-jobs/paged")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<Page<ShortTermJobResponse>> getMyJobsPaged(
            Authentication authentication,
            @PageableDefault(size = 10) Pageable pageable) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("GET /api/short-term-jobs/my-jobs/paged - Fetching jobs paged for user ID: {}", userId);

        Page<ShortTermJobResponse> response = shortTermJobService.getMyJobsPaged(userId, pageable);
        return ResponseEntity.ok(response);
    }

    // ==================== JOB BROWSING (PUBLIC) ====================

    /**
     * GET /api/short-term-jobs/public - Get all published jobs
     */
    @GetMapping("/public")
    public ResponseEntity<List<ShortTermJobResponse>> getPublishedJobs() {
        log.info("GET /api/short-term-jobs/public - Fetching published jobs");
        List<ShortTermJobResponse> response = shortTermJobService.getPublishedJobs();
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/short-term-jobs/public/paged - Get published jobs with pagination
     */
    @GetMapping("/public/paged")
    public ResponseEntity<Page<ShortTermJobResponse>> getPublishedJobsPaged(
            @PageableDefault(size = 10) Pageable pageable) {

        log.info("GET /api/short-term-jobs/public/paged - Fetching published jobs paged");
        Page<ShortTermJobResponse> response = shortTermJobService.getPublishedJobsPaged(pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/short-term-jobs/search - Search jobs with filters
     */
    @GetMapping("/search")
    public ResponseEntity<Page<ShortTermJobResponse>> searchJobs(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) BigDecimal minBudget,
            @RequestParam(required = false) BigDecimal maxBudget,
            @RequestParam(required = false) Boolean isRemote,
            @RequestParam(required = false) String urgency,
            @PageableDefault(size = 10) Pageable pageable) {

        log.info("GET /api/short-term-jobs/search - Searching jobs");
        Page<ShortTermJobResponse> response = shortTermJobService.searchJobs(
                search, minBudget, maxBudget, isRemote, urgency, pageable
        );
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/short-term-jobs/{id} - Get job details
     */
    @GetMapping("/{id}")
    public ResponseEntity<ShortTermJobResponse> getJobDetails(@PathVariable Long id) {
        log.info("GET /api/short-term-jobs/{} - Fetching job details", id);
        ShortTermJobResponse response = shortTermJobService.getJobDetails(id);
        return ResponseEntity.ok(response);
    }

    // ==================== JOB APPLICATION (CANDIDATE) ====================

    /**
     * POST /api/short-term-jobs/{id}/apply - Apply to a job
     */
    @PostMapping("/{id}/apply")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ShortTermApplicationResponse> applyToJob(
            @PathVariable Long id,
            @Valid @RequestBody ApplyShortTermJobRequest request,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("POST /api/short-term-jobs/{}/apply - User {} applying", id, userId);

        ShortTermApplicationResponse response = shortTermJobService.applyToJob(userId, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * DELETE /api/short-term-jobs/applications/{id}/withdraw - Withdraw application
     */
    @DeleteMapping("/applications/{id}/withdraw")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> withdrawApplication(
            @PathVariable Long id,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("DELETE /api/short-term-jobs/applications/{}/withdraw - User {} withdrawing", id, userId);

        shortTermJobService.withdrawApplication(userId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/short-term-jobs/my-applications - Get all applications for current user
     */
    @GetMapping("/my-applications")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<ShortTermApplicationResponse>> getMyApplications(
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("GET /api/short-term-jobs/my-applications - Fetching applications for user {}", userId);

        List<ShortTermApplicationResponse> response = shortTermJobService.getMyApplications(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/short-term-jobs/my-applications/paged - Get my applications with pagination
     */
    @GetMapping("/my-applications/paged")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<ShortTermApplicationResponse>> getMyApplicationsPaged(
            Authentication authentication,
            @PageableDefault(size = 10) Pageable pageable) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("GET /api/short-term-jobs/my-applications/paged - Fetching applications paged for user {}", userId);

        Page<ShortTermApplicationResponse> response = shortTermJobService.getMyApplicationsPaged(userId, pageable);
        return ResponseEntity.ok(response);
    }

    // ==================== APPLICATION MANAGEMENT (RECRUITER) ====================

    /**
     * GET /api/short-term-jobs/{id}/applicants - Get all applicants for a job
     */
    @GetMapping("/{id}/applicants")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<Page<ShortTermApplicationResponse>> getJobApplicants(
            @PathVariable Long id,
            Authentication authentication,
            @PageableDefault(size = 10) Pageable pageable) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("GET /api/short-term-jobs/{}/applicants - Fetching applicants for job", id);

        Page<ShortTermApplicationResponse> response = shortTermJobService.getJobApplicants(userId, id, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/short-term-jobs/applications/{id}/status - Update application status
     */
    @PatchMapping("/applications/{id}/status")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<ShortTermApplicationResponse> updateApplicationStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateShortTermApplicationStatusRequest request,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("PATCH /api/short-term-jobs/applications/{}/status - Updating to {}", id, request.getStatus());

        ShortTermApplicationResponse response = shortTermJobService.updateApplicationStatus(userId, id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/short-term-jobs/{jobId}/select-candidate/{applicationId} - Select a candidate
     */
    @PostMapping("/{jobId}/select-candidate/{applicationId}")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<ShortTermApplicationResponse> selectCandidate(
            @PathVariable Long jobId,
            @PathVariable Long applicationId,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("POST /api/short-term-jobs/{}/select-candidate/{}", jobId, applicationId);

        ShortTermApplicationResponse response = shortTermJobService.selectCandidate(userId, jobId, applicationId);
        return ResponseEntity.ok(response);
    }

    // ==================== WORK SUBMISSION (CANDIDATE) ====================

    /**
     * POST /api/short-term-jobs/applications/submit-deliverables - Submit deliverables
     */
    @PostMapping("/applications/submit-deliverables")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ShortTermApplicationResponse> submitDeliverables(
            @Valid @RequestBody SubmitDeliverableRequest request,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("POST /api/short-term-jobs/applications/submit-deliverables - Application {}", 
                request.getApplicationId());

        ShortTermApplicationResponse response = shortTermJobService.submitDeliverables(userId, request);
        return ResponseEntity.ok(response);
    }

    // ==================== WORK REVIEW (RECRUITER) ====================

    /**
     * POST /api/short-term-jobs/applications/{id}/approve - Approve submitted work
     */
    @PostMapping("/applications/{id}/approve")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<ShortTermApplicationResponse> approveWork(
            @PathVariable Long id,
            @RequestParam(required = false) String message,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("POST /api/short-term-jobs/applications/{}/approve - Approving work", id);

        ShortTermApplicationResponse response = shortTermJobService.approveWork(userId, id, message);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/short-term-jobs/applications/request-revision - Request revision
     */
    @PostMapping("/applications/request-revision")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<ShortTermApplicationResponse> requestRevision(
            @Valid @RequestBody RequestRevisionRequest request,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("POST /api/short-term-jobs/applications/request-revision - Application {}", 
                request.getApplicationId());

        ShortTermApplicationResponse response = shortTermJobService.requestRevision(userId, request);
        return ResponseEntity.ok(response);
    }

    // ==================== COMPLETION ====================

    /**
     * POST /api/short-term-jobs/{id}/complete - Mark job as completed
     */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<ShortTermJobResponse> completeJob(
            @PathVariable Long id,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("POST /api/short-term-jobs/{}/complete - Completing job", id);

        ShortTermJobResponse response = shortTermJobService.completeJob(userId, id);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/short-term-jobs/{id}/mark-paid - Mark job as paid
     */
    @PostMapping("/{id}/mark-paid")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<ShortTermJobResponse> markAsPaid(
            @PathVariable Long id,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("POST /api/short-term-jobs/{}/mark-paid - Marking as paid", id);

        ShortTermJobResponse response = shortTermJobService.markAsPaid(userId, id);
        return ResponseEntity.ok(response);
    }
}
