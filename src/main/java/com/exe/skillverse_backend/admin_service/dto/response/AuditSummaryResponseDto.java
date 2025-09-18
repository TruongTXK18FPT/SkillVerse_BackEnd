package com.exe.skillverse_backend.admin_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Audit summary statistics")
public class AuditSummaryResponseDto {
    @Schema(description = "Total number of recent audit logs")
    private long totalRecentLogs;

    @Schema(description = "Number of user registrations")
    private long userRegistrations;

    @Schema(description = "Number of login attempts")
    private long loginAttempts;

    @Schema(description = "Number of mentor applications")
    private long mentorApplications;

    @Schema(description = "Number of business registrations")
    private long businessRegistrations;
}