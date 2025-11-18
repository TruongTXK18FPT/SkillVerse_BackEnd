package com.exe.skillverse_backend.payment_service.controller;

import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import com.exe.skillverse_backend.payment_service.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Test controller for manually triggering payment callbacks
 * USE ONLY FOR DEVELOPMENT/TESTING
 */
@RestController
@RequestMapping("/api/payments/test")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Test", description = "Test endpoints for payment callbacks (DEV ONLY)")
public class PaymentTestController {

    private final PaymentService paymentService;

    /**
     * Manually trigger payment callback for testing
     * Use this when PayOS webhook cannot reach localhost
     * 
     * @param orderCode The order code from PaymentTransaction
     * @param status Payment status (PAID, CANCELLED, PROCESSING)
     * @return Result message
     */
    @PostMapping("/trigger-callback/{internalReference}")
    @Operation(summary = "Manually trigger payment callback", 
               description = "FOR TESTING ONLY - Simulate PayOS webhook callback using internal reference")
    public ResponseEntity<String> triggerCallback(
            @PathVariable String internalReference,
            @RequestParam(defaultValue = "PAID") String status) {
        
        log.warn("⚠️ MANUAL CALLBACK TRIGGERED - internalReference: {}, status: {}", internalReference, status);
        
        try {
            // Get payment by internal reference first
            var paymentOpt = paymentService.getPaymentByReference(internalReference);
            if (paymentOpt.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body("❌ Payment not found with internal reference: " + internalReference);
            }
            
            // Use the referenceId (external PayOS ID) for callback processing
            var payment = paymentOpt.get();
            String referenceId = payment.getReferenceId();
            
            if (referenceId == null || referenceId.isEmpty()) {
                // If no external reference, use internal reference
                referenceId = internalReference;
            }
            
            log.info("Processing callback with referenceId: {}", referenceId);
            
            String metadata = String.format("{\"orderCode\":\"%s\",\"status\":\"%s\",\"manual\":true}", 
                                          internalReference, status);
            
            PaymentTransaction transaction = paymentService.processPaymentCallback(
                referenceId, 
                status, 
                metadata
            );
            
            String message = String.format(
                "✅ Callback processed successfully!\n" +
                "Order: %s\n" +
                "Status: %s\n" +
                "Type: %s\n" +
                "Amount: %s\n" +
                "Transaction Status: %s",
                internalReference,
                status,
                transaction.getType(),
                transaction.getAmount(),
                transaction.getStatus()
            );
            
            log.info(message);
            return ResponseEntity.ok(message);
            
        } catch (Exception e) {
            log.error("❌ Failed to process callback: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                .body("Failed to process callback: " + e.getMessage());
        }
    }

    /**
     * Get payment transaction by order code for verification
     */
    @GetMapping("/check/{orderCode}")
    @Operation(summary = "Check payment status by order code")
    public ResponseEntity<?> checkPayment(@PathVariable String orderCode) {
        try {
            return paymentService.getPaymentByReference(orderCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}
