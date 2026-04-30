package com.exe.skillverse_backend.premium_service.repository;

import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
         * Find plan by type (single result)
         */
        Optional<PremiumPlan> findTopByPlanTypeAndIsActiveTrueOrderByCreatedAtDescIdDesc(
                        PremiumPlan.PlanType planType);

        default Optional<PremiumPlan> findByPlanTypeAndIsActiveTrue(PremiumPlan.PlanType planType) {
                return findTopByPlanTypeAndIsActiveTrueOrderByCreatedAtDescIdDesc(planType);
        }

        /**
         * Find all active plans by type (multiple plans, e.g. monthly/yearly)
         */
        List<PremiumPlan> findAllByPlanTypeAndIsActiveTrue(PremiumPlan.PlanType planType);

        /**
         * Find plans within price range
         */
        List<PremiumPlan> findByIsActiveTrueAndPriceBetweenOrderByPrice(
                        BigDecimal minPrice, BigDecimal maxPrice);

        /**
         * Find plans with active role-based discount pricing.
         */
        @Query("""
                        SELECT p FROM PremiumPlan p
                        WHERE p.isActive = true
                          AND COALESCE(p.discountPercent, p.studentDiscountPercent, 0) > 0
                        """)
        List<PremiumPlan> findPlansWithDiscountPricing();

        /**
         * Backward-compatible alias for older student-specific callers.
         */
        default List<PremiumPlan> findPlansWithStudentDiscount() {
                return findPlansWithDiscountPricing();
        }

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
         * Count active subscriptions for a plan (used in admin stats)
         */
        @Query("SELECT COUNT(s) FROM UserSubscription s WHERE s.plan = :plan AND s.isActive = true")
        Long countActiveSubscriptions(@Param("plan") PremiumPlan plan);

        /**
         * Count plans excluding a specific plan type (for admin validation)
         */
        long countByPlanTypeNot(PremiumPlan.PlanType planType);
}
