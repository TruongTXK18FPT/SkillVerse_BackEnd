package com.exe.skillverse_backend.admin_service.dto.request;

import com.exe.skillverse_backend.auth_service.entity.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating user status (ban/unban)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserStatusRequest {
    @NotNull(message = "User ID is required")
    private Long userId;
    
    @NotNull(message = "Status is required")
    private UserStatus status;
    
    private String reason; // Optional reason for status change
}
