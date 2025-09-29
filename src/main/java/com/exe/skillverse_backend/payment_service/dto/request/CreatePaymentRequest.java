package com.exe.skillverse_backend.payment_service.dto.request;

import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for creating a payment transaction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payment creation request")
public class CreatePaymentRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1000", message = "Minimum payment amount is 1,000 VND")
    @DecimalMax(value = "100000000", message = "Maximum payment amount is 100,000,000 VND")
    @Schema(description = "Payment amount in VND", example = "79000")
    private BigDecimal amount;

    @Builder.Default
    @Pattern(regexp = "^(VND|USD)$", message = "Currency must be VND or USD")
    @Schema(description = "Payment currency", example = "VND")
    private String currency = "VND";

    @NotNull(message = "Payment type is required")
    @Schema(description = "Type of payment", example = "PREMIUM_SUBSCRIPTION")
    private PaymentTransaction.PaymentType type;

    @NotNull(message = "Payment method is required")
    @Schema(description = "Payment method", example = "PAYOS")
    private PaymentTransaction.PaymentMethod paymentMethod;

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    @Schema(description = "Payment description", example = "Premium Basic Subscription")
    private String description;

    @Schema(description = "Premium plan ID (required for PREMIUM_SUBSCRIPTION type)")
    private Long planId;

    @Schema(description = "Course ID (required for COURSE_PURCHASE type)")
    private Long courseId;

    @Schema(description = "Additional metadata as JSON string")
    private String metadata;

    @Schema(description = "Success callback URL")
    private String successUrl;

    @Schema(description = "Cancel callback URL")
    private String cancelUrl;
}