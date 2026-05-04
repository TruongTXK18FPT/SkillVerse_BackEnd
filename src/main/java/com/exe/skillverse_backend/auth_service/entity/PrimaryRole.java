package com.exe.skillverse_backend.auth_service.entity;

import java.util.Set;

/**
 * PrimaryRole enum representing all user roles in the system.
 * <p>
 * Main roles: USER, MENTOR, RECRUITER, PARENT, ADMIN
 * Sub-admin roles: USER_ADMIN, CONTENT_ADMIN, COMMUNITY_ADMIN, FINANCE_ADMIN,
 *                   PREMIUM_ADMIN, AI_ADMIN, SUPPORT_ADMIN, SYSTEM_ADMIN
 */
public enum PrimaryRole {
    // Main Roles
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
    SYSTEM_ADMIN; // Manage system settings, logs

    /**
     * Check if this role is a main role (not sub-admin).
     * Main roles: USER, MENTOR, RECRUITER, PARENT, ADMIN
     */
    public boolean isMainRole() {
        return this == USER || this == MENTOR || this == RECRUITER ||
               this == PARENT || this == ADMIN;
    }

    /**
     * Check if this role is a sub-admin role.
     * Sub-admin roles: USER_ADMIN, CONTENT_ADMIN, COMMUNITY_ADMIN, FINANCE_ADMIN,
     *                  PREMIUM_ADMIN, AI_ADMIN, SUPPORT_ADMIN, SYSTEM_ADMIN
     */
    public boolean isSubAdminRole() {
        return !isMainRole();
    }

    /**
     * Get the set of all sub-admin role names.
     * Useful for validation and whitelisting.
     */
    public static Set<String> subAdminRoleNames() {
        return Set.of(
            USER_ADMIN.name(),
            CONTENT_ADMIN.name(),
            COMMUNITY_ADMIN.name(),
            FINANCE_ADMIN.name(),
            PREMIUM_ADMIN.name(),
            AI_ADMIN.name(),
            SUPPORT_ADMIN.name(),
            SYSTEM_ADMIN.name()
        );
    }
}