package com.exe.skillverse_backend.premium_service.dto.request;

import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a premium subscription
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Premium subscription creation request")
public class CreateSubscriptionRequest {

    @NotNull(message = "Plan ID is required")
    @Schema(description = "Premium plan ID", example = "1")
    private Long planId;

    @NotNull(message = "Payment method is required")
    @Schema(description = "Payment method", example = "PAYOS")
    private PaymentTransaction.PaymentMethod paymentMethod;

    @Schema(description = "Whether to apply student discount (requires .edu email)", example = "false")
    @Builder.Default
    private Boolean applyStudentDiscount = false;

    @Schema(description = "Enable auto-renewal", example = "false")
    @Builder.Default
    private Boolean autoRenew = false;

    @Schema(description = "Success callback URL after payment")
    private String successUrl;

    @Schema(description = "Cancel callback URL if payment cancelled")
    private String cancelUrl;

    @Schema(description = "Coupon code for additional discounts")
    private String couponCode;
}