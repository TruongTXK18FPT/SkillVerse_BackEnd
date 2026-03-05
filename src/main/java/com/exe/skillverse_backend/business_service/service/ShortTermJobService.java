package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.business_service.dto.request.*;
import com.exe.skillverse_backend.business_service.dto.response.ShortTermApplicationResponse;
import com.exe.skillverse_backend.business_service.dto.response.ShortTermJobResponse;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface ShortTermJobService {

    // ==================== JOB POSTING (RECRUITER) ====================
    
    /**
     * Create a new short-term job posting
     */
    ShortTermJobResponse createJob(Long userId, CreateShortTermJobRequest request);

    /**
     * Update an existing short-term job (only if DRAFT or PUBLISHED)
     */
    ShortTermJobResponse updateJob(Long userId, Long jobId, UpdateShortTermJobRequest request);

    /**
     * Change job status
     */
    ShortTermJobResponse changeJobStatus(Long userId, Long jobId, ShortTermJobStatus newStatus, String reason);

    /**
     * Delete a job (only if DRAFT)
     */
    void deleteJob(Long userId, Long jobId);

    /**
     * Get all jobs for current recruiter
     */
    List<ShortTermJobResponse> getMyJobs(Long userId);

    /**
     * Get my jobs with pagination
     */
    Page<ShortTermJobResponse> getMyJobsPaged(Long userId, Pageable pageable);

    // ==================== JOB BROWSING (PUBLIC) ====================
    
    /**
     * Get all published short-term jobs
     */
    List<ShortTermJobResponse> getPublishedJobs();

    /**
     * Get published jobs with pagination
     */
    Page<ShortTermJobResponse> getPublishedJobsPaged(Pageable pageable);

    /**
     * Search jobs with filters
     */
    Page<ShortTermJobResponse> searchJobs(
            String search,
            BigDecimal minBudget,
            BigDecimal maxBudget,
            Boolean isRemote,
            String urgency,
            Pageable pageable
    );

    /**
     * Get job details by ID
     */
    ShortTermJobResponse getJobDetails(Long jobId);

    // ==================== JOB APPLICATION (CANDIDATE) ====================
    
    /**
     * Apply to a short-term job
     */
    ShortTermApplicationResponse applyToJob(Long userId, Long jobId, ApplyShortTermJobRequest request);

    /**
     * Withdraw application
     */
    void withdrawApplication(Long userId, Long applicationId);

    /**
     * Get all applications for current user
     */
    List<ShortTermApplicationResponse> getMyApplications(Long userId);

    /**
     * Get my applications with pagination
     */
    Page<ShortTermApplicationResponse> getMyApplicationsPaged(Long userId, Pageable pageable);

    // ==================== APPLICATION MANAGEMENT (RECRUITER) ====================
    
    /**
     * Get all applicants for a job
     */
    Page<ShortTermApplicationResponse> getJobApplicants(Long userId, Long jobId, Pageable pageable);

    /**
     * Update application status (ACCEPT, REJECT, etc.)
     */
    ShortTermApplicationResponse updateApplicationStatus(
            Long userId,
            Long applicationId,
            UpdateShortTermApplicationStatusRequest request
    );

    /**
     * Select a candidate for the job
     */
    ShortTermApplicationResponse selectCandidate(Long userId, Long jobId, Long applicationId);

    // ==================== WORK SUBMISSION (CANDIDATE) ====================
    
    /**
     * Submit deliverables (bàn giao công việc)
     */
    ShortTermApplicationResponse submitDeliverables(Long userId, SubmitDeliverableRequest request);

    // ==================== WORK REVIEW (RECRUITER) ====================
    
    /**
     * Approve submitted work
     */
    ShortTermApplicationResponse approveWork(Long userId, Long applicationId, String message);

    /**
     * Request revision
     */
    ShortTermApplicationResponse requestRevision(Long userId, RequestRevisionRequest request);

    // ==================== COMPLETION ====================
    
    /**
     * Mark job as completed
     */
    ShortTermJobResponse completeJob(Long userId, Long jobId);

    /**
     * Mark job as paid
     */
    ShortTermJobResponse markAsPaid(Long userId, Long jobId);
}
