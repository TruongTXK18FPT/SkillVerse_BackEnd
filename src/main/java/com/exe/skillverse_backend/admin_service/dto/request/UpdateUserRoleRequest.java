package com.exe.skillverse_backend.admin_service.dto.request;

import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating user role
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRoleRequest {
    @NotNull(message = "User ID is required")
    private Long userId;
    
    @NotNull(message = "Role is required")
    private PrimaryRole primaryRole;
    
    private String reason; // Optional reason for role change
}
