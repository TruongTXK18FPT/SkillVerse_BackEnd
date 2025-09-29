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
}
