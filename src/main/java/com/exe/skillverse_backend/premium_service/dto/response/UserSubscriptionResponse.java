package com.exe.skillverse_backend.premium_service.dto.response;

import com.exe.skillverse_backend.premium_service.entity.UserSubscription;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for user subscription information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User subscription information")
public class UserSubscriptionResponse {

    @Schema(description = "Subscription ID", example = "1")
    private Long id;

    @Schema(description = "User ID", example = "123")
    private Long userId;

    @Schema(description = "User name")
    private String userName;

    @Schema(description = "User email")
    private String userEmail;

    @Schema(description = "User avatar URL")
    private String userAvatarUrl;

    @Schema(description = "Premium plan information")
    private PremiumPlanResponse plan;

    @Schema(description = "Subscription start date")
    private LocalDateTime startDate;

    @Schema(description = "Subscription end date")
    private LocalDateTime endDate;

    @Schema(description = "Whether subscription is active", example = "true")
    private Boolean isActive;

    @Schema(description = "Subscription status", example = "ACTIVE")
    private UserSubscription.SubscriptionStatus status;

    @Schema(description = "Legacy alias for discounted pricing, retained for backward compatibility", example = "false")
    private Boolean isStudentSubscription;

    @Schema(description = "Whether this subscription is using a discounted pricing policy", example = "false")
    private Boolean isDiscountedSubscription;

    @Schema(description = "Auto-renewal enabled", example = "false")
    private Boolean autoRenew;

    @Schema(description = "Renewal amount locked for the upcoming billing cycle", example = "249000")
    private BigDecimal renewalPrice;

    @Schema(description = "When the system will attempt wallet auto-renewal")
    private LocalDateTime renewalAttemptDate;

    @Schema(description = "When the current renewal amount was locked for the next cycle")
    private LocalDateTime renewalPriceLockedAt;

    @Schema(description = "Scheduled downgrade target plan, if the user has already queued a lower-tier plan")
    private PremiumPlanResponse scheduledChangePlan;

    @Schema(description = "When the scheduled downgrade will become active")
    private LocalDateTime scheduledChangeEffectiveDate;

    @Schema(description = "Whether auto-renewal will apply to the scheduled plan after the switch")
    private Boolean scheduledChangeAutoRenew;

    @Schema(description = "Renewal amount that will apply to the scheduled plan")
    private BigDecimal scheduledChangeRenewalPrice;

    @Schema(description = "When the system will attempt auto-renewal for the scheduled plan")
    private LocalDateTime scheduledChangeRenewalAttemptDate;

    @Schema(description = "Payment transaction ID that created this subscription")
    private Long paymentTransactionId;

    @Schema(description = "Days remaining in subscription", example = "25")
    private Long daysRemaining;

    @Schema(description = "Whether subscription is currently active and valid")
    private Boolean currentlyActive;

    @Schema(description = "Cancellation reason if cancelled")
    private String cancellationReason;

    @Schema(description = "When subscription was cancelled")
    private LocalDateTime cancelledAt;

    @Schema(description = "Subscription creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Subscription last update timestamp")
    private LocalDateTime updatedAt;
}
