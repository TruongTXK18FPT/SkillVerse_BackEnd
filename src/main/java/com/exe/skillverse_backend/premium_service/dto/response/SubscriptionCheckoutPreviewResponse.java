package com.exe.skillverse_backend.premium_service.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Preview DTO for premium checkout and upgrade pricing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionCheckoutPreviewResponse {

    private boolean eligible;
    private boolean upgrade;
    private boolean samePlan;
    private boolean downgrade;
    private Long buyerUserId;
    private Long targetUserId;
    private Long currentSubscriptionId;
    private PremiumPlanResponse currentPlan;
    private PremiumPlanResponse targetPlan;
    private BigDecimal fullPrice;
    private BigDecimal effectivePrice;
    private BigDecimal amountDue;
    private BigDecimal currentPlanCredit;
    private BigDecimal proratedTargetPrice;
    private Long remainingDays;
    private LocalDateTime nextRenewalDate;
    private String currency;
    private PricingMode pricingMode;
    private String message;

    public enum PricingMode {
        FULL_PURCHASE,
        UPGRADE_PRORATED,
        UPGRADE_GRACE_WINDOW,
        UPGRADE_FULL_PRICE,
        UPGRADE_NOT_ALLOWED,
        CURRENT_PLAN,
        DOWNGRADE_NOT_ALLOWED,
        DOWNGRADE_SCHEDULED
    }
}
