package com.exe.skillverse_backend.business_service.controller;

import com.exe.skillverse_backend.business_service.dto.request.CreateJobReviewRequest;
import com.exe.skillverse_backend.business_service.dto.response.JobReviewResponse;
import com.exe.skillverse_backend.business_service.dto.response.UserRatingSummary;
import com.exe.skillverse_backend.business_service.service.JobReviewService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/job-reviews")
@Slf4j
@RequiredArgsConstructor
public class JobReviewController {

    private final JobReviewService jobReviewService;

    /**
     * POST /api/job-reviews - Create a review for completed job
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'RECRUITER')")
    public ResponseEntity<JobReviewResponse> createReview(
            @Valid @RequestBody CreateJobReviewRequest request,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("POST /api/job-reviews - Creating review for application {} by user {}",
                request.getApplicationId(), userId);

        JobReviewResponse response = jobReviewService.createReview(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/job-reviews/{id} - Get review by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<JobReviewResponse> getReviewById(@PathVariable Long id) {
        log.info("GET /api/job-reviews/{} - Fetching review", id);
        JobReviewResponse response = jobReviewService.getReviewById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/job-reviews/application/{applicationId} - Get reviews for an application
     */
    @GetMapping("/application/{applicationId}")
    @PreAuthorize("hasAnyRole('USER', 'RECRUITER')")
    public ResponseEntity<List<JobReviewResponse>> getReviewsForApplication(
            @PathVariable Long applicationId) {

        log.info("GET /api/job-reviews/application/{} - Fetching reviews", applicationId);
        List<JobReviewResponse> response = jobReviewService.getReviewsForApplication(applicationId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/job-reviews/my-reviews - Get reviews written by current user
     */
    @GetMapping("/my-reviews")
    @PreAuthorize("hasAnyRole('USER', 'RECRUITER')")
    public ResponseEntity<List<JobReviewResponse>> getMyReviews(Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("GET /api/job-reviews/my-reviews - Fetching reviews by user {}", userId);

        List<JobReviewResponse> response = jobReviewService.getReviewsWrittenByUser(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/job-reviews/my-reviews/paged - Get my reviews with pagination
     */
    @GetMapping("/my-reviews/paged")
    @PreAuthorize("hasAnyRole('USER', 'RECRUITER')")
    public ResponseEntity<Page<JobReviewResponse>> getMyReviewsPaged(
            Authentication authentication,
            @PageableDefault(size = 10) Pageable pageable) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("GET /api/job-reviews/my-reviews/paged - Fetching reviews paged by user {}", userId);

        Page<JobReviewResponse> response = jobReviewService.getReviewsWrittenByUserPaged(userId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/job-reviews/user/{userId} - Get public reviews for a user
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<JobReviewResponse>> getPublicReviewsForUser(
            @PathVariable Long userId) {

        log.info("GET /api/job-reviews/user/{} - Fetching public reviews", userId);
        List<JobReviewResponse> response = jobReviewService.getPublicReviewsForUser(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/job-reviews/user/{userId}/paged - Get public reviews for a user with pagination
     */
    @GetMapping("/user/{userId}/paged")
    public ResponseEntity<Page<JobReviewResponse>> getPublicReviewsForUserPaged(
            @PathVariable Long userId,
            @PageableDefault(size = 10) Pageable pageable) {

        log.info("GET /api/job-reviews/user/{}/paged - Fetching public reviews paged", userId);
        Page<JobReviewResponse> response = jobReviewService.getPublicReviewsForUserPaged(userId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/job-reviews/user/{userId}/summary - Get rating summary for a user
     */
    @GetMapping("/user/{userId}/summary")
    public ResponseEntity<UserRatingSummary> getUserRatingSummary(@PathVariable Long userId) {

        log.info("GET /api/job-reviews/user/{}/summary - Fetching rating summary", userId);
        UserRatingSummary response = jobReviewService.getUserRatingSummary(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/job-reviews/can-review/{applicationId} - Check if user can write review
     */
    @GetMapping("/can-review/{applicationId}")
    @PreAuthorize("hasAnyRole('USER', 'RECRUITER')")
    public ResponseEntity<Boolean> canWriteReview(
            @PathVariable Long applicationId,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("GET /api/job-reviews/can-review/{} - Checking for user {}", applicationId, userId);

        boolean canReview = jobReviewService.canWriteReview(userId, applicationId);
        return ResponseEntity.ok(canReview);
    }

    /**
     * PATCH /api/job-reviews/{id}/visibility - Update review visibility
     */
    @PatchMapping("/{id}/visibility")
    @PreAuthorize("hasAnyRole('USER', 'RECRUITER')")
    public ResponseEntity<JobReviewResponse> updateReviewVisibility(
            @PathVariable Long id,
            @RequestParam Boolean isPublic,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());
        log.info("PATCH /api/job-reviews/{}/visibility - Setting to {} by user {}", id, isPublic, userId);

        JobReviewResponse response = jobReviewService.updateReviewVisibility(userId, id, isPublic);
        return ResponseEntity.ok(response);
    }
}
