package com.exe.skillverse_backend.admin_service.service.impl;

import com.exe.skillverse_backend.admin_service.dto.request.CreatePremiumPlanRequest;
import com.exe.skillverse_backend.admin_service.dto.request.UpdatePremiumPlanRequest;
import com.exe.skillverse_backend.admin_service.dto.response.AdminPremiumPlanResponse;
import com.exe.skillverse_backend.admin_service.service.AdminPremiumService;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import com.exe.skillverse_backend.premium_service.entity.UserSubscription;
import com.exe.skillverse_backend.premium_service.repository.PremiumPlanRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminPremiumServiceImpl implements AdminPremiumService {

    private static final int MAX_PREMIUM_PLANS = 4; // Excluding FREE_TIER
    private static final String FREE_TIER_NAME = "free_tier";

    private final PremiumPlanRepository premiumPlanRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<AdminPremiumPlanResponse> getAllPlans() {
        log.info("Admin fetching all premium plans");
        List<PremiumPlan> plans = premiumPlanRepository.findAll();
        return plans.stream()
                .map(this::mapToAdminResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminPremiumPlanResponse getPlanById(Long planId) {
        log.info("Admin fetching premium plan by ID: {}", planId);
        PremiumPlan plan = premiumPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Premium plan not found with ID: " + planId));
        return mapToAdminResponse(plan);
    }

    @Override
    @Transactional
    public AdminPremiumPlanResponse createPlan(CreatePremiumPlanRequest request) {
        log.info("Admin creating new premium plan: {}", request.getName());

        // Validation 1: Cannot create FREE_TIER
        if (request.getPlanType() == PremiumPlan.PlanType.FREE_TIER) {
            throw new RuntimeException("Cannot create FREE_TIER plan. FREE_TIER is a system plan.");
        }

        // Validation 2: Check if plan name already exists
        if (premiumPlanRepository.findByName(request.getName()).isPresent()) {
            throw new RuntimeException("Plan with name '" + request.getName() + "' already exists");
        }

        // Validation 3: Maximum 4 premium plans (excluding FREE_TIER)
        long nonFreeTierCount = premiumPlanRepository.countByPlanTypeNot(PremiumPlan.PlanType.FREE_TIER);
        if (nonFreeTierCount >= MAX_PREMIUM_PLANS) {
            throw new RuntimeException("Maximum " + MAX_PREMIUM_PLANS
                    + " premium plans allowed (excluding FREE_TIER). Please delete an existing plan first.");
        }

        // Create new plan
        PremiumPlan plan = PremiumPlan.builder()
                .name(request.getName())
                .displayName(request.getDisplayName())
                .description(request.getDescription())
                .durationMonths(request.getDurationMonths())
                .price(request.getPrice())
                .currency("VND")
                .planType(request.getPlanType())
                .studentDiscountPercent(request.getStudentDiscountPercent())
                .features(request.getFeatures())
                .isActive(request.getIsActive())
                .maxSubscribers(request.getMaxSubscribers())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        PremiumPlan savedPlan = premiumPlanRepository.save(plan);
        log.info("Successfully created premium plan: {} (ID: {})", savedPlan.getName(), savedPlan.getId());

        return mapToAdminResponse(savedPlan);
    }

    @Override
    @Transactional
    public AdminPremiumPlanResponse updatePlan(Long planId, UpdatePremiumPlanRequest request) {
        log.info("Admin updating premium plan ID: {}", planId);

        PremiumPlan plan = premiumPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Premium plan not found with ID: " + planId));

        // Validation: Cannot update FREE_TIER
        if (plan.getPlanType() == PremiumPlan.PlanType.FREE_TIER) {
            throw new RuntimeException("Cannot update FREE_TIER plan. FREE_TIER is a system plan.");
        }

        // Update fields
        plan.setDisplayName(request.getDisplayName());
        plan.setDescription(request.getDescription());
        plan.setDurationMonths(request.getDurationMonths());
        plan.setPrice(request.getPrice());
        plan.setStudentDiscountPercent(request.getStudentDiscountPercent());
        plan.setFeatures(request.getFeatures());
        plan.setMaxSubscribers(request.getMaxSubscribers());

        if (request.getIsActive() != null) {
            plan.setIsActive(request.getIsActive());
        }

        plan.setUpdatedAt(LocalDateTime.now());

        PremiumPlan updatedPlan = premiumPlanRepository.save(plan);
        log.info("Successfully updated premium plan: {} (ID: {})", updatedPlan.getName(), updatedPlan.getId());

        return mapToAdminResponse(updatedPlan);
    }

    @Override
    @Transactional
    public void deletePlan(Long planId) {
        log.info("Admin deleting premium plan ID: {}", planId);

        PremiumPlan plan = premiumPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Premium plan not found with ID: " + planId));

        // Validation 1: Cannot delete FREE_TIER
        if (plan.getPlanType() == PremiumPlan.PlanType.FREE_TIER) {
            throw new RuntimeException("Cannot delete FREE_TIER plan. FREE_TIER is a system plan.");
        }

        // Validation 2: Cannot delete plan with active subscribers
        long activeSubscribers = plan.getSubscriptions().stream()
                .filter(sub -> sub.getStatus() == UserSubscription.SubscriptionStatus.ACTIVE)
                .count();

        if (activeSubscribers > 0) {
            throw new RuntimeException("Cannot delete plan with " + activeSubscribers
                    + " active subscribers. Please wait for subscriptions to expire or cancel them first.");
        }

        premiumPlanRepository.delete(plan);
        log.info("Successfully deleted premium plan: {} (ID: {})", plan.getName(), plan.getId());
    }

    @Override
    @Transactional
    public AdminPremiumPlanResponse togglePlanActive(Long planId) {
        log.info("Admin toggling active status for premium plan ID: {}", planId);

        PremiumPlan plan = premiumPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Premium plan not found with ID: " + planId));

        // Validation: Cannot deactivate FREE_TIER
        if (plan.getPlanType() == PremiumPlan.PlanType.FREE_TIER && plan.getIsActive()) {
            throw new RuntimeException("Cannot deactivate FREE_TIER plan. FREE_TIER must always be active.");
        }

        plan.setIsActive(!plan.getIsActive());
        plan.setUpdatedAt(LocalDateTime.now());

        PremiumPlan updatedPlan = premiumPlanRepository.save(plan);
        log.info("Successfully toggled active status for plan: {} (ID: {}) - New status: {}",
                updatedPlan.getName(), updatedPlan.getId(), updatedPlan.getIsActive());

        return mapToAdminResponse(updatedPlan);
    }

    /**
     * Map PremiumPlan entity to AdminPremiumPlanResponse DTO
     */
    private AdminPremiumPlanResponse mapToAdminResponse(PremiumPlan plan) {
        // Parse features JSON
        List<String> featuresList = null;
        try {
            if (plan.getFeatures() != null) {
                featuresList = objectMapper.readValue(plan.getFeatures(), new TypeReference<List<String>>() {
                });
            }
        } catch (Exception e) {
            log.warn("Failed to parse features JSON for plan {}: {}", plan.getName(), e.getMessage());
        }

        // Calculate current active subscribers
        long currentSubscribers = plan.getSubscriptions().stream()
                .filter(sub -> sub.getStatus() == UserSubscription.SubscriptionStatus.ACTIVE)
                .count();

        // Calculate total revenue (from all subscriptions that were paid)
        // Revenue = plan price * number of subscriptions (both active and expired)
        BigDecimal totalRevenue = BigDecimal.valueOf(
                plan.getSubscriptions().stream()
                        .filter(sub -> sub.getStatus() == UserSubscription.SubscriptionStatus.ACTIVE ||
                                sub.getStatus() == UserSubscription.SubscriptionStatus.EXPIRED)
                        .count())
                .multiply(plan.getPrice());

        return AdminPremiumPlanResponse.builder()
                .id(plan.getId())
                .name(plan.getName())
                .displayName(plan.getDisplayName())
                .description(plan.getDescription())
                .durationMonths(plan.getDurationMonths())
                .price(plan.getPrice())
                .currency(plan.getCurrency())
                .planType(plan.getPlanType())
                .studentDiscountPercent(plan.getStudentDiscountPercent())
                .studentPrice(plan.getStudentPrice())
                .features(featuresList)
                .isActive(plan.getIsActive())
                .maxSubscribers(plan.getMaxSubscribers())
                .currentSubscribers(currentSubscribers)
                .totalRevenue(totalRevenue)
                .availableForSubscription(plan.isAvailableForSubscription())
                .isFreeTier(plan.getPlanType() == PremiumPlan.PlanType.FREE_TIER)
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }
}
