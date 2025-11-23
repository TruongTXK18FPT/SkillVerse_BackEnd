package com.exe.skillverse_backend.premium_service.controller;

import com.exe.skillverse_backend.premium_service.dto.request.CreateSubscriptionRequest;
import com.exe.skillverse_backend.premium_service.dto.response.PremiumPlanResponse;
import com.exe.skillverse_backend.premium_service.dto.response.UserSubscriptionResponse;
import com.exe.skillverse_backend.premium_service.service.PremiumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/premium")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Premium Service", description = "Premium subscription management")
public class PremiumController {

    private final PremiumService premiumService;

    @GetMapping("/plans")
    @Operation(summary = "Get available premium plans")
    public ResponseEntity<List<PremiumPlanResponse>> getAvailablePlans() {
        log.info("Fetching available premium plans");
        List<PremiumPlanResponse> plans = premiumService.getAvailablePlans();
        return ResponseEntity.ok(plans);
    }

    @GetMapping("/plans/{planId}")
    @Operation(summary = "Get premium plan by ID")
    public ResponseEntity<PremiumPlanResponse> getPlanById(
            @Parameter(description = "Premium plan ID") @PathVariable Long planId) {
        log.info("Fetching premium plan with ID: {}", planId);
        return premiumService.getPlanById(planId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/subscribe")
    @Operation(summary = "Create premium subscription")
    public ResponseEntity<UserSubscriptionResponse> createSubscription(
            @Valid @RequestBody CreateSubscriptionRequest request,
            Authentication authentication) {

        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("Creating subscription for user: {}", userId);
        UserSubscriptionResponse response = premiumService.createSubscription(userId, request);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/subscription/current")
    @Operation(summary = "Get current subscription")
    public ResponseEntity<UserSubscriptionResponse> getCurrentSubscription(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("Fetching current subscription for user: {}", userId);
        return premiumService.getCurrentSubscription(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/subscription/history")
    @Operation(summary = "Get subscription history")
    public ResponseEntity<List<UserSubscriptionResponse>> getSubscriptionHistory(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("Fetching subscription history for user: {}", userId);
        List<UserSubscriptionResponse> history = premiumService.getSubscriptionHistory(userId);

        return ResponseEntity.ok(history);
    }

    @PutMapping("/subscription/cancel")
    @Operation(summary = "Cancel subscription")
    public ResponseEntity<String> cancelSubscription(
            @RequestParam(required = false) String reason,
            Authentication authentication) {

        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("Cancelling subscription for user: {}", userId);
        premiumService.cancelSubscription(userId, reason != null ? reason : "User requested cancellation");

        return ResponseEntity.ok("Subscription cancelled successfully");
    }

    @GetMapping("/status")
    @Operation(summary = "Check premium status")
    public ResponseEntity<Boolean> checkPremiumStatus(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("Checking premium status for user: {}", userId);
        boolean hasActivePremium = premiumService.hasActivePremiumSubscription(userId);

        return ResponseEntity.ok(hasActivePremium);
    }

    @PostMapping("/purchase-with-wallet")
    @Operation(summary = "Purchase premium subscription with wallet cash")
    public ResponseEntity<UserSubscriptionResponse> purchaseWithWallet(
            @RequestParam Long planId,
            @RequestParam(required = false, defaultValue = "false") Boolean applyStudentDiscount,
            Authentication authentication) {

        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("User {} purchasing premium plan {} with wallet", userId, planId);
        
        try {
            UserSubscriptionResponse response = premiumService.purchaseWithWalletCash(
                userId, 
                planId, 
                applyStudentDiscount != null && applyStudentDiscount
            );
            
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Failed to purchase premium with wallet: {}", e.getMessage());
            throw e;
        }
    }

    @PostMapping("/subscription/enable-auto-renewal")
    @Operation(summary = "Enable auto-renewal for subscription")
    public ResponseEntity<?> enableAutoRenewal(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("User {} requesting to enable auto-renewal", userId);
        
        try {
            premiumService.enableAutoRenewal(userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Auto-renewal enabled successfully. Your subscription will be automatically renewed."
            ));
        } catch (RuntimeException e) {
            log.error("Failed to enable auto-renewal: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/subscription/cancel-auto-renewal")
    @Operation(summary = "Cancel auto-renewal (no refund, subscription continues until end date)")
    public ResponseEntity<?> cancelAutoRenewal(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("User {} requesting auto-renewal cancellation", userId);
        
        try {
            premiumService.cancelAutoRenewal(userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Auto-renewal cancelled successfully. Your subscription will remain active until the end date."
            ));
        } catch (RuntimeException e) {
            log.error("Failed to cancel auto-renewal: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/subscription/cancel-with-refund")
    @Operation(summary = "Cancel subscription with refund (24h=100%, 1-3days=50%, >3days=0%)")
    public ResponseEntity<?> cancelSubscriptionWithRefund(
            @RequestParam(required = false) String reason,
            Authentication authentication) {

        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("User {} requesting subscription cancellation with refund", userId);
        
        try {
            double refundAmount = premiumService.cancelSubscriptionWithRefund(userId, reason);
            
            String message = refundAmount > 0 
                ? "Subscription cancelled successfully. Refund amount: " + refundAmount + " VND"
                : "Auto-renewal cancelled. No refund available (over 3 days).";
            
            return ResponseEntity.ok(new RefundResponse(
                true,
                message,
                refundAmount
            ));
        } catch (RuntimeException e) {
            log.error("Failed to cancel subscription with refund: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new RefundResponse(
                false,
                e.getMessage(),
                0.0
            ));
        }
    }

    @GetMapping("/subscription/refund-eligibility")
    @Operation(summary = "Get refund eligibility details (percentage and amount)")
    public ResponseEntity<PremiumService.RefundEligibility> checkRefundEligibility(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("Checking refund eligibility for user: {}", userId);
        PremiumService.RefundEligibility eligibility = premiumService.getRefundEligibility(userId);

        return ResponseEntity.ok(eligibility);
    }

    // Inner response classes
    private record RefundResponse(boolean success, String message, double refundAmount) {}
}
