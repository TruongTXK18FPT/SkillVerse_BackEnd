package com.exe.skillverse_backend.premium_service.dto.response;

import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for premium plan information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Premium plan information")
public class PremiumPlanResponse {

    @Schema(description = "Plan ID", example = "1")
    private Long id;

    @Schema(description = "Plan name", example = "premium-basic")
    private String name;

    @Schema(description = "Display name", example = "Premium Basic")
    private String displayName;

    @Schema(description = "Plan description", example = "Bắt đầu với AI & khóa học")
    private String description;

    @Schema(description = "Duration in months", example = "1")
    private Integer durationMonths;

    @Schema(description = "Plan price", example = "79000")
    private BigDecimal price;

    @Schema(description = "Currency", example = "VND")
    private String currency;

    @Schema(description = "Plan type", example = "PREMIUM_BASIC")
    private PremiumPlan.PlanType planType;

    @Schema(description = "Student discount percentage", example = "50")
    private BigDecimal studentDiscountPercent;

    @Schema(description = "Student price after discount", example = "39500")
    private BigDecimal studentPrice;

    @Schema(description = "Plan features list")
    private List<String> features;

    @Schema(description = "Whether plan is active", example = "true")
    private Boolean isActive;

    @Schema(description = "Maximum subscribers allowed")
    private Integer maxSubscribers;

    @Schema(description = "Current active subscribers count")
    private Long currentSubscribers;

    @Schema(description = "Whether plan is available for new subscriptions")
    private Boolean availableForSubscription;

    @Schema(description = "Plan creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Plan last update timestamp")
    private LocalDateTime updatedAt;
}