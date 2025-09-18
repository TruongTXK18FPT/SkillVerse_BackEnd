package com.exe.skillverse_backend.admin_service.dto.response;

import com.exe.skillverse_backend.mentor_service.entity.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Mentor application details for admin review")
public class MentorApplicationDto {
    @Schema(description = "User ID", example = "123")
    private Long userId;

    @Schema(description = "Full name", example = "Nguyễn Văn An")
    private String fullName;

    @Schema(description = "Email address", example = "mentor@email.com")
    private String email;

    @Schema(description = "Main expertise area", example = "Software Development")
    private String mainExpertiseArea;

    @Schema(description = "Years of experience", example = "5")
    private Integer yearsOfExperience;

    @Schema(description = "Personal profile description")
    private String personalProfile;

    @Schema(description = "LinkedIn profile URL")
    private String linkedinProfile;

    @Schema(description = "CV/Portfolio URL")
    private String cvPortfolioUrl;

    @Schema(description = "Certificates URL")
    private String certificatesUrl;

    @Schema(description = "Application status", example = "PENDING")
    private ApplicationStatus applicationStatus;

    @Schema(description = "Email verification status", example = "true")
    private Boolean isEmailVerified;

    @Schema(description = "User account status", example = "ACTIVE")
    private String userStatus;

    @Schema(description = "Application date")
    private LocalDateTime applicationDate;

    @Schema(description = "Approval date")
    private LocalDateTime approvalDate;

    @Schema(description = "Rejection reason")
    private String rejectionReason;
}