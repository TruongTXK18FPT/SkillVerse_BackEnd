package com.exe.skillverse_backend.admin_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "Response containing applications with optional status filtering")
public class ApplicationsResponse {

    @Schema(description = "List of mentor applications")
    private List<MentorApplicationDto> mentorApplications;

    @Schema(description = "List of recruiter applications")
    private List<RecruiterApplicationDto> recruiterApplications;

    @Schema(description = "Total number of applications returned")
    private Integer totalApplications;

    @Schema(description = "Filter status applied", example = "PENDING")
    private String filterStatus;

    @Schema(description = "Statistics by status")
    private ApplicationStatusStatsDto statusStats;

}