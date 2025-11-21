package com.exe.skillverse_backend.admin_service.dto.request;

import com.exe.skillverse_backend.premium_service.entity.FeatureType;
import com.exe.skillverse_backend.premium_service.entity.ResetPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for configuring feature limits
 * Used when creating or updating premium plans
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Feature limit configuration")
public class FeatureLimitConfigRequest {

    @NotNull(message = "Feature type is required")
    @Schema(description = "Type of feature to limit", example = "AI_CHATBOT_REQUESTS")
    private FeatureType featureType;

    @Min(value = 0, message = "Limit value must be at least 0")
    @Schema(description = "Maximum usage allowed (null for unlimited)", example = "50")
    private Integer limitValue;

    @NotNull(message = "Reset period is required")
    @Schema(description = "How often usage resets", example = "DAILY")
    private ResetPeriod resetPeriod;

    @Schema(description = "Whether this feature is unlimited", example = "false")
    @Builder.Default
    private Boolean isUnlimited = false;

    @DecimalMin(value = "0.0", message = "Bonus multiplier must be at least 0")
    @DecimalMax(value = "10.0", message = "Bonus multiplier cannot exceed 10")
    @Schema(description = "Bonus multiplier for features like coin earning (1.0 = normal, 2.0 = double)", example = "1.5")
    private BigDecimal bonusMultiplier;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    @Schema(description = "Description of this limit", example = "50 AI chatbot requests per day")
    private String description;

    @Schema(description = "Whether this limit is active", example = "true")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Validation: Unlimited features cannot have limit value
     */
    @AssertTrue(message = "Unlimited features cannot have a limit value")
    private boolean isValidUnlimitedConfig() {
        return !Boolean.TRUE.equals(isUnlimited) || limitValue == null;
    }

    /**
     * Validation: Non-unlimited features must have limit value (except multiplier
     * features)
     */
    @AssertTrue(message = "Non-unlimited features must have a limit value or bonus multiplier")
    private boolean isValidLimitConfig() {
        if (Boolean.TRUE.equals(isUnlimited)) {
            return true;
        }
        // Multiplier features use bonusMultiplier instead of limitValue
        if (featureType != null && featureType.isMultiplierFeature()) {
            return bonusMultiplier != null;
        }
        return limitValue != null && limitValue >= 0;
    }
}
