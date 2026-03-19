package com.exe.skillverse_backend.business_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyShortTermJobRequest {

    @Size(max = 5000, message = "Cover letter must not exceed 5000 characters")
    private String coverLetter;

    @DecimalMin(value = "0.01", message = "Proposed price must be greater than 0")
    private BigDecimal proposedPrice;

    @Size(max = 100, message = "Proposed duration must not exceed 100 characters")
    private String proposedDuration;

    private List<String> portfolio; // Links to relevant work
}
