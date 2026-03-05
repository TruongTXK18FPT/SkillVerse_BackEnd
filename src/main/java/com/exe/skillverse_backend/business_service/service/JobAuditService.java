package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.business_service.entity.JobStatusAuditLog;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermApplicationStatus;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermJobStatus;

import java.util.List;

public interface JobAuditService {

    /**
     * Log job status change
     */
    void logJobStatusChange(
            Long jobId,
            String previousStatus,
            String newStatus,
            Long userId,
            JobStatusAuditLog.AuditRole role,
            String reason
    );

    /**
     * Log short-term job status change
     */
    void logShortTermJobStatusChange(
            Long shortTermJobId,
            ShortTermJobStatus previousStatus,
            ShortTermJobStatus newStatus,
            Long userId,
            JobStatusAuditLog.AuditRole role,
            String reason
    );

    /**
     * Log application status change
     */
    void logApplicationStatusChange(
            Long applicationId,
            ShortTermApplicationStatus previousStatus,
            ShortTermApplicationStatus newStatus,
            Long userId,
            JobStatusAuditLog.AuditRole role,
            String reason
    );

    /**
     * Get audit logs for job
     */
    List<JobStatusAuditLog> getAuditLogsForJob(Long jobId);

    /**
     * Get audit logs for short-term job
     */
    List<JobStatusAuditLog> getAuditLogsForShortTermJob(Long shortTermJobId);

    /**
     * Get audit logs for application
     */
    List<JobStatusAuditLog> getAuditLogsForApplication(Long applicationId);
}
