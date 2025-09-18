package com.exe.skillverse_backend.admin_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request for unified approval/rejection of mentor or recruiter applications")
public class ApplicationActionRequest {

    @NotNull(message = "User ID is required")
    @Schema(description = "User ID of the applicant", example = "123", required = true)
    private Long userId;

    @NotBlank(message = "Application type is required")
    @Schema(description = "Type of application", example = "MENTOR", allowableValues = { "MENTOR",
            "RECRUITER" }, required = true)
    private String applicationType; // MENTOR or RECRUITER

    @NotBlank(message = "Action is required")
    @Schema(description = "Action to perform", example = "APPROVE", allowableValues = { "APPROVE",
            "REJECT" }, required = true)
    private String action; // APPROVE or REJECT

    @Schema(description = "Reason for rejection (required if action is REJECT)", example = "Insufficient experience for mentor position")
    private String rejectionReason;
}