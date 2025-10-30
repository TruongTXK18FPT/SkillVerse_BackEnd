package com.exe.skillverse_backend.business_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateJobRequest {

    @NotBlank(message = "Job title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @NotBlank(message = "Job description is required")
    private String description;

    @NotNull(message = "Required skills are required")
    @Size(min = 1, message = "At least one skill is required")
    private List<String> requiredSkills;

    @NotNull(message = "Minimum budget is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Minimum budget must be at least 0")
    private BigDecimal minBudget;

    @NotNull(message = "Maximum budget is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Maximum budget must be at least 0")
    private BigDecimal maxBudget;

    @NotNull(message = "Deadline is required")
    @Future(message = "Deadline must be in the future")
    private LocalDate deadline;

    @NotNull(message = "Remote status is required")
    private Boolean isRemote;

    private String location; // Nullable - required only if isRemote = false
}
