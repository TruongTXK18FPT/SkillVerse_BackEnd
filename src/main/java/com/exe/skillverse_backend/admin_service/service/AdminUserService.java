package com.exe.skillverse_backend.admin_service.service;

import com.exe.skillverse_backend.admin_service.dto.request.ResetPasswordRequest;
import com.exe.skillverse_backend.admin_service.dto.request.UpdateUserProfileRequest;
import com.exe.skillverse_backend.admin_service.dto.request.UpdateUserRoleRequest;
import com.exe.skillverse_backend.admin_service.dto.request.UpdateUserStatusRequest;
import com.exe.skillverse_backend.admin_service.dto.response.AdminUserDetailResponse;
import com.exe.skillverse_backend.admin_service.dto.response.AdminUserListResponse;
import com.exe.skillverse_backend.admin_service.dto.response.AdminUserResponse;
import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.UserStatus;

/**
 * Service interface for admin user management operations
 */
public interface AdminUserService {
    
    /**
     * Get all users with optional filters
     * @param role Filter by primary role (optional)
     * @param status Filter by user status (optional)
     * @param search Search by name or email (optional)
     * @return AdminUserListResponse with user list and statistics
     */
    AdminUserListResponse getAllUsers(PrimaryRole role, UserStatus status, String search);
    
    /**
     * Get user details by ID (basic info)
     * @param userId User ID
     * @return AdminUserResponse with user details
     */
    AdminUserResponse getUserById(Long userId);
    
    /**
     * Get detailed user information for modal/detail page
     * @param userId User ID
     * @return AdminUserDetailResponse with comprehensive user information
     */
    AdminUserDetailResponse getUserDetailById(Long userId);
    
    /**
     * Update user status (ban/unban)
     * @param request UpdateUserStatusRequest
     * @return Updated AdminUserResponse
     */
    AdminUserResponse updateUserStatus(UpdateUserStatusRequest request);
    
    /**
     * Update user role
     * @param request UpdateUserRoleRequest
     * @return Updated AdminUserResponse
     */
    AdminUserResponse updateUserRole(UpdateUserRoleRequest request);
    
    /**
     * Update user profile information
     * @param request UpdateUserProfileRequest
     * @return Updated AdminUserResponse
     */
    AdminUserResponse updateUserProfile(UpdateUserProfileRequest request);
    
    /**
     * Reset user password (admin action)
     * @param request ResetPasswordRequest
     * @return Success message
     */
    String resetUserPassword(ResetPasswordRequest request);
    
    /**
     * Delete user (soft delete by setting status to INACTIVE)
     * @param userId User ID
     */
    void deleteUser(Long userId);
    
    /**
     * Permanently delete user from database (only for INACTIVE users)
     * @param userId User ID
     * @throws RuntimeException if user is still ACTIVE
     */
    void permanentlyDeleteUser(Long userId);
}
