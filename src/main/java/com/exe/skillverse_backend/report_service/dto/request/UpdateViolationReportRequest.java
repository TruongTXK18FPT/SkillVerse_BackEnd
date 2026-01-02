package com.exe.skillverse_backend.report_service.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for admin to update a violation report
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateViolationReportRequest {

    /**
     * New status: PENDING, INVESTIGATING, RESOLVED, DISMISSED
     */
    private String status;

    /**
     * New severity level: LOW, MEDIUM, HIGH
     */
    private String severity;

    /**
     * Admin's notes/response
     */
    private String adminNotes;

    /**
     * ID of admin to assign this report to
     */
    private Long assignedAdminId;

    /**
     * Resolution action taken: NO_ACTION, WARNING_ISSUED, CONTENT_REMOVED,
     * ACCOUNT_SUSPENDED, ACCOUNT_BANNED, ESCALATED
     */
    private String resolutionAction;
}
