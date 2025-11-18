package com.exe.skillverse_backend.wallet_service.repository;

import com.exe.skillverse_backend.wallet_service.entity.WithdrawalRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for WithdrawalRequest entity
 */
@Repository
public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, Long> {
    
    /**
     * Find withdrawal request by request code
     */
    Optional<WithdrawalRequest> findByRequestCode(String requestCode);
    
    /**
     * Find all withdrawal requests for a user
     */
    @EntityGraph(attributePaths = {"user", "approvedBy"})
    Page<WithdrawalRequest> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    /**
     * Find withdrawal requests by status
     */
    @EntityGraph(attributePaths = {"user", "approvedBy"})
    Page<WithdrawalRequest> findByStatusOrderByCreatedAtDesc(
        WithdrawalRequest.WithdrawalStatus status,
        Pageable pageable
    );
    
    /**
     * Find withdrawal requests by user and status
     */
    Page<WithdrawalRequest> findByUser_IdAndStatusOrderByCreatedAtDesc(
        Long userId,
        WithdrawalRequest.WithdrawalStatus status,
        Pageable pageable
    );
    
    /**
     * Find pending requests for a user
     */
    @Query("SELECT w FROM WithdrawalRequest w WHERE w.user.id = :userId " +
           "AND w.status IN ('PENDING', 'APPROVED', 'PROCESSING')")
    List<WithdrawalRequest> findPendingRequestsByUserId(@Param("userId") Long userId);
    
    /**
     * Find all pending requests (for admin)
     */
    @Query("SELECT w FROM WithdrawalRequest w WHERE w.status = 'PENDING' " +
           "ORDER BY w.priority ASC, w.createdAt ASC")
    Page<WithdrawalRequest> findAllPendingRequests(Pageable pageable);
    
    /**
     * Find requests approved by admin
     */
    Page<WithdrawalRequest> findByApprovedBy_IdOrderByApprovedAtDesc(Long adminId, Pageable pageable);
    
    /**
     * Find expired requests that need to be auto-cancelled
     */
    @Query("SELECT w FROM WithdrawalRequest w WHERE w.status = 'PENDING' " +
           "AND w.expiresAt IS NOT NULL " +
           "AND w.expiresAt < :now")
    List<WithdrawalRequest> findExpiredRequests(@Param("now") LocalDateTime now);
    
    /**
     * Count pending requests by user
     */
    long countByUser_IdAndStatusIn(Long userId, List<WithdrawalRequest.WithdrawalStatus> statuses);
    
    /**
     * Calculate total withdrawal amount by user in time range
     */
    @Query("SELECT COALESCE(SUM(w.amount), 0) FROM WithdrawalRequest w " +
           "WHERE w.user.id = :userId " +
           "AND w.status = 'COMPLETED' " +
           "AND w.completedAt BETWEEN :startDate AND :endDate")
    java.math.BigDecimal calculateTotalWithdrawnInRange(
        @Param("userId") Long userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
}
