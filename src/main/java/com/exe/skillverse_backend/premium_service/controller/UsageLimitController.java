package com.exe.skillverse_backend.premium_service.controller;

import com.exe.skillverse_backend.premium_service.dto.response.FeatureLimitInfo;
import com.exe.skillverse_backend.premium_service.dto.response.UsageCheckResult;
import com.exe.skillverse_backend.premium_service.entity.FeatureType;
import com.exe.skillverse_backend.premium_service.service.UsageLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for user usage limits and tracking
 */
@RestController
@RequestMapping("/api/usage")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Usage Limits", description = "User endpoints for checking usage limits and current usage")
public class UsageLimitController {

    private final UsageLimitService usageLimitService;

    /**
     * Get all usage information for current user
     */
    @GetMapping("/my-usage")
    @Operation(summary = "Get my usage", description = "Get all feature limits and current usage for the authenticated user's plan")
    public ResponseEntity<List<FeatureLimitInfo>> getMyUsage(Authentication authentication) {
        Long userId = getUserIdFromAuth(authentication);
        log.info("User {} fetching all usage info", userId);

        List<FeatureLimitInfo> usage = usageLimitService.getUserPlanLimits(userId);
        return ResponseEntity.ok(usage);
    }

    /**
     * Get usage for a specific feature
     */
    @GetMapping("/my-usage/{featureType}")
    @Operation(summary = "Get feature usage", description = "Get usage information for a specific feature")
    public ResponseEntity<FeatureLimitInfo> getFeatureUsage(
            @Parameter(description = "Feature type to check") @PathVariable FeatureType featureType,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        log.info("User {} fetching usage for feature {}", userId, featureType);

        FeatureLimitInfo usage = usageLimitService.getUserUsage(userId, featureType);
        return ResponseEntity.ok(usage);
    }

    /**
     * Check if user can use a feature (without recording usage)
     */
    @GetMapping("/can-use/{featureType}")
    @Operation(summary = "Check if can use feature", description = "Check if user can use a feature without recording usage. Useful for UI to show/hide features.")
    public ResponseEntity<UsageCheckResult> canUseFeature(
            @Parameter(description = "Feature type to check") @PathVariable FeatureType featureType,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        log.debug("User {} checking if can use feature {}", userId, featureType);

        UsageCheckResult result = usageLimitService.canUseFeature(userId, featureType);
        return ResponseEntity.ok(result);
    }

    /**
     * Extract user ID from JWT authentication
     */
    private Long getUserIdFromAuth(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return Long.valueOf(jwt.getClaimAsString("userId"));
    }
}
