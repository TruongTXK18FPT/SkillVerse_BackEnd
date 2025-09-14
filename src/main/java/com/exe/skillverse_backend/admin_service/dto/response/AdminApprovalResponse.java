package com.exe.skillverse_backend.admin_service.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminApprovalResponse {

    private boolean success;
    private String message;
    private String userEmail;
    private Long userId;
    private String role;
    private String action; // "APPROVED" or "REJECTED"
    private String reason; // Optional rejection reason
}