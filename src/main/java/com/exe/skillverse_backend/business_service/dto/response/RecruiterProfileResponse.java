package com.exe.skillverse_backend.business_service.dto.response;

import com.exe.skillverse_backend.mentor_service.entity.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private String taxCodeOrBusinessRegistrationNumber;
    private String companyDocumentsUrl;
    private ApplicationStatus applicationStatus;
    private LocalDateTime applicationDate;
    private LocalDateTime approvalDate;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
