package com.exe.skillverse_backend.business_service.dto.request;

import com.exe.skillverse_backend.business_service.entity.enums.JobUrgency;
import com.exe.skillverse_backend.business_service.entity.enums.PaymentMethod;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateShortTermJobRequest {

    @NotBlank(message = "Job title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @NotBlank(message = "Job description is required")
    private String description;

    @NotNull(message = "Required skills are required")
    @Size(min = 1, message = "At least one skill is required")
    private List<String> requiredSkills;

    @NotNull(message = "Budget is required")
    @DecimalMin(value = "0.01", message = "Budget must be greater than 0")
    private BigDecimal budget;

    @Builder.Default
    private Boolean isNegotiable = false;

    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.FIXED;

    @NotNull(message = "Deadline is required")
    @Future(message = "Deadline must be in the future")
    private LocalDateTime deadline;

    @NotBlank(message = "Estimated duration is required")
    @Size(max = 50, message = "Estimated duration must not exceed 50 characters")
    private String estimatedDuration;

    @Builder.Default
    private JobUrgency urgency = JobUrgency.NORMAL;

    private LocalDateTime startTime;

    @NotNull(message = "Remote status is required")
    private Boolean isRemote;

    private String location;

    @Min(value = 1, message = "Max applicants must be at least 1")
    private Integer maxApplicants;

    @DecimalMin(value = "0.0", message = "Min rating must be at least 0")
    @DecimalMax(value = "5.0", message = "Min rating must not exceed 5")
    private BigDecimal minRating;

    private List<CreateMilestoneRequest> milestones;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateMilestoneRequest {
        @NotBlank(message = "Milestone title is required")
        private String title;
        
        private String description;
        
        @NotNull(message = "Milestone amount is required")
        @DecimalMin(value = "0.01", message = "Milestone amount must be greater than 0")
        private BigDecimal amount;
        
        @NotNull(message = "Milestone deadline is required")
        @Future(message = "Milestone deadline must be in the future")
        private LocalDateTime deadline;
        
        @NotNull(message = "Milestone order is required")
        @Min(value = 1, message = "Milestone order must be at least 1")
        private Integer order;
    }
}
