package com.exe.skillverse_backend.payment_service.controller;

import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import com.exe.skillverse_backend.payment_service.service.PaymentService;
import com.exe.skillverse_backend.payment_service.service.impl.PayOSGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * PayOS webhook controller for handling payment callbacks
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "PayOS Webhook", description = "PayOS payment webhook handling")
public class PayOSWebhookController {

    private final PaymentService paymentService;
    private final PayOSGatewayService payOSGatewayService;

    @PostMapping("/callback/payos")
    @Operation(summary = "Handle PayOS webhook callback", description = "Process PayOS payment webhook with signature validation")
    public ResponseEntity<Map<String, String>> handlePayOSWebhook(
            @RequestHeader(value = "x-payos-signature", required = false) String signature,
            @RequestBody Map<String, Object> payload) {

        log.info("Received PayOS webhook: {}", payload);

        try {
            // Extract payment information (PayOS usually wraps inside 'data')
            String orderCode;
            String status;
            Object dataObj = payload.get("data");
            if (dataObj instanceof Map<?, ?> data) {
                Object oc = data.get("orderCode");
                Object st = data.get("status");
                orderCode = oc != null ? String.valueOf(oc) : String.valueOf(payload.get("orderCode"));
                status = st != null ? String.valueOf(st) : String.valueOf(payload.get("status"));
            } else {
                orderCode = String.valueOf(payload.get("orderCode"));
                status = String.valueOf(payload.get("status"));
            }

            // Fallback: if no explicit status, use success flag
            if (status == null || status.equals("null") || status.isEmpty()) {
                Object success = payload.get("success");
                status = (success instanceof Boolean && (Boolean) success) ? "PAID" : "FAILED";
            }

            String metadata = payload.toString(); // Store raw payload

            // Validate orderCode before processing
            if (orderCode == null || orderCode.equals("null") || orderCode.isEmpty()) {
                log.error("PayOS webhook missing orderCode: {}", payload);
                return ResponseEntity.badRequest().body(Map.of("error", "Missing orderCode"));
            }

            // PayOS sends signature in body; header is only fallback for compatibility.
            String bodySignature = payload.get("signature") != null
                    ? String.valueOf(payload.get("signature"))
                    : null;
            String effectiveSignature = (bodySignature != null && !bodySignature.isBlank())
                    ? bodySignature
                    : signature;

            boolean hasSignature = effectiveSignature != null && !effectiveSignature.isBlank();
            if (hasSignature) {
                boolean isValid = payOSGatewayService.validateCallback(effectiveSignature, payload);
                if (!isValid) {
                    log.warn("Invalid PayOS webhook signature");
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid signature"));
                }
            } else {
                // Fallback hardening: no signature => verify directly with PayOS before applying
                // any local state transition to prevent forged callbacks.
                PaymentTransaction.PaymentStatus verifiedStatus = payOSGatewayService.verifyPayment(orderCode);
                status = switch (verifiedStatus) {
                    case COMPLETED -> "PAID";
                    case CANCELLED -> "CANCELLED";
                    case FAILED -> "FAILED";
                    default -> null;
                };

                if (status == null) {
                    log.warn("Unsigned webhook ignored because gateway status is not terminal yet. orderCode={}",
                            orderCode);
                    return ResponseEntity.ok(Map.of("message", "Webhook received - payment not completed yet"));
                }
            }
            
            // Ignore test webhooks from PayOS (orderCode like "123", "456", etc.)
            if (orderCode.matches("^\\d{1,3}$")) {
                log.warn("⚠️ Ignoring test webhook from PayOS - orderCode: {}", orderCode);
                return ResponseEntity.ok(Map.of("message", "Test webhook ignored"));
            }

            log.info("Processing PayOS webhook - orderCode: {}, status: {}", orderCode, status);

            // Process the callback
            paymentService.processPaymentCallback(orderCode, status, metadata);

            log.info("PayOS webhook processed successfully for order: {}", orderCode);
            return ResponseEntity.ok(Map.of("message", "Webhook processed successfully"));

        } catch (Exception e) {
            log.error("❌ Error processing PayOS webhook", e);
            log.error("❌ Error details - Message: {}, Type: {}", e.getMessage(), e.getClass().getSimpleName());
            log.error("❌ Payload that caused error: {}", payload);
            
            // Return error but don't expose internal details to PayOS
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                        "error", "Failed to process webhook",
                        "message", e.getMessage() != null ? e.getMessage() : "Unknown error"
                    ));
        }
    }
}
