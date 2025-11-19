package com.exe.skillverse_backend.admin_service.service;

import com.exe.skillverse_backend.admin_service.dto.request.CreatePremiumPlanRequest;
import com.exe.skillverse_backend.admin_service.dto.request.UpdatePremiumPlanRequest;
import com.exe.skillverse_backend.admin_service.dto.response.AdminPremiumPlanResponse;

import java.util.List;

/**
 * Service interface for admin premium plan management
 */
public interface AdminPremiumService {

    /**
     * Get all premium plans (including inactive ones)
     * 
     * @return List of all premium plans with admin stats
     */
    List<AdminPremiumPlanResponse> getAllPlans();

    /**
     * Get premium plan by ID
     * 
     * @param planId Plan ID
     * @return Premium plan details
     */
    AdminPremiumPlanResponse getPlanById(Long planId);

    /**
     * Create a new premium plan
     * Validation:
     * - Maximum 4 plans (excluding FREE_TIER)
     * - Cannot create FREE_TIER
     * - Plan name must be unique
     * 
     * @param request Create plan request
     * @return Created premium plan
     */
    AdminPremiumPlanResponse createPlan(CreatePremiumPlanRequest request);

    /**
     * Update an existing premium plan
     * Validation:
     * - Cannot update FREE_TIER
     * - Cannot change plan type
     * 
     * @param planId  Plan ID
     * @param request Update plan request
     * @return Updated premium plan
     */
    AdminPremiumPlanResponse updatePlan(Long planId, UpdatePremiumPlanRequest request);

    /**
     * Delete a premium plan
     * Validation:
     * - Cannot delete FREE_TIER
     * - Cannot delete plan with active subscribers
     * 
     * @param planId Plan ID
     */
    void deletePlan(Long planId);

    /**
     * Toggle plan active status
     * Validation:
     * - Cannot deactivate FREE_TIER
     * 
     * @param planId Plan ID
     * @return Updated premium plan
     */
    AdminPremiumPlanResponse togglePlanActive(Long planId);
}
