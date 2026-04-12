package com.exe.skillverse_backend.business_service.dto.response;

import com.exe.skillverse_backend.mentor_service.entity.ApplicationStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecruiterProfileResponse {
    private Long userId;
    private String email;
    private String companyName;
    private String companyWebsite;
    private String companyAddress;
    private String companyPhone;
    private String companyLogoUrl;
    private String taxCodeOrBusinessRegistrationNumber;
    private String companyDocumentsUrl;
    private String contactPersonPhone;
    private String contactPersonPosition;
    private String companySize;
    private String industry;
    private ApplicationStatus applicationStatus;
    private LocalDateTime applicationDate;
    private LocalDateTime approvalDate;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
