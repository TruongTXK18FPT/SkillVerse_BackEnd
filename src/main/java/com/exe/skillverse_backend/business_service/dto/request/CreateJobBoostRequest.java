package com.exe.skillverse_backend.business_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request DTO for creating a job boost
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateJobBoostRequest {

    @NotNull(message = "Job ID is required")
    private Long jobId;

    @NotNull(message = "Duration in days is required")
    @Min(value = 7, message = "Boost duration must be at least 7 days")
    @Max(value = 30, message = "Boost duration cannot exceed 30 days")
    private Integer durationDays;

    /**
     * Optional: Schedule boost for future activation
     * If null, boost starts immediately
     */
    private LocalDateTime scheduledStartAt;

    /**
     * Optional: Custom expiration time (overrides durationDays if provided)
     */
    private LocalDateTime expiresAt;
}
