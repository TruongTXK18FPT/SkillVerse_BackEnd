package com.exe.skillverse_backend.premium_service.controller;

import com.exe.skillverse_backend.premium_service.dto.response.PremiumPlanResponse;
import com.exe.skillverse_backend.premium_service.dto.response.RecruiterSubscriptionInfoResponse;
import com.exe.skillverse_backend.premium_service.dto.response.UserSubscriptionResponse;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import com.exe.skillverse_backend.premium_service.service.PremiumService;
import com.exe.skillverse_backend.premium_service.service.RecruiterSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recruiter/subscription")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('RECRUITER')")
@Tag(name = "Recruiter Subscription", description = "Recruiter Pro subscription management — quota, highlight, AI candidate suggestion")
public class RecruiterSubscriptionController {

    private final RecruiterSubscriptionService recruiterSubscriptionService;
    private final PremiumService premiumService;

    private boolean isRecruiterPlan(PremiumPlanResponse plan) {
        if (plan == null) {
            return false;
        }

        return plan.getPlanType() == PremiumPlan.PlanType.RECRUITER_PRO
                || plan.getTargetRole() == PremiumPlan.TargetRole.RECRUITER;
    }

    // ────────────────────────────── Info ──────────────────────────────

    @GetMapping("/info")
    @Operation(summary = "Get current recruiter subscription info (quota, features, remaining days)")
    public ResponseEntity<RecruiterSubscriptionInfoResponse> getSubscriptionInfo(Authentication authentication) {
        Long userId = extractUserId(authentication);
        log.info("Fetching recruiter subscription info for user: {}", userId);
        RecruiterSubscriptionInfoResponse info = recruiterSubscriptionService.getSubscriptionInfo(userId);
        return ResponseEntity.ok(info);
    }

    @GetMapping("/status")
    @Operation(summary = "Check if current user has active Recruiter Pro subscription")
    public ResponseEntity<Map<String, Object>> checkStatus(Authentication authentication) {
        Long userId = extractUserId(authentication);
        boolean active = recruiterSubscriptionService.hasActiveRecruiterSubscription(userId);
        return ResponseEntity.ok(Map.of(
                "hasSubscription", active,
                "canPostJob", active,
                "canHighlightJob", active && recruiterSubscriptionService.canHighlightJob(userId),
                "canUseAICandidateSuggestion", active && recruiterSubscriptionService.canUseAICandidateSuggestion(userId)
        ));
    }

    // ────────────────────────────── Plans ─────────────────────────────

    @GetMapping("/plans")
    @Operation(summary = "Get available Recruiter Pro plans (monthly / yearly)")
    public ResponseEntity<List<PremiumPlanResponse>> getRecruiterPlans() {
        log.info("Fetching available Recruiter Pro plans");
        List<PremiumPlanResponse> plans = premiumService.getAvailablePlansByTargetRole(
                PremiumPlan.TargetRole.RECRUITER,
                false
        ).stream()
                .filter(this::isRecruiterPlan)
                .toList();
        return ResponseEntity.ok(plans);
    }

    // ────────────────────────────── Purchase ──────────────────────────

    @PostMapping("/purchase/{planId}")
    @Operation(summary = "Purchase Recruiter Pro subscription using wallet cash")
    public ResponseEntity<?> purchaseRecruiterPlan(
            @PathVariable Long planId,
            Authentication authentication) {

        Long userId = extractUserId(authentication);
        log.info("User {} purchasing Recruiter Pro plan {}", userId, planId);

        try {
            UserSubscriptionResponse response = premiumService.purchaseWithWalletCash(
                    userId, planId, false);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Failed to purchase Recruiter Pro plan: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    // ────────────────────────────── Quota check ──────────────────────

    @GetMapping("/can-post")
    @Operation(summary = "Check if recruiter can post a new job (subscription + quota)")
    public ResponseEntity<Map<String, Object>> canPostJob(Authentication authentication) {
        Long userId = extractUserId(authentication);
        try {
            // Don't actually record — just check
            boolean hasSub = recruiterSubscriptionService.hasActiveRecruiterSubscription(userId);
            RecruiterSubscriptionInfoResponse info = recruiterSubscriptionService.getSubscriptionInfo(userId);
            return ResponseEntity.ok(Map.of(
                    "canPost", hasSub && (info.isJobPostingUnlimited() || info.getJobPostingRemaining() > 0),
                    "remaining", info.getJobPostingRemaining() != null ? info.getJobPostingRemaining() : 0,
                    "limit", info.getJobPostingLimit() != null ? info.getJobPostingLimit() : 0,
                    "used", info.getJobPostingUsed() != null ? info.getJobPostingUsed() : 0
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "canPost", false,
                    "remaining", 0,
                    "limit", 0,
                    "used", 0,
                    "reason", e.getMessage()
            ));
        }
    }

    // ────────────────────────────── Helper ────────────────────────────

    private Long extractUserId(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return Long.valueOf(jwt.getClaimAsString("userId"));
    }
}
