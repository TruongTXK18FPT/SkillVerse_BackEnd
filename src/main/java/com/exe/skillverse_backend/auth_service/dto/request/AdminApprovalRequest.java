package com.exe.skillverse_backend.auth_service.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminApprovalRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @Size(max = 500, message = "Rejection reason must not exceed 500 characters")
    private String rejectionReason; // Only required for rejections
}