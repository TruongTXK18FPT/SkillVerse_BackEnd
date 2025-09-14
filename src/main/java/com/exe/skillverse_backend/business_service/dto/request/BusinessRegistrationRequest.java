package com.exe.skillverse_backend.business_service.dto.request;

import com.exe.skillverse_backend.shared.dto.request.BaseRegistrationRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Business/Recruiter registration request")
public class BusinessRegistrationRequest extends BaseRegistrationRequest {

    @NotBlank(message = "Company name is required")
    @Size(max = 200, message = "Company name must not exceed 200 characters")
    @Schema(description = "Company or organization name", example = "Tech Solutions Inc.")
    private String companyName;

    @NotBlank(message = "Company description is required")
    @Size(max = 1000, message = "Company description must not exceed 1000 characters")
    @Schema(description = "Brief description of the company", example = "Leading software development company specializing in innovative solutions")
    private String companyDescription;

    @Size(max = 100, message = "Company website must not exceed 100 characters")
    @Schema(description = "Company website URL", example = "https://techsolutions.com")
    private String companyWebsite;

    @Size(max = 100, message = "Industry must not exceed 100 characters")
    @Schema(description = "Industry sector", example = "Technology, Software Development")
    private String industry;

    @Size(max = 50, message = "Company size must not exceed 50 characters")
    @Schema(description = "Company size range", example = "50-200 employees")
    private String companySize;

    @NotBlank(message = "Job title is required")
    @Size(max = 100, message = "Job title must not exceed 100 characters")
    @Schema(description = "Recruiter's job title", example = "Senior HR Manager")
    private String jobTitle;

    @Size(max = 100, message = "LinkedIn URL must not exceed 100 characters")
    @Schema(description = "Professional LinkedIn profile URL", example = "https://linkedin.com/in/recruiter")
    private String linkedinUrl;

    @Size(max = 1000, message = "Hiring goals must not exceed 1000 characters")
    @Schema(description = "What type of talent the recruiter is looking to hire", example = "Seeking experienced software engineers and project managers for expanding team")
    private String hiringGoals;
}