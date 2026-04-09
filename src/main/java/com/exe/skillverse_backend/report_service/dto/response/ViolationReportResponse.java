package com.exe.skillverse_backend.report_service.dto.response;

import com.exe.skillverse_backend.report_service.entity.ViolationReport;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for violation report details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViolationReportResponse {

    private Long id;
    private String reportCode;
    private String title;

    // Reporter info
    private Long reporterId;
    private String reporterName;
    private String reporterEmail;

    // Reported user info
    private Long reportedUserId;
    private String reportedUserName;
    private String reportedUserEmail;

    private String reportType;
    private String severity;
    private String description;
    private String status;

    // Admin info
    private String adminNotes;
    private Long assignedAdminId;
    private String assignedAdminName;
    private String resolutionAction;
    private LocalDateTime resolvedAt;

    // Evidence
    private List<EvidenceResponse> evidences;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Convert entity to response DTO
     */
    public static ViolationReportResponse fromEntity(ViolationReport report) {
        return ViolationReportResponse.builder()
                .id(report.getId())
                .reportCode(report.getReportCode())
                .title(report.getTitle())
                // Reporter
                .reporterId(report.getReporter() != null ? report.getReporter().getId() : null)
                .reportedUserName(report.getReportedUserName() != null
                        ? report.getReportedUserName()
                        : (report.getReportedUser() != null ? report.getReportedUser().getFullName() : null))
                .reporterEmail(report.getReporter() != null ? report.getReporter().getEmail() : null)
                // Reported user
                .reportedUserId(report.getReportedUser() != null ? report.getReportedUser().getId() : null)
                .reportedUserName(report.getReportedUser() != null ? report.getReportedUser().getFullName() : null)
                .reportedUserEmail(report.getReportedUser() != null ? report.getReportedUser().getEmail() : null)
                // Report details
                .reportType(report.getReportType() != null ? report.getReportType().name() : null)
                .severity(report.getSeverity() != null ? report.getSeverity().name() : null)
                .description(report.getDescription())
                .status(report.getStatus() != null ? report.getStatus().name() : null)
                // Admin
                .adminNotes(report.getAdminNotes())
                .assignedAdminId(report.getAssignedAdmin() != null ? report.getAssignedAdmin().getId() : null)
                .assignedAdminName(report.getAssignedAdmin() != null ? report.getAssignedAdmin().getFullName() : null)
                .resolutionAction(report.getResolutionAction() != null ? report.getResolutionAction().name() : null)
                .resolvedAt(report.getResolvedAt())
                // Evidences
                .evidences(report.getEvidences() != null
                        ? report.getEvidences().stream()
                            .map(EvidenceResponse::fromEntity)
                            .collect(Collectors.toList())
                        : null)
                // Timestamps
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }
}
