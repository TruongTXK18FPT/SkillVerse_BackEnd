package com.exe.skillverse_backend.business_service.dto.response;

import com.exe.skillverse_backend.shared.dto.response.BaseRegistrationResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Business/Recruiter registration response")
public class BusinessRegistrationResponse extends BaseRegistrationResponse {

    @Schema(description = "Recruiter profile ID", example = "789")
    private Long recruiterProfileId;

    @Schema(description = "Application status", example = "PENDING")
    private String applicationStatus;

    @Schema(description = "Role assigned", example = "RECRUITER")
    private String role;

    @Schema(description = "Company name", example = "Tech Solutions Inc.")
    private String companyName;
}