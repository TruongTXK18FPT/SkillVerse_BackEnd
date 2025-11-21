package com.exe.skillverse_backend.premium_service.repository;

import com.exe.skillverse_backend.premium_service.entity.FeatureType;
import com.exe.skillverse_backend.premium_service.entity.PlanFeatureLimits;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing plan feature limits
 */
@Repository
public interface PlanFeatureLimitsRepository extends JpaRepository<PlanFeatureLimits, Long> {

    /**
     * Find all limits for a specific plan
     */
    List<PlanFeatureLimits> findByPlan(PremiumPlan plan);

    /**
     * Find all active limits for a specific plan
     */
    List<PlanFeatureLimits> findByPlanAndIsActiveTrue(PremiumPlan plan);

    /**
     * Find a specific limit for a plan and feature type
     */
    Optional<PlanFeatureLimits> findByPlanAndFeatureType(PremiumPlan plan, FeatureType featureType);

    /**
     * Find active limit for a plan and feature type
     */
    Optional<PlanFeatureLimits> findByPlanAndFeatureTypeAndIsActiveTrue(PremiumPlan plan, FeatureType featureType);

    /**
     * Find all limits for a specific feature type across all plans
     */
    List<PlanFeatureLimits> findByFeatureType(FeatureType featureType);

    /**
     * Find all limits for a plan by plan ID
     */
    @Query("SELECT pfl FROM PlanFeatureLimits pfl WHERE pfl.plan.id = :planId")
    List<PlanFeatureLimits> findByPlanId(@Param("planId") Long planId);

    /**
     * Find active limits for a plan by plan ID
     */
    @Query("SELECT pfl FROM PlanFeatureLimits pfl WHERE pfl.plan.id = :planId AND pfl.isActive = true")
    List<PlanFeatureLimits> findActiveLimitsByPlanId(@Param("planId") Long planId);

    /**
     * Check if a limit exists for a plan and feature
     */
    boolean existsByPlanAndFeatureType(PremiumPlan plan, FeatureType featureType);

    /**
     * Delete all limits for a specific plan
     */
    void deleteByPlan(PremiumPlan plan);

    /**
     * Find all unlimited features for a plan
     */
    @Query("SELECT pfl FROM PlanFeatureLimits pfl WHERE pfl.plan = :plan AND pfl.isUnlimited = true AND pfl.isActive = true")
    List<PlanFeatureLimits> findUnlimitedFeatures(@Param("plan") PremiumPlan plan);

    /**
     * Find all boolean features (enabled/disabled) for a plan
     */
    @Query("SELECT pfl FROM PlanFeatureLimits pfl WHERE pfl.plan = :plan " +
            "AND pfl.featureType IN ('PRIORITY_SUPPORT', 'AD_FREE_EXPERIENCE') " +
            "AND pfl.isActive = true")
    List<PlanFeatureLimits> findBooleanFeatures(@Param("plan") PremiumPlan plan);

    /**
     * Get coin earning multiplier for a plan
     */
    @Query("SELECT pfl FROM PlanFeatureLimits pfl WHERE pfl.plan = :plan " +
            "AND pfl.featureType = 'COIN_EARNING_MULTIPLIER' " +
            "AND pfl.isActive = true")
    Optional<PlanFeatureLimits> findCoinMultiplier(@Param("plan") PremiumPlan plan);
}
