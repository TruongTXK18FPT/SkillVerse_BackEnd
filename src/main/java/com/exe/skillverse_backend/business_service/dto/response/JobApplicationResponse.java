package com.exe.skillverse_backend.business_service.dto.response;

import com.exe.skillverse_backend.business_service.entity.enums.JobApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobApplicationResponse {

    private Long id;
    private Long jobId;
    private String jobTitle;
    private Long userId;
    private String userFullName;
    private String userEmail;
    private String coverLetter;
    private LocalDateTime appliedAt;
    private JobApplicationStatus status;
    private String acceptanceMessage;
    private String rejectionReason;
    private LocalDateTime reviewedAt;
    private LocalDateTime processedAt;

    // Job details for user's application view
    private String recruiterCompanyName;
    private BigDecimal minBudget;
    private BigDecimal maxBudget;
    private Boolean isRemote;
    private String location;
}
