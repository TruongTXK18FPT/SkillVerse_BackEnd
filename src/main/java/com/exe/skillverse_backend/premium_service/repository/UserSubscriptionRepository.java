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
         * Find user's active subscription (simplified).
         * Returns the highest priority active subscription (Premium > Free Tier).
         * Also validates that subscription is within valid date range.
         * Priority: PREMIUM_PLUS > PREMIUM_BASIC > STUDENT_PACK > FREE_TIER
         */
        @Query("""
                SELECT s FROM UserSubscription s 
                JOIN FETCH s.plan p
                WHERE s.user = :user 
                AND s.isActive = true 
                AND s.status = 'ACTIVE'
                AND s.startDate <= CURRENT_TIMESTAMP 
                AND s.endDate > CURRENT_TIMESTAMP
                ORDER BY CASE p.planType 
                    WHEN 'PREMIUM_PLUS' THEN 1 
                    WHEN 'PREMIUM_BASIC' THEN 2 
                    WHEN 'RECRUITER_PRO' THEN 2 
                    WHEN 'STUDENT_PACK' THEN 3 
                    WHEN 'FREE_TIER' THEN 4 
                    ELSE 5 
                END
        """)
        Optional<UserSubscription> findCurrentActiveSubscription(@Param("user") User user);

        /**
         * Check if user has an active RECRUITER_PRO subscription
         * Also checks for PREMIUM_PLUS and PREMIUM_BASIC which can be used for job posting
         */
        @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM UserSubscription s " +
                "JOIN s.plan p " +
                "WHERE s.user.id = :userId AND s.isActive = true AND s.status = 'ACTIVE' " +
                "AND s.startDate <= CURRENT_TIMESTAMP AND s.endDate > CURRENT_TIMESTAMP " +
                "AND p.planType IN ('RECRUITER_PRO', 'PREMIUM_PLUS', 'PREMIUM_BASIC')")
        Boolean hasActiveRecruiterSubscription(@Param("userId") Long userId);

        /**
         * Find user's active RECRUITER_PRO subscription specifically.
         * Also checks for PREMIUM_PLUS and PREMIUM_BASIC which can be used for job posting.
         * Unlike findCurrentActiveSubscription, this filters by specific planTypes
         * to avoid returning a student/free tier plan when user has multiple subscriptions.
         */
        @Query("""
                SELECT s FROM UserSubscription s
                JOIN FETCH s.plan p
                WHERE s.user = :user
                AND s.isActive = true
                AND s.status = 'ACTIVE'
                AND s.startDate <= CURRENT_TIMESTAMP
                AND s.endDate > CURRENT_TIMESTAMP
                AND p.planType IN ('RECRUITER_PRO', 'PREMIUM_PLUS', 'PREMIUM_BASIC')
                ORDER BY CASE p.planType
                    WHEN 'RECRUITER_PRO' THEN 1
                    WHEN 'PREMIUM_PLUS' THEN 2
                    WHEN 'PREMIUM_BASIC' THEN 3
                    ELSE 4
                END, p.price DESC
        """)
        Optional<UserSubscription> findActiveRecruiterSubscription(@Param("user") User user);

        /**
         * @deprecated Use {@link #findCurrentActiveSubscription(User)} instead for proper validation
         */
        @Deprecated
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
         * Find subscriptions expiring soon (with plan eagerly loaded to prevent N+1)
         */
        @Query("SELECT s FROM UserSubscription s JOIN FETCH s.plan WHERE s.isActive = true " +
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
         * Check if user has any active non-free subscription (exclude FREE_TIER)
         */
        @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM UserSubscription s " +
                        "JOIN s.plan p " +
                        "WHERE s.user = :user AND s.isActive = true AND s.status = 'ACTIVE' " +
                        "AND s.startDate <= :now AND s.endDate > :now AND p.planType <> 'FREE_TIER'")
        Boolean hasActiveNonFreeSubscription(@Param("user") User user, @Param("now") LocalDateTime now);

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
                        "AND s.isActive = true AND s.status = :status " +
                        "AND s.endDate BETWEEN :now AND :renewalWindow")
        List<UserSubscription> findSubscriptionsForAutoRenewal(
                        @Param("now") LocalDateTime now,
                        @Param("renewalWindow") LocalDateTime renewalWindow,
                        @Param("status") UserSubscription.SubscriptionStatus status);

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

        /**
         * [OPTIMIZED] Find all user IDs that don't have any active subscription.
         * Uses single query instead of N+1 pattern.
         * Used by scheduler to assign Free Tier in batch.
         */
        @Query("""
                SELECT u.id FROM User u
                WHERE NOT EXISTS (
                    SELECT 1 FROM UserSubscription us
                    WHERE us.user.id = u.id
                    AND us.isActive = true
                    AND us.status = 'ACTIVE'
                    AND us.startDate <= :now
                    AND us.endDate > :now
                )
        """)
        List<Long> findUserIdsWithoutActiveSubscription(@Param("now") LocalDateTime now);

        /**
         * [OPTIMIZED] Check if user has active subscription by user ID only (no entity load).
         */
        @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM UserSubscription s " +
                        "WHERE s.user.id = :userId AND s.isActive = true AND s.status = 'ACTIVE' " +
                        "AND s.startDate <= :now AND s.endDate > :now")
        Boolean hasActiveSubscriptionByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

        /**
         * [OPTIMIZED] Find existing FREE_TIER subscription for a user (active or inactive).
         * Used to reactivate instead of creating duplicates.
         */
        @Query("SELECT s FROM UserSubscription s JOIN s.plan p WHERE s.user.id = :userId " +
                        "AND p.planType = 'FREE_TIER' ORDER BY s.createdAt DESC")
        Optional<UserSubscription> findFreeTierSubscriptionByUserId(@Param("userId") Long userId);

        /**
         * Find SUSPENDED FREE_TIER subscription for a user.
         * Used when premium expires - reactivate suspended free tier.
         * Returns the most recently created suspended FREE_TIER to handle edge case of multiple suspensions.
         */
        @Query("SELECT s FROM UserSubscription s JOIN s.plan p WHERE s.user.id = :userId " +
                        "AND p.planType = 'FREE_TIER' AND s.status = 'SUSPENDED' " +
                        "ORDER BY s.createdAt DESC LIMIT 1")
        Optional<UserSubscription> findSuspendedFreeTierByUserId(@Param("userId") Long userId);

        /**
         * Find ALL suspended FREE_TIER subscriptions for cleanup purposes.
         */
        @Query("SELECT s FROM UserSubscription s JOIN s.plan p WHERE s.user.id = :userId " +
                        "AND p.planType = 'FREE_TIER' AND s.status = 'SUSPENDED' " +
                        "ORDER BY s.createdAt DESC")
        List<UserSubscription> findAllSuspendedFreeTierByUserId(@Param("userId") Long userId);

        /**
         * [OPTIMIZED] Batch reactivate SUSPENDED FREE_TIER subscriptions.
         * Returns number of reactivated subscriptions.
         */
        @Modifying
        @Query("""
                UPDATE UserSubscription s SET 
                    s.isActive = true, 
                    s.status = 'ACTIVE',
                    s.cancellationReason = null,
                    s.updatedAt = :now
                WHERE s.user.id IN :userIds 
                AND s.status = 'SUSPENDED'
                AND EXISTS (
                    SELECT 1 FROM PremiumPlan p 
                    WHERE p.id = s.plan.id AND p.planType = 'FREE_TIER'
                )
        """)
        int batchReactivateSuspendedFreeTier(
                @Param("userIds") List<Long> userIds,
                @Param("now") LocalDateTime now);

        /**
         * [OPTIMIZED] Find user IDs that have inactive FREE_TIER subscriptions.
         * Used to identify which users can be reactivated vs need new subscription.
         */
        @Query("""
                SELECT s.user.id FROM UserSubscription s 
                JOIN s.plan p 
                WHERE s.user.id IN :userIds 
                AND p.planType = 'FREE_TIER'
        """)
        List<Long> findUserIdsWithExistingFreeTier(@Param("userIds") List<Long> userIds);

        /**
         * Find PENDING RECRUITER_PRO subscriptions for a user.
         * Used by auto-recovery to activate subscriptions that were paid but never activated.
         */
        @Query("""
                SELECT s FROM UserSubscription s
                JOIN FETCH s.plan p
                WHERE s.user.id = :userId
                AND s.status = 'PENDING'
                AND p.planType = 'RECRUITER_PRO'
                ORDER BY s.createdAt DESC
        """)
        List<UserSubscription> findPendingRecruiterSubscriptions(@Param("userId") Long userId);
}