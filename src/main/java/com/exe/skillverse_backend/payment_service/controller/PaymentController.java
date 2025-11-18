package com.exe.skillverse_backend.payment_service.controller;

import com.exe.skillverse_backend.payment_service.dto.request.CreatePaymentRequest;
import com.exe.skillverse_backend.payment_service.dto.response.CreatePaymentResponse;
import com.exe.skillverse_backend.payment_service.dto.response.PaymentTransactionResponse;
import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import com.exe.skillverse_backend.payment_service.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Controller", description = "Payment processing and transaction management")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create")
    @Operation(summary = "Create a new payment", description = "Create a payment transaction for a subscription or service")
    public ResponseEntity<CreatePaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            Authentication authentication) {

        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("Creating payment for user: {}", userId);
        CreatePaymentResponse response = paymentService.createPayment(userId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/callback/{gatewayReference}")
    @Operation(summary = "Handle payment gateway callback", description = "Process callback from payment gateway")
    public ResponseEntity<String> handleCallback(
            @PathVariable String gatewayReference,
            @RequestParam String status,
            @RequestBody(required = false) String metadata) {

        log.info("Processing payment callback for gateway reference: {}", gatewayReference);
        PaymentTransaction transaction = paymentService.processPaymentCallback(gatewayReference, status, metadata);

        return ResponseEntity.ok("Callback processed successfully. Payment status: " + transaction.getStatus());
    }

    @GetMapping("/history")
    @Operation(summary = "Get payment history", description = "Get payment history for the authenticated user")
    public ResponseEntity<List<PaymentTransactionResponse>> getPaymentHistory(
            Authentication authentication) {

        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("Fetching payment history for user: {}", userId);
        List<PaymentTransactionResponse> history = paymentService.getUserPaymentHistory(userId);

        return ResponseEntity.ok(history);
    }

    @GetMapping("/transaction/{internalReference}")
    @Operation(summary = "Get payment by reference", description = "Get payment details by internal reference")
    public ResponseEntity<PaymentTransactionResponse> getPaymentByReference(
            @PathVariable String internalReference) {

        log.info("Fetching payment by reference: {}", internalReference);
        return paymentService.getPaymentByReference(internalReference)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/verify/{internalReference}")
    @Operation(summary = "Verify & sync payment with gateway", description = "Verify payment status with PayOS gateway and update local payment if necessary (fallback for webhook)")
    public ResponseEntity<PaymentTransactionResponse> verifyPaymentWithGateway(
            @PathVariable String internalReference) {

        log.info(" Verifying payment with gateway for reference: {}", internalReference);
        
        try {
            // Verify with gateway and update status
            boolean verified = paymentService.verifyPaymentWithGateway(internalReference);
            
            if (verified) {
                var paymentOpt = paymentService.getPaymentByReference(internalReference);
                if (paymentOpt.isPresent()) {
                    log.info(" Payment verified and updated - Status: {}", paymentOpt.get().getStatus());
                    return ResponseEntity.ok(paymentOpt.get());
                }
            }
            
            log.warn(" Payment verification failed or not found: {}", internalReference);
            return ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            log.error(" Error verifying payment: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/transaction/id/{paymentId}")
    @Operation(summary = "Get payment by ID", description = "Get payment details by payment ID")
    public ResponseEntity<PaymentTransactionResponse> getPaymentById(
            @PathVariable Long paymentId) {

        log.info("Fetching payment by ID: {}", paymentId);
        return paymentService.getPaymentById(paymentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/cancel/{internalReference}")
    @Operation(summary = "Cancel payment", description = "Cancel a pending payment transaction")
    public ResponseEntity<String> cancelPayment(
            @PathVariable String internalReference,
            @RequestParam(required = false) String reason) {

        log.info("Cancelling payment: {}", internalReference);
        paymentService.cancelPayment(internalReference, reason);

        return ResponseEntity.ok("Payment cancelled successfully");
    }

    @PutMapping("/status/{internalReference}")
    @Operation(summary = "Update payment status", description = "Update the status of a payment transaction")
    public ResponseEntity<PaymentTransaction> updatePaymentStatus(
            @PathVariable String internalReference,
            @RequestParam PaymentTransaction.PaymentStatus status,
            @RequestParam(required = false) String failureReason) {

        log.info("Updating payment status for {}: {}", internalReference, status);
        PaymentTransaction transaction = paymentService.updatePaymentStatus(internalReference, status, failureReason);

        return ResponseEntity.ok(transaction);
    }
}