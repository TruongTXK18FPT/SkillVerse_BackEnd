package com.exe.skillverse_backend.mentor_service.dto.request;

import com.exe.skillverse_backend.shared.dto.request.BaseRegistrationRequest;
import com.exe.skillverse_backend.shared.validation.PasswordMatches;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@PasswordMatches
@Schema(description = "Mentor registration request matching the registration form")
public class MentorRegistrationRequest extends BaseRegistrationRequest {

    // Personal Information Section
    // Note: fullName and email are inherited from BaseRegistrationRequest

    @Schema(description = "LinkedIn profile URL (optional)", example = "https://www.linkedin.com/in/your-profile")
    private String linkedinProfile;

    // Expertise & Experience Section
    @NotBlank(message = "Main expertise area is required")
    @Schema(description = "Main area of expertise from dropdown", example = "Software Development", required = true)
    private String mainExpertiseArea;

    @NotNull(message = "Years of experience is required")
    @Min(value = 0, message = "Years of experience cannot be negative")
    @Max(value = 50, message = "Years of experience seems too high")
    @Schema(description = "Years of professional experience", example = "5", required = true)
    private Integer yearsOfExperience;

    @NotBlank(message = "Personal profile is required")
    @Size(max = 1000, message = "Personal profile must not exceed 1000 characters")
    @Schema(description = "Personal achievements and experience description", example = "mo ta co ban ve ban than...", required = true)
    private String personalProfile;

    // Documents Section
    @Schema(description = "CV or Portfolio file URL (will be set after file upload)", example = "https://storage.example.com/cv/mentor-cv.pdf")
    private String cvPortfolioUrl;

    @Schema(description = "Certificates file URL (optional, will be set after file upload)", example = "https://storage.example.com/certificates/mentor-cert.pdf")
    private String certificatesUrl;

    // Password Section (inherited from BaseRegistrationRequest)
    // password and confirmPassword fields are already available from
    // BaseRegistrationRequest
    // with @PasswordMatches validation at class level

    // Legacy fields for backward compatibility (deprecated)
    @Deprecated
    @Schema(description = "Deprecated: Use mainExpertiseArea instead")
    private String expertise;

    @Deprecated
    @Schema(description = "Deprecated: Use personalProfile instead")
    private String teachingExperience;

    @Deprecated
    @Schema(description = "Deprecated: Not used in new form")
    private java.util.List<String> skills;

    @Deprecated
    @Schema(description = "Deprecated: Not collected during registration")
    private Double hourlyRate;

    @Deprecated
    @Schema(description = "Deprecated: Use linkedinProfile instead")
    private String linkedinUrl;

    @Deprecated
    @Schema(description = "Deprecated: Not used in new form")
    private String githubUrl;

    @Deprecated
    @Schema(description = "Deprecated: Use personalProfile instead")
    private String motivation;
}