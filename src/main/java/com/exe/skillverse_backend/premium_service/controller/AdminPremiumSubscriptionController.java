package com.exe.skillverse_backend.premium_service.controller;

import com.exe.skillverse_backend.premium_service.dto.response.UserSubscriptionResponse;
import com.exe.skillverse_backend.premium_service.service.PremiumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin Premium Subscription Controller - Admin-only APIs for premium subscription management
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/premium")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Premium Subscriptions", description = "Admin premium subscription management")
public class AdminPremiumSubscriptionController {
    
    private final PremiumService premiumService;
    
    /**
     * Get all premium subscriptions with filtering
     */
    @GetMapping("/subscriptions")
    @Operation(summary = "Get all premium subscriptions", description = "Retrieve all premium subscriptions with optional filters")
    public ResponseEntity<Page<UserSubscriptionResponse>> getAllSubscriptions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long planId,
            Pageable pageable
    ) {
        log.info("Admin fetching all premium subscriptions - status: {}, userId: {}", status, userId);
        Page<UserSubscriptionResponse> subscriptions = premiumService.getAllSubscriptionsAdmin(
            status, userId, planId, pageable
        );
        return ResponseEntity.ok(subscriptions);
    }
    
    /**
     * Get premium subscription detail
     */
    @GetMapping("/subscriptions/{id}")
    @Operation(summary = "Get subscription detail", description = "Retrieve detailed subscription information")
    public ResponseEntity<UserSubscriptionResponse> getSubscriptionDetail(@PathVariable Long id) {
        log.info("Admin fetching subscription detail for id: {}", id);
        return premiumService.getSubscriptionByIdAdmin(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get premium subscription statistics
     */
    @GetMapping("/statistics")
    @Operation(summary = "Get premium statistics", description = "Retrieve premium subscription statistics")
    public ResponseEntity<Map<String, Object>> getPremiumStatistics() {
        log.info("Admin fetching premium statistics");
        Map<String, Object> stats = premiumService.getPremiumStatistics();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Get revenue from premium subscriptions
     */
    @GetMapping("/revenue")
    @Operation(summary = "Get premium revenue", description = "Calculate total revenue from premium subscriptions")
    public ResponseEntity<Map<String, Object>> getPremiumRevenue(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        log.info("Admin fetching premium revenue");
        
        Map<String, Object> revenue = new HashMap<>();
        // This will be implemented in service
        revenue.put("totalRevenue", 0);
        revenue.put("activeSubscriptions", 0);
        revenue.put("newSubscriptions", 0);
        
        return ResponseEntity.ok(revenue);
    }
}
