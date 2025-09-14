package com.exe.skillverse_backend.auth_service.entity;

/**
 * Enum representing account approval status
 */
public enum AccountStatus {
    PENDING, // Account created but waiting for admin approval (mentor/recruiter)
    ACTIVE, // Account approved and active
    REJECTED, // Account rejected by admin
    SUSPENDED // Account temporarily suspended
}