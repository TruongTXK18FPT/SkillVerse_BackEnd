package com.exe.skillverse_backend.admin_service.dto.response;

import com.exe.skillverse_backend.premium_service.entity.FeatureType;
import com.exe.skillverse_backend.premium_service.entity.ResetPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for feature limit configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Feature limit configuration details")
public class FeatureLimitResponse {

    @Schema(description = "Limit configuration ID")
    private Long id;

    @Schema(description = "Feature type")
    private FeatureType featureType;

    @Schema(description = "Feature display name (English)")
    private String featureName;

    @Schema(description = "Feature display name (Vietnamese)")
    private String featureNameVi;

    @Schema(description = "Maximum usage allowed per period")
    private Integer limitValue;

    @Schema(description = "Reset period")
    private ResetPeriod resetPeriod;

    @Schema(description = "Whether feature is unlimited")
    private Boolean isUnlimited;

    @Schema(description = "Bonus multiplier (for multiplier features)")
    private BigDecimal bonusMultiplier;

    @Schema(description = "Description")
    private String description;

    @Schema(description = "Whether limit is active")
    private Boolean isActive;

    @Schema(description = "Created timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last updated timestamp")
    private LocalDateTime updatedAt;
}
