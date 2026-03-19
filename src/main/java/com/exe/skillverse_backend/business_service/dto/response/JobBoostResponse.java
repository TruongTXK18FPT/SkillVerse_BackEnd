package com.exe.skillverse_backend.business_service.dto.response;

import com.exe.skillverse_backend.business_service.entity.enums.JobBoostStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for job boost
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobBoostResponse {

    private Long id;
    private Long jobId;
    private String jobTitle;
    private String companyName;
    private Long recruiterId;

    private JobBoostStatus boostStatus;
    private LocalDateTime startedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime scheduledStartAt;

    // Analytics summary
    private Integer impressions;
    private Integer clicks;
    private Integer applications;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Computed fields
    private Boolean isActive;
    private Long remainingMinutes;
    private Double clickThroughRate;
    private Double applicationConversionRate;
}
