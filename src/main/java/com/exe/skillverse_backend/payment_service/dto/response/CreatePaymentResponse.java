package com.exe.skillverse_backend.payment_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for payment creation containing payment gateway URL
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payment creation response with gateway URL")
public class CreatePaymentResponse {

    @Schema(description = "Internal transaction reference", example = "TXN_1695123456789_123")
    private String transactionReference;

    @Schema(description = "Payment gateway checkout URL")
    private String checkoutUrl;

    @Schema(description = "Payment gateway reference ID")
    private String gatewayReferenceId;

    @Schema(description = "QR code URL for mobile payments (if supported)")
    private String qrCodeUrl;

    @Schema(description = "Deep link for mobile app payments (if supported)")
    private String deepLinkUrl;

    @Schema(description = "Payment expires at timestamp (ISO format)")
    private String expiresAt;

    @Schema(description = "Success message", example = "Payment created successfully")
    private String message;
}