package com.exe.skillverse_backend.premium_service.repository;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.premium_service.entity.FeatureType;
import com.exe.skillverse_backend.premium_service.entity.UserUsageTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing user usage tracking
 */
@Repository
public interface UserUsageTrackingRepository extends JpaRepository<UserUsageTracking, Long> {

    /**
     * Find usage tracking for a specific user and feature
     */
    Optional<UserUsageTracking> findByUserAndFeatureType(User user, FeatureType featureType);

    /**
     * Find all usage tracking records for a user
     */
    List<UserUsageTracking> findByUser(User user);

    /**
     * Find usage tracking by user ID and feature type
     */
    @Query("SELECT uut FROM UserUsageTracking uut WHERE uut.user.id = :userId AND uut.featureType = :featureType")
    Optional<UserUsageTracking> findByUserIdAndFeatureType(@Param("userId") Long userId,
            @Param("featureType") FeatureType featureType);

    /**
     * Find all usage tracking for a user by user ID
     */
    @Query("SELECT uut FROM UserUsageTracking uut WHERE uut.user.id = :userId")
    List<UserUsageTracking> findByUserId(@Param("userId") Long userId);

    /**
     * Find all expired usage periods that need reset
     */
    @Query("SELECT uut FROM UserUsageTracking uut WHERE uut.currentPeriodEnd < :now")
    List<UserUsageTracking> findExpiredPeriods(@Param("now") LocalDateTime now);

    /**
     * Find all usage records for a specific feature type
     */
    List<UserUsageTracking> findByFeatureType(FeatureType featureType);

    /**
     * Check if user has any usage for a feature
     */
    boolean existsByUserAndFeatureType(User user, FeatureType featureType);

    /**
     * Delete all usage tracking for a user
     */
    void deleteByUser(User user);

    /**
     * Find users who have exceeded a specific limit
     */
    @Query("SELECT uut FROM UserUsageTracking uut WHERE uut.featureType = :featureType " +
            "AND uut.usageCount >= :limit")
    List<UserUsageTracking> findUsersExceedingLimit(@Param("featureType") FeatureType featureType,
            @Param("limit") Integer limit);

    /**
     * Get total usage count for a feature across all users
     */
    @Query("SELECT SUM(uut.usageCount) FROM UserUsageTracking uut WHERE uut.featureType = :featureType")
    Long getTotalUsageForFeature(@Param("featureType") FeatureType featureType);

    /**
     * Get average usage for a feature
     */
    @Query("SELECT AVG(uut.usageCount) FROM UserUsageTracking uut WHERE uut.featureType = :featureType")
    Double getAverageUsageForFeature(@Param("featureType") FeatureType featureType);

    /**
     * Find top users by usage for a specific feature
     */
    @Query("SELECT uut FROM UserUsageTracking uut WHERE uut.featureType = :featureType " +
            "ORDER BY uut.usageCount DESC")
    List<UserUsageTracking> findTopUsersByUsage(@Param("featureType") FeatureType featureType);

    /**
     * Reset all expired periods (bulk operation)
     */
    @Modifying
    @Query("UPDATE UserUsageTracking uut SET uut.usageCount = 0, uut.lastResetAt = :now " +
            "WHERE uut.currentPeriodEnd < :now")
    int resetExpiredPeriods(@Param("now") LocalDateTime now);

    /**
     * Atomically increment usage count to prevent race conditions.
     * Returns 1 if successful, 0 if tracking record not found.
     * This prevents concurrent requests from bypassing rate limits.
     */
    @Modifying
    @Query("UPDATE UserUsageTracking uut SET uut.usageCount = uut.usageCount + 1 " +
            "WHERE uut.id = :trackingId")
    int atomicIncrementUsage(@Param("trackingId") Long trackingId);

    /**
     * Atomically check and increment usage if under limit.
     * Returns 1 if incremented (under limit), 0 if limit reached or record not found.
     * CRITICAL: Prevents race condition in check-then-act pattern.
     */
    @Modifying
    @Query("UPDATE UserUsageTracking uut SET uut.usageCount = uut.usageCount + 1 " +
            "WHERE uut.id = :trackingId AND uut.usageCount < :limit")
    int atomicIncrementIfUnderLimit(@Param("trackingId") Long trackingId, @Param("limit") Integer limit);

    /**
     * Atomically reset expired period and increment usage in one operation.
     * Returns 1 if successful, 0 if period not expired or record not found.
     * This combines reset + increment to prevent race conditions.
     */
    @Modifying
    @Query("UPDATE UserUsageTracking uut SET " +
            "uut.usageCount = 1, " +
            "uut.lastResetAt = :now, " +
            "uut.currentPeriodStart = :periodStart, " +
            "uut.currentPeriodEnd = :periodEnd " +
            "WHERE uut.id = :trackingId AND uut.currentPeriodEnd < :now")
    int atomicResetAndIncrement(
            @Param("trackingId") Long trackingId,
            @Param("now") LocalDateTime now,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd);

    /**
     * Find usage records that will expire soon (within hours)
     */
    @Query("SELECT uut FROM UserUsageTracking uut WHERE uut.currentPeriodEnd BETWEEN :now AND :threshold")
    List<UserUsageTracking> findExpiringWithin(@Param("now") LocalDateTime now,
            @Param("threshold") LocalDateTime threshold);

    /**
     * Get usage statistics for a date range
     */
    @Query("SELECT uut.featureType, COUNT(uut), SUM(uut.usageCount), AVG(uut.usageCount) " +
            "FROM UserUsageTracking uut " +
            "WHERE uut.currentPeriodStart >= :startDate AND uut.currentPeriodEnd <= :endDate " +
            "GROUP BY uut.featureType")
    List<Object[]> getUsageStatistics(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}
