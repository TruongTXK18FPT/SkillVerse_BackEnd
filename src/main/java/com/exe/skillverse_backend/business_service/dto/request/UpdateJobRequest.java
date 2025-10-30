package com.exe.skillverse_backend.business_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;
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
public class UpdateJobRequest {

    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    private String description;

    @Size(min = 1, message = "At least one skill is required if provided")
    private List<String> requiredSkills;

    @DecimalMin(value = "0.0", inclusive = true, message = "Minimum budget must be at least 0")
    private BigDecimal minBudget;

    @DecimalMin(value = "0.0", inclusive = true, message = "Maximum budget must be at least 0")
    private BigDecimal maxBudget;

    @Future(message = "Deadline must be in the future")
    private LocalDate deadline;

    private Boolean isRemote;

    private String location;
}
