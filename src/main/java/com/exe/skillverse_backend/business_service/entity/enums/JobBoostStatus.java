package com.exe.skillverse_backend.business_service.entity.enums;

/**
 * Enum representing the status of a job boost
 */
public enum JobBoostStatus {
    /**
     * Boost is currently active and job is shown at top
     */
    ACTIVE,

    /**
     * Boost has expired (past expires_at)
     */
    EXPIRED,

    /**
     * Boost is scheduled for future activation
     */
    SCHEDULED,

    /**
     * Boost was cancelled by recruiter or admin
     */
    CANCELLED
}
