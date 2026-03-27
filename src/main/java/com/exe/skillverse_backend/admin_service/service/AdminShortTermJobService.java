package com.exe.skillverse_backend.admin_service.service;

import com.exe.skillverse_backend.admin_service.dto.request.ResolveDisputeAdminRequest;
import com.exe.skillverse_backend.admin_service.dto.response.AdminJobStatsResponse;
import com.exe.skillverse_backend.business_service.dto.response.ShortTermJobResponse;
import com.exe.skillverse_backend.business_service.entity.Dispute;
import com.exe.skillverse_backend.business_service.entity.JobStatusAuditLog;
import com.exe.skillverse_backend.business_service.entity.Dispute.DisputeStatus;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermJobStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminShortTermJobService {

    /**
     * Get all short-term jobs pending admin approval
     */
    List<ShortTermJobResponse> getPendingJobs();

    /**
     * Approve a short-term job → status changes to PUBLISHED, fee is deducted
     */
    ShortTermJobResponse approveJob(Long jobId);

    /**
     * Reject a short-term job → status changes back to DRAFT
     */
    ShortTermJobResponse rejectJob(Long jobId, String reason);

    // ==================== FULL JOB MANAGEMENT ====================

    /**
     * Get paginated list of all jobs, optionally filtered by status
     */
    Page<ShortTermJobResponse> getAllJobs(ShortTermJobStatus status, Pageable pageable);

    /**
     * Get job details by ID (admin view with full recruiter info)
     */
    ShortTermJobResponse getJobDetail(Long jobId);

    /**
     * Soft delete a job (set status to CANCELLED)
     */
    ShortTermJobResponse deleteJob(Long adminId, Long jobId, String reason);

    /**
     * Ban a job (admin action)
     */
    ShortTermJobResponse banJob(Long adminId, Long jobId, String reason);

    /**
     * Unban a job (admin action)
     */
    ShortTermJobResponse unbanJob(Long adminId, Long jobId);

    // ==================== DISPUTE MANAGEMENT ====================

    /**
     * Get paginated list of all disputes, optionally filtered by status
     */
    Page<Dispute> getAllDisputes(DisputeStatus status, Pageable pageable);

    /**
     * Get dispute detail by ID
     */
    Dispute getDisputeDetail(Long disputeId);

    /**
     * Get audit trail relevant to a dispute.
     */
    List<JobStatusAuditLog> getDisputeAuditLogs(Long disputeId);

    /**
     * Resolve a dispute (admin action) - handles escrow accordingly
     */
    Dispute resolveDispute(Long adminId, Long disputeId, ResolveDisputeAdminRequest request);

    // ==================== DASHBOARD STATS ====================

    /**
     * Get job statistics for admin dashboard
     */
    AdminJobStatsResponse getJobStats();
}
