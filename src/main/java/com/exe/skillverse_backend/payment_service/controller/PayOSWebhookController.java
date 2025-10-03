package com.exe.skillverse_backend.payment_service.controller;

import com.exe.skillverse_backend.payment_service.service.PaymentService;
import com.exe.skillverse_backend.payment_service.service.impl.PayOSGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
            // Validate signature if provided
            if (signature != null && !signature.isEmpty()) {
                boolean isValid = payOSGatewayService.validateCallback(signature, payload);
                if (!isValid) {
                    log.warn("Invalid PayOS webhook signature");
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid signature"));
                }
            }

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

            // Process the callback
            paymentService.processPaymentCallback(orderCode, status, metadata);

            log.info("PayOS webhook processed successfully for order: {}", orderCode);
            return ResponseEntity.ok(Map.of("message", "Webhook processed successfully"));

        } catch (Exception e) {
            log.error("Error processing PayOS webhook: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to process webhook"));
        }
    }
}
