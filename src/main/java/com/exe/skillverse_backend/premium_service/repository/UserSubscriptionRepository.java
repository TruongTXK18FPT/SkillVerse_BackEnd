package com.exe.skillverse_backend.premium_service.repository;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import com.exe.skillverse_backend.premium_service.entity.UserSubscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for UserSubscription entity
 */
@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    /**
     * Find user's current active subscription
     */
    Optional<UserSubscription> findByUserAndIsActiveTrueAndStatus(
            User user, UserSubscription.SubscriptionStatus status);

    /**
     * Find user's active subscription (simplified)
     */
    Optional<UserSubscription> findByUserAndIsActiveTrue(User user);

    /**
     * Find all subscriptions for a user
     */
    Page<UserSubscription> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    /**
     * Find subscriptions by plan
     */
    List<UserSubscription> findByPlan(PremiumPlan plan);

    /**
     * Find active subscriptions by plan
     */
    List<UserSubscription> findByPlanAndIsActiveTrue(PremiumPlan plan);

    /**
     * Find subscriptions expiring soon
     */
    @Query("SELECT s FROM UserSubscription s WHERE s.isActive = true " +
            "AND s.status = 'ACTIVE' AND s.endDate BETWEEN :now AND :cutoffDate")
    List<UserSubscription> findSubscriptionsExpiringSoon(
            @Param("now") LocalDateTime now,
            @Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find expired subscriptions that are still marked as active
     */
    @Query("SELECT s FROM UserSubscription s WHERE s.isActive = true " +
            "AND s.status = 'ACTIVE' AND s.endDate < :now")
    List<UserSubscription> findExpiredActiveSubscriptions(@Param("now") LocalDateTime now);

    /**
     * Check if user has any active subscription
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM UserSubscription s " +
            "WHERE s.user = :user AND s.isActive = true AND s.status = 'ACTIVE' " +
            "AND s.startDate <= :now AND s.endDate > :now")
    Boolean hasActiveSubscription(@Param("user") User user, @Param("now") LocalDateTime now);

    /**
     * Find user's subscription history by plan type
     */
    @Query("SELECT s FROM UserSubscription s JOIN s.plan p WHERE s.user = :user " +
            "AND p.planType = :planType ORDER BY s.createdAt DESC")
    List<UserSubscription> findByUserAndPlanType(
            @Param("user") User user,
            @Param("planType") PremiumPlan.PlanType planType);

    /**
     * Count total subscriptions for a plan
     */
    Long countByPlan(PremiumPlan plan);

    /**
     * Find subscriptions eligible for auto-renewal
     */
    @Query("SELECT s FROM UserSubscription s WHERE s.autoRenew = true " +
            "AND s.isActive = true AND s.status = 'ACTIVE' " +
            "AND s.endDate BETWEEN :now AND :renewalWindow")
    List<UserSubscription> findSubscriptionsForAutoRenewal(
            @Param("now") LocalDateTime now,
            @Param("renewalWindow") LocalDateTime renewalWindow);

    /**
     * Find student subscriptions
     */
    List<UserSubscription> findByIsStudentSubscriptionTrueAndIsActiveTrue();

    /**
     * Get subscription statistics by plan type
     */
    @Query("SELECT p.planType, COUNT(s), COUNT(CASE WHEN s.isActive = true THEN 1 END) " +
            "FROM UserSubscription s JOIN s.plan p " +
            "GROUP BY p.planType")
    List<Object[]> getSubscriptionStatsByPlanType();

    /**
     * Bulk update expired subscriptions
     */
    @Modifying
    @Query("UPDATE UserSubscription s SET s.isActive = false, s.status = 'EXPIRED' " +
            "WHERE s.isActive = true AND s.status = 'ACTIVE' AND s.endDate < :now")
    int markExpiredSubscriptions(@Param("now") LocalDateTime now);

    /**
     * Find user's latest subscription for a specific plan type
     */
    @Query("SELECT s FROM UserSubscription s JOIN s.plan p WHERE s.user = :user " +
            "AND p.planType = :planType ORDER BY s.createdAt DESC")
    Page<UserSubscription> findLatestSubscriptionByUserAndPlanType(
            @Param("user") User user,
            @Param("planType") PremiumPlan.PlanType planType,
            Pageable pageable);
}