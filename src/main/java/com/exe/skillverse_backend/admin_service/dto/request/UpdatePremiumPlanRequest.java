package com.exe.skillverse_backend.admin_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;

import java.math.BigDecimal;
import java.util.List;

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
    // Note: Allow Integer.MAX_VALUE for FREE_TIER (permanent plan)
    @Schema(description = "Plan duration in months (use Integer.MAX_VALUE for permanent plans like FREE_TIER)", example = "3")
    private Integer durationMonths;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Price must be at least 0")
    @Schema(description = "Plan price in VND (0 for FREE_TIER)", example = "199000")
    private BigDecimal price;

    @DecimalMin(value = "0.0", message = "Discount must be at least 0")
    @DecimalMax(value = "100.0", message = "Discount must not exceed 100")
    @Schema(description = "Discount percentage for the plan's target role (0-100)", example = "20")
    private BigDecimal discountPercent;

    @DecimalMin(value = "0.0", message = "Student discount must be at least 0")
    @DecimalMax(value = "100.0", message = "Student discount must not exceed 100")
    @Schema(description = "Legacy alias for role discount percentage (0-100). Optional for backward compatibility.", example = "20")
    private BigDecimal studentDiscountPercent;

    @NotBlank(message = "Features are required")
    @Schema(description = "JSON array of features", example = "[\"Feature 1\", \"Feature 2\"]")
    private String features;

    @Schema(description = "Whether plan is active", example = "true")
    private Boolean isActive;

    @Schema(description = "Target role for this plan (LEARNER, RECRUITER)", example = "RECRUITER")
    private PremiumPlan.TargetRole targetRole;

    @Valid
    @Schema(description = "Feature limits configuration for this plan (optional - only update if provided)")
    private List<FeatureLimitConfigRequest> featureLimits;
}
