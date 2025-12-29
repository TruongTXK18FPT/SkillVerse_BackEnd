package com.exe.skillverse_backend.auth_service.entity;

public enum PrimaryRole {
    USER, // Regular user with basic access
    MENTOR, // Approved mentor who can offer services
    RECRUITER, // Approved recruiter who can post jobs
    PARENT, // Parent who can monitor and fund students
    ADMIN, // Administrator with full access (Super Admin)
    
    // Sub-Admin Roles
    USER_ADMIN, // Manage users, verification
    CONTENT_ADMIN, // Manage courses, jobs, content moderation
    COMMUNITY_ADMIN, // Manage community, reports
    FINANCE_ADMIN, // Manage payments, withdrawals
    PREMIUM_ADMIN, // Manage premium plans, skill points
    AI_ADMIN, // Manage AI experts, skins
    SUPPORT_ADMIN, // Manage tickets, notifications
    SYSTEM_ADMIN // Manage system settings, logs
}