package com.exe.skillverse_backend.support_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating a support ticket (Admin only)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTicketRequest {

    private String status; // OPEN, IN_PROGRESS, WAITING, RESOLVED, CLOSED

    private String priority; // LOW, MEDIUM, HIGH

    private String adminResponse;

    private Long assignedToId; // Admin user ID
}
