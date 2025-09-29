package com.exe.skillverse_backend.payment_service.repository;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for PaymentTransaction entity
 */
@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    /**
     * Find transaction by internal reference
     */
    Optional<PaymentTransaction> findByInternalReference(String internalReference);

    /**
     * Find transaction by external reference ID
     */
    Optional<PaymentTransaction> findByReferenceId(String referenceId);

    /**
     * Find all transactions for a user
     */
    Page<PaymentTransaction> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    /**
     * Find transactions by user and status
     */
    List<PaymentTransaction> findByUserAndStatus(User user, PaymentTransaction.PaymentStatus status);

    /**
     * Find transactions by user and type
     */
    List<PaymentTransaction> findByUserAndType(User user, PaymentTransaction.PaymentType type);

    /**
     * Find transactions by status
     */
    List<PaymentTransaction> findByStatus(PaymentTransaction.PaymentStatus status);

    /**
     * Find transactions by payment method
     */
    List<PaymentTransaction> findByPaymentMethod(PaymentTransaction.PaymentMethod paymentMethod);

    /**
     * Find pending transactions older than specified time
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.status = 'PENDING' AND pt.createdAt < :cutoffTime")
    List<PaymentTransaction> findPendingTransactionsOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Get total payment amount for user by type
     */
    @Query("SELECT COALESCE(SUM(pt.amount), 0) FROM PaymentTransaction pt " +
            "WHERE pt.user = :user AND pt.type = :type AND pt.status = 'COMPLETED'")
    BigDecimal getTotalAmountByUserAndType(@Param("user") User user,
            @Param("type") PaymentTransaction.PaymentType type);

    /**
     * Get transaction statistics by payment method
     */
    @Query("SELECT pt.paymentMethod, COUNT(pt), SUM(pt.amount) FROM PaymentTransaction pt " +
            "WHERE pt.status = 'COMPLETED' AND pt.createdAt >= :fromDate " +
            "GROUP BY pt.paymentMethod")
    List<Object[]> getPaymentMethodStats(@Param("fromDate") LocalDateTime fromDate);

    /**
     * Find user's latest completed payment for a specific type
     */
    @Query("SELECT pt FROM PaymentTransaction pt " +
            "WHERE pt.user = :user AND pt.type = :type AND pt.status = 'COMPLETED' " +
            "ORDER BY pt.createdAt DESC")
    Page<PaymentTransaction> findLatestCompletedPaymentByUserAndType(
            @Param("user") User user,
            @Param("type") PaymentTransaction.PaymentType type,
            Pageable pageable);

    /**
     * Count failed transactions for a user in a time period
     */
    @Query("SELECT COUNT(pt) FROM PaymentTransaction pt " +
            "WHERE pt.user = :user AND pt.status = 'FAILED' " +
            "AND pt.createdAt >= :fromDate")
    Long countFailedTransactionsForUser(@Param("user") User user,
            @Param("fromDate") LocalDateTime fromDate);

    /**
     * Find transactions that need status updates from gateway
     */
    @Query("SELECT pt FROM PaymentTransaction pt " +
            "WHERE pt.status IN ('PENDING', 'PROCESSING') " +
            "AND pt.referenceId IS NOT NULL " +
            "ORDER BY pt.createdAt ASC")
    List<PaymentTransaction> findTransactionsNeedingStatusUpdate();
}