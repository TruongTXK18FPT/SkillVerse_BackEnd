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
@Schema(description = "Recruiter application details for admin review")
public class RecruiterApplicationDto {

    @Schema(description = "User ID", example = "124")
    private Long userId;

    @Schema(description = "Full name", example = "Trần Thị Bình")
    private String fullName;

    @Schema(description = "Company email (used for login)", example = "hr@techcorp.vn")
    private String email;

    @Schema(description = "Company name", example = "TechCorp Vietnam")
    private String companyName;

    @Schema(description = "Company website", example = "https://www.techcorp.vn")
    private String companyWebsite;

    @Schema(description = "Company address")
    private String companyAddress;

    @Schema(description = "Tax code or business registration number", example = "0123456789")
    private String taxCodeOrBusinessRegistrationNumber;

    @Schema(description = "Company documents URL")
    private String companyDocumentsUrl;

    @Schema(description = "Contact person phone number", example = "+84912345678")
    private String contactPersonPhone;

    @Schema(description = "Contact person position/title", example = "CEO")
    private String contactPersonPosition;

    @Schema(description = "Company size", example = "11-50")
    private String companySize;

    @Schema(description = "Industry/sector", example = "Information Technology")
    private String industry;

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