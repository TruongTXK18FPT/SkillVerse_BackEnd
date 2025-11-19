package com.exe.skillverse_backend.admin_service.controller;

import com.exe.skillverse_backend.admin_service.dto.request.CreatePremiumPlanRequest;
import com.exe.skillverse_backend.admin_service.dto.request.UpdatePremiumPlanRequest;
import com.exe.skillverse_backend.admin_service.dto.response.AdminPremiumPlanResponse;
import com.exe.skillverse_backend.admin_service.service.AdminPremiumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin controller for managing premium plans
 * Only accessible by users with ADMIN role
 */
@RestController
@RequestMapping("/api/admin/premium")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Premium Management", description = "Admin endpoints for managing premium plans")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPremiumController {

    private final AdminPremiumService adminPremiumService;

    /**
     * GET /api/admin/premium/plans - Get all premium plans (including inactive)
     */
    @GetMapping("/plans")
    @Operation(summary = "Get all premium plans", description = "Retrieve all premium plans with admin statistics")
    public ResponseEntity<List<AdminPremiumPlanResponse>> getAllPlans() {
        log.info("Admin fetching all premium plans");
        List<AdminPremiumPlanResponse> plans = adminPremiumService.getAllPlans();
        return ResponseEntity.ok(plans);
    }

    /**
     * GET /api/admin/premium/plans/{planId} - Get premium plan by ID
     */
    @GetMapping("/plans/{planId}")
    @Operation(summary = "Get premium plan by ID", description = "Retrieve detailed information about a specific premium plan")
    public ResponseEntity<AdminPremiumPlanResponse> getPlanById(
            @Parameter(description = "Premium plan ID") @PathVariable Long planId) {
        log.info("Admin fetching premium plan by ID: {}", planId);
        AdminPremiumPlanResponse plan = adminPremiumService.getPlanById(planId);
        return ResponseEntity.ok(plan);
    }

    /**
     * POST /api/admin/premium/plans - Create new premium plan
     * Validation:
     * - Maximum 4 plans (excluding FREE_TIER)
     * - Cannot create FREE_TIER
     * - Plan name must be unique
     */
    @PostMapping("/plans")
    @Operation(summary = "Create new premium plan", description = "Create a new premium plan. Maximum 4 plans allowed (excluding FREE_TIER). Cannot create FREE_TIER.")
    public ResponseEntity<AdminPremiumPlanResponse> createPlan(
            @Valid @RequestBody CreatePremiumPlanRequest request) {
        log.info("Admin creating new premium plan: {}", request.getName());
        AdminPremiumPlanResponse createdPlan = adminPremiumService.createPlan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPlan);
    }

    /**
     * PUT /api/admin/premium/plans/{planId} - Update premium plan
     * Validation:
     * - Cannot update FREE_TIER
     */
    @PutMapping("/plans/{planId}")
    @Operation(summary = "Update premium plan", description = "Update an existing premium plan. Cannot update FREE_TIER.")
    public ResponseEntity<AdminPremiumPlanResponse> updatePlan(
            @Parameter(description = "Premium plan ID") @PathVariable Long planId,
            @Valid @RequestBody UpdatePremiumPlanRequest request) {
        log.info("Admin updating premium plan ID: {}", planId);
        AdminPremiumPlanResponse updatedPlan = adminPremiumService.updatePlan(planId, request);
        return ResponseEntity.ok(updatedPlan);
    }

    /**
     * DELETE /api/admin/premium/plans/{planId} - Delete premium plan
     * Validation:
     * - Cannot delete FREE_TIER
     * - Cannot delete plan with active subscribers
     */
    @DeleteMapping("/plans/{planId}")
    @Operation(summary = "Delete premium plan", description = "Delete a premium plan. Cannot delete FREE_TIER or plans with active subscribers.")
    public ResponseEntity<Void> deletePlan(
            @Parameter(description = "Premium plan ID") @PathVariable Long planId) {
        log.info("Admin deleting premium plan ID: {}", planId);
        adminPremiumService.deletePlan(planId);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/admin/premium/plans/{planId}/toggle-active - Toggle plan active
     * status
     * Validation:
     * - Cannot deactivate FREE_TIER
     */
    @PatchMapping("/plans/{planId}/toggle-active")
    @Operation(summary = "Toggle plan active status", description = "Activate or deactivate a premium plan. Cannot deactivate FREE_TIER.")
    public ResponseEntity<AdminPremiumPlanResponse> togglePlanActive(
            @Parameter(description = "Premium plan ID") @PathVariable Long planId) {
        log.info("Admin toggling active status for premium plan ID: {}", planId);
        AdminPremiumPlanResponse updatedPlan = adminPremiumService.togglePlanActive(planId);
        return ResponseEntity.ok(updatedPlan);
    }
}
