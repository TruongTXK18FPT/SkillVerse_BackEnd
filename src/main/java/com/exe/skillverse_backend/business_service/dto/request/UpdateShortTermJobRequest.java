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
public class UpdateShortTermJobRequest {

    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    private String description;

    private List<String> requiredSkills;

    @DecimalMin(value = "0.01", message = "Budget must be greater than 0")
    private BigDecimal budget;

    private Boolean isNegotiable;

    private PaymentMethod paymentMethod;

    @Future(message = "Deadline must be in the future")
    private LocalDateTime deadline;

    @Size(max = 50, message = "Estimated duration must not exceed 50 characters")
    private String estimatedDuration;

    private JobUrgency urgency;

    private LocalDateTime startTime;

    private Boolean isRemote;

    private String location;

    @Min(value = 1, message = "Max applicants must be at least 1")
    private Integer maxApplicants;

    @DecimalMin(value = "0.0", message = "Min rating must be at least 0")
    @DecimalMax(value = "5.0", message = "Min rating must not exceed 5")
    private BigDecimal minRating;
}
