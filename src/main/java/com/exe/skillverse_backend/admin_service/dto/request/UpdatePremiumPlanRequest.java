package com.exe.skillverse_backend.admin_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for updating an existing premium plan (Admin only)
 * Note: Cannot update FREE_TIER plan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update an existing premium plan")
public class UpdatePremiumPlanRequest {

    @NotBlank(message = "Display name is required")
    @Size(min = 3, max = 150, message = "Display name must be between 3 and 150 characters")
    @Schema(description = "Display name shown to users", example = "Premium Advanced")
    private String displayName;

    @NotBlank(message = "Description is required")
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Schema(description = "Plan description", example = "Advanced features for professionals")
    private String description;

    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Duration must be at least 1 month")
    @Max(value = 12, message = "Duration must not exceed 12 months")
    @Schema(description = "Plan duration in months", example = "3")
    private Integer durationMonths;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    @Schema(description = "Plan price in VND", example = "199000")
    private BigDecimal price;

    @NotNull(message = "Student discount is required")
    @DecimalMin(value = "0.0", message = "Student discount must be at least 0")
    @DecimalMax(value = "100.0", message = "Student discount must not exceed 100")
    @Schema(description = "Student discount percentage (0-100)", example = "20")
    private BigDecimal studentDiscountPercent;

    @NotBlank(message = "Features are required")
    @Schema(description = "JSON array of features", example = "[\"Feature 1\", \"Feature 2\"]")
    private String features;

    @Schema(description = "Maximum number of subscribers (null = unlimited)", example = "1000")
    private Integer maxSubscribers;

    @Schema(description = "Whether plan is active", example = "true")
    private Boolean isActive;
}
