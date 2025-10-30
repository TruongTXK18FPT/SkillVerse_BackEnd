package com.exe.skillverse_backend.business_service.dto.response;

import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobPostingResponse {

    private Long id;
    private String title;
    private String description;
    private List<String> requiredSkills;
    private BigDecimal minBudget;
    private BigDecimal maxBudget;
    private LocalDate deadline;
    private Boolean isRemote;
    private String location;
    private JobStatus status;
    private Integer applicantCount;

    // Recruiter information
    private String recruiterCompanyName;
    private String recruiterEmail;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
