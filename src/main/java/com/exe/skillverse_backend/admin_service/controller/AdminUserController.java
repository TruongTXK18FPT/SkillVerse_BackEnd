package com.exe.skillverse_backend.admin_service.controller;

import com.exe.skillverse_backend.admin_service.dto.request.AddRoleRequest;
import com.exe.skillverse_backend.admin_service.dto.request.ResetPasswordRequest;
import com.exe.skillverse_backend.admin_service.dto.request.UpdateUserProfileRequest;
import com.exe.skillverse_backend.admin_service.dto.request.UpdateUserRoleRequest;
import com.exe.skillverse_backend.admin_service.dto.request.UpdateUserStatusRequest;
import com.exe.skillverse_backend.admin_service.dto.response.AdminUserDetailResponse;
import com.exe.skillverse_backend.admin_service.dto.response.AdminUserListResponse;
import com.exe.skillverse_backend.admin_service.dto.response.AdminUserResponse;
import com.exe.skillverse_backend.admin_service.service.AdminUserService;
import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.UserStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for admin user management operations
 * All endpoints require ADMIN role
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin User Management", description = "APIs for managing all system users")
@PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "Get all users with filters", 
               description = "Retrieve all users with optional filters for role, status, and search")
    public ResponseEntity<AdminUserListResponse> getAllUsers(
        @Parameter(description = "Filter by primary role (USER, MENTOR, RECRUITER, ADMIN)")
        @RequestParam(required = false) PrimaryRole role,
        
        @Parameter(description = "Filter by user status (ACTIVE, INACTIVE)")
        @RequestParam(required = false) UserStatus status,
        
        @Parameter(description = "Search by name or email")
        @RequestParam(required = false) String search
    ) {
        log.info("GET /api/admin/users - role: {}, status: {}, search: {}", role, status, search);
        AdminUserListResponse response = adminUserService.getAllUsers(role, status, search);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID", description = "Retrieve basic information about a specific user")
    public ResponseEntity<AdminUserResponse> getUserById(
        @Parameter(description = "User ID", required = true)
        @PathVariable Long userId
    ) {
        log.info("GET /api/admin/users/{}", userId);
        AdminUserResponse response = adminUserService.getUserById(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}/detail")
    @Operation(summary = "Get detailed user information", 
               description = "Retrieve comprehensive user information for modal/detail page")
    public ResponseEntity<AdminUserDetailResponse> getUserDetailById(
        @Parameter(description = "User ID", required = true)
        @PathVariable Long userId
    ) {
        log.info("GET /api/admin/users/{}/detail", userId);
        AdminUserDetailResponse response = adminUserService.getUserDetailById(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user status", description = "Update user status (ban/unban)")
    public ResponseEntity<AdminUserResponse> updateUserStatus(
        @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        log.info("PUT /api/admin/users/status - userId: {}, status: {}", 
                 request.getUserId(), request.getStatus());
        AdminUserResponse response = adminUserService.updateUserStatus(request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user role", description = "Update user primary role")
    public ResponseEntity<AdminUserResponse> updateUserRole(
        @Valid @RequestBody UpdateUserRoleRequest request
    ) {
        log.info("PUT /api/admin/users/role - userId: {}, role: {}", 
                 request.getUserId(), request.getPrimaryRole());
        AdminUserResponse response = adminUserService.updateUserRole(request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/roles")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Set sub-admin roles for user", description = "Replace user's sub-admin roles. Only ADMIN can assign sub-admin roles.")
    public ResponseEntity<AdminUserResponse> setSubAdminRoles(
        @Valid @RequestBody AddRoleRequest request
    ) {
        log.info("PUT /api/admin/users/roles - userId: {}, roles: {}", 
                 request.getUserId(), request.getRoles());
        AdminUserResponse response = adminUserService.setSubAdminRoles(request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete user", description = "Soft delete user by setting status to INACTIVE")
    public ResponseEntity<Void> deleteUser(
        @Parameter(description = "User ID", required = true)
        @PathVariable Long userId
    ) {
        log.info("DELETE /api/admin/users/{}", userId);
        adminUserService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}/permanent")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Permanently delete user",
        description = "Permanently delete user from database. Only works for banned/deactivated users (INACTIVE).")
    public ResponseEntity<Void> permanentlyDeleteUser(
        @Parameter(description = "User ID", required = true)
        @PathVariable Long userId
    ) {
        log.info("DELETE /api/admin/users/{}/permanent", userId);
        adminUserService.permanentlyDeleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/ban")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ban user", description = "Ban a user account")
    public ResponseEntity<AdminUserResponse> banUser(
        @Parameter(description = "User ID", required = true)
        @PathVariable Long userId,
        
        @Parameter(description = "Reason for banning")
        @RequestParam(required = false) String reason
    ) {
        log.info("POST /api/admin/users/{}/ban - reason: {}", userId, reason);
        UpdateUserStatusRequest request = UpdateUserStatusRequest.builder()
            .userId(userId)
            .status(UserStatus.INACTIVE)
            .reason(reason)
            .build();
        AdminUserResponse response = adminUserService.updateUserStatus(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/unban")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Unban user", description = "Unban a user account")
    public ResponseEntity<AdminUserResponse> unbanUser(
        @Parameter(description = "User ID", required = true)
        @PathVariable Long userId,
        
        @Parameter(description = "Reason for unbanning")
        @RequestParam(required = false) String reason
    ) {
        log.info("POST /api/admin/users/{}/unban - reason: {}", userId, reason);
        UpdateUserStatusRequest request = UpdateUserStatusRequest.builder()
            .userId(userId)
            .status(UserStatus.ACTIVE)
            .reason(reason)
            .build();
        AdminUserResponse response = adminUserService.updateUserStatus(request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/profile")
    @Operation(summary = "Update user profile", description = "Update user profile information (name, email, phone)")
    public ResponseEntity<AdminUserResponse> updateUserProfile(
        @Valid @RequestBody UpdateUserProfileRequest request
    ) {
        log.info("PUT /api/admin/users/profile - userId: {}", request.getUserId());
        AdminUserResponse response = adminUserService.updateUserProfile(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reset user password", description = "Admin reset user password")
    public ResponseEntity<String> resetUserPassword(
        @Valid @RequestBody ResetPasswordRequest request
    ) {
        log.info("POST /api/admin/users/reset-password - userId: {}", request.getUserId());
        String message = adminUserService.resetUserPassword(request);
        return ResponseEntity.ok(message);
    }
}
