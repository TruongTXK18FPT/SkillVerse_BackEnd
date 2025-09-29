package com.exe.skillverse_backend.payment_service.dto.response;

import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for payment transaction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payment transaction response")
public class PaymentTransactionResponse {

    @Schema(description = "Transaction ID", example = "1")
    private Long id;

    @Schema(description = "User ID", example = "123")
    private Long userId;

    @Schema(description = "Payment amount", example = "79000")
    private BigDecimal amount;

    @Schema(description = "Payment currency", example = "VND")
    private String currency;

    @Schema(description = "Payment type", example = "PREMIUM_SUBSCRIPTION")
    private PaymentTransaction.PaymentType type;

    @Schema(description = "Payment status", example = "COMPLETED")
    private PaymentTransaction.PaymentStatus status;

    @Schema(description = "Payment method", example = "PAYOS")
    private PaymentTransaction.PaymentMethod paymentMethod;

    @Schema(description = "External reference ID from payment gateway")
    private String referenceId;

    @Schema(description = "Internal transaction reference", example = "TXN_1695123456789_123")
    private String internalReference;

    @Schema(description = "Payment description", example = "Premium Basic Subscription")
    private String description;

    @Schema(description = "Failure reason if payment failed")
    private String failureReason;

    @Schema(description = "Transaction creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Transaction last update timestamp")
    private LocalDateTime updatedAt;
}