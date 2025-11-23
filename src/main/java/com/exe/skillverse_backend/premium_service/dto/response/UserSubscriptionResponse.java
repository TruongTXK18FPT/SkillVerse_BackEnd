package com.exe.skillverse_backend.premium_service.dto.response;

import com.exe.skillverse_backend.premium_service.entity.UserSubscription;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    @Schema(description = "Whether this is a student subscription", example = "false")
    private Boolean isStudentSubscription;

    @Schema(description = "Auto-renewal enabled", example = "false")
    private Boolean autoRenew;

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