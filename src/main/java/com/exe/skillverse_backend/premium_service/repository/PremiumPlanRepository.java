package com.exe.skillverse_backend.premium_service.repository;

import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Repository for PremiumPlan entity
 */
@Repository
public interface PremiumPlanRepository extends JpaRepository<PremiumPlan, Long> {

        /**
         * Find plan by name
         */
        Optional<PremiumPlan> findByName(String name);

        /**
         * Find all active plans
         */
        List<PremiumPlan> findByIsActiveTrueOrderByPrice();

        /**
         * Find plan by type
         */
        Optional<PremiumPlan> findByPlanTypeAndIsActiveTrue(PremiumPlan.PlanType planType);

        /**
         * Find plans within price range
         */
        List<PremiumPlan> findByIsActiveTrueAndPriceBetweenOrderByPrice(
                        BigDecimal minPrice, BigDecimal maxPrice);

        /**
         * Find plans with student discounts
         */
        @Query("SELECT p FROM PremiumPlan p WHERE p.isActive = true AND p.studentDiscountPercent > 0")
        List<PremiumPlan> findPlansWithStudentDiscount();

        /**
         * Find plans by duration
         */
        List<PremiumPlan> findByIsActiveTrueAndDurationMonthsOrderByPrice(Integer durationMonths);

        /**
         * Get all plan types that are currently active
         */
        @Query("SELECT DISTINCT p.planType FROM PremiumPlan p WHERE p.isActive = true")
        List<PremiumPlan.PlanType> getActivePlanTypes();

        /**
         * Count active subscriptions for a plan
         */
        @Query("SELECT COUNT(s) FROM UserSubscription s WHERE s.plan = :plan AND s.isActive = true")
        Long countActiveSubscriptions(@Param("plan") PremiumPlan plan);

        /**
         * Find plans available for new subscriptions
         */
        @Query("SELECT p FROM PremiumPlan p WHERE p.isActive = true " +
                        "AND (p.maxSubscribers IS NULL OR " +
                        "     (SELECT COUNT(s) FROM UserSubscription s WHERE s.plan = p AND s.isActive = true) < p.maxSubscribers)")
        List<PremiumPlan> findAvailablePlans();

        /**
         * Count plans excluding a specific plan type (for admin validation)
         */
        long countByPlanTypeNot(PremiumPlan.PlanType planType);
}