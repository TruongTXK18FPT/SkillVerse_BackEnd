package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.business_service.dto.request.CreateJobReviewRequest;
import com.exe.skillverse_backend.business_service.dto.response.JobReviewResponse;
import com.exe.skillverse_backend.business_service.dto.response.UserRatingSummary;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface JobReviewService {

    /**
     * Create a review for completed job
     * Only allowed after job is COMPLETED and PAID
     */
    JobReviewResponse createReview(Long userId, CreateJobReviewRequest request);

    /**
     * Get review by ID
     */
    JobReviewResponse getReviewById(Long reviewId);

    /**
     * Get reviews for an application
     */
    List<JobReviewResponse> getReviewsForApplication(Long applicationId);

    /**
     * Get reviews written by user
     */
    List<JobReviewResponse> getReviewsWrittenByUser(Long userId);

    /**
     * Get reviews written by user with pagination
     */
    Page<JobReviewResponse> getReviewsWrittenByUserPaged(Long userId, Pageable pageable);

    /**
     * Get public reviews for a user (as reviewee)
     */
    List<JobReviewResponse> getPublicReviewsForUser(Long userId);

    /**
     * Get public reviews with pagination
     */
    Page<JobReviewResponse> getPublicReviewsForUserPaged(Long userId, Pageable pageable);

    /**
     * Get rating summary for a user
     */
    UserRatingSummary getUserRatingSummary(Long userId);

    /**
     * Check if user can write review for application
     */
    boolean canWriteReview(Long userId, Long applicationId);

    /**
     * Update review visibility
     */
    JobReviewResponse updateReviewVisibility(Long userId, Long reviewId, Boolean isPublic);
}
