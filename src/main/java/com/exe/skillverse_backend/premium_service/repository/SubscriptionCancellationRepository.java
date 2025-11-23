package com.exe.skillverse_backend.premium_service.repository;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.premium_service.entity.SubscriptionCancellation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for SubscriptionCancellation entity
 */
@Repository
public interface SubscriptionCancellationRepository extends JpaRepository<SubscriptionCancellation, Long> {

    /**
     * Count cancellations for a user in a specific month
     */
    Long countByUserAndCancellationMonth(User user, String cancellationMonth);

    /**
     * Get all cancellations for a user in a specific month
     */
    List<SubscriptionCancellation> findByUserAndCancellationMonth(User user, String cancellationMonth);

    /**
     * Get all cancellations for a user
     */
    List<SubscriptionCancellation> findByUserOrderByCreatedAtDesc(User user);

    /**
     * Count total refund cancellations for a user
     */
    @Query("SELECT COUNT(c) FROM SubscriptionCancellation c WHERE c.user = :user " +
           "AND c.cancellationType = 'CANCEL_WITH_REFUND'")
    Long countRefundCancellationsByUser(@Param("user") User user);

    /**
     * Get cancellation statistics by month
     */
    @Query("SELECT c.cancellationMonth, COUNT(c), SUM(c.refundAmount) " +
           "FROM SubscriptionCancellation c " +
           "GROUP BY c.cancellationMonth " +
           "ORDER BY c.cancellationMonth DESC")
    List<Object[]> getCancellationStatsByMonth();
}
