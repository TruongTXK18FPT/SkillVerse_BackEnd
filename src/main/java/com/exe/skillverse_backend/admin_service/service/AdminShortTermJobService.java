package com.exe.skillverse_backend.admin_service.service;

import com.exe.skillverse_backend.business_service.dto.response.ShortTermJobResponse;

import java.util.List;

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
}
