package com.exe.skillverse_backend.wallet_service.repository;

import com.exe.skillverse_backend.wallet_service.entity.Wallet;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for WalletTransaction entity
 */
@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    /**
     * Find all transactions for a wallet entity
     */
    Page<WalletTransaction> findByWalletOrderByCreatedAtDesc(Wallet wallet, Pageable pageable);
    
    /**
     * Find all transactions for a wallet by ID
     */
    Page<WalletTransaction> findByWallet_WalletIdOrderByCreatedAtDesc(Long walletId, Pageable pageable);
    
    /**
     * Find transactions by wallet and type
     */
    Page<WalletTransaction> findByWallet_WalletIdAndTransactionTypeOrderByCreatedAtDesc(
        Long walletId, 
        WalletTransaction.TransactionType transactionType, 
        Pageable pageable
    );
    
    /**
     * Find transactions by wallet and currency type
     */
    Page<WalletTransaction> findByWallet_WalletIdAndCurrencyTypeOrderByCreatedAtDesc(
        Long walletId,
        WalletTransaction.CurrencyType currencyType,
        Pageable pageable
    );
    
    /**
     * Find transactions by reference
     */
    List<WalletTransaction> findByReferenceTypeAndReferenceId(String referenceType, String referenceId);

    /**
     * Find transaction by reference and reference type (idempotency check)
     */
    Optional<WalletTransaction> findByReferenceIdAndReferenceType(String referenceId, String referenceType);

    /**
     * Check if payment reference already processed (idempotency check)
     */
    boolean existsByReferenceIdAndReferenceTypeAndStatus(
            String referenceId,
            String referenceType,
            WalletTransaction.TransactionStatus status
    );
    
    /**
     * Find transactions in date range
     */
    @Query("SELECT t FROM WalletTransaction t WHERE t.wallet.walletId = :walletId " +
           "AND t.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY t.createdAt DESC")
    List<WalletTransaction> findTransactionsByDateRange(
        @Param("walletId") Long walletId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    /**
     * Calculate total cash deposited for a wallet
     */
    @Query("SELECT COALESCE(SUM(t.cashAmount), 0) FROM WalletTransaction t " +
           "WHERE t.wallet.walletId = :walletId " +
           "AND t.transactionType = 'DEPOSIT_CASH' " +
           "AND t.status = 'COMPLETED'")
    java.math.BigDecimal calculateTotalDeposited(@Param("walletId") Long walletId);
    
    /**
     * Calculate total cash withdrawn for a wallet
     */
    @Query("SELECT COALESCE(SUM(t.cashAmount), 0) FROM WalletTransaction t " +
           "WHERE t.wallet.walletId = :walletId " +
           "AND t.transactionType = 'WITHDRAWAL_CASH' " +
           "AND t.status = 'COMPLETED'")
    java.math.BigDecimal calculateTotalWithdrawn(@Param("walletId") Long walletId);
    
    /**
     * Calculate total coins earned for a wallet
     */
    @Query("SELECT COALESCE(SUM(t.coinAmount), 0) FROM WalletTransaction t " +
           "WHERE t.wallet.walletId = :walletId " +
           "AND t.transactionType IN ('EARN_COINS', 'BONUS_COINS', 'REWARD_ACHIEVEMENT', 'DAILY_LOGIN_BONUS') " +
           "AND t.status = 'COMPLETED'")
    Long calculateTotalCoinsEarned(@Param("walletId") Long walletId);
    
    /**
     * Calculate total coins spent for a wallet
     */
    @Query("SELECT COALESCE(SUM(t.coinAmount), 0) FROM WalletTransaction t " +
           "WHERE t.wallet.walletId = :walletId " +
           "AND t.transactionType IN ('SPEND_COINS', 'PURCHASE_COURSE', 'TIP_MENTOR') " +
           "AND t.status = 'COMPLETED'")
    Long calculateTotalCoinsSpent(@Param("walletId") Long walletId);
    
    /**
     * Calculate total revenue from purchases (Premium, Course, Coins) across ALL wallets
     * This is for admin dashboard statistics
     */
    @Query("SELECT COALESCE(SUM(t.cashAmount), 0) FROM WalletTransaction t " +
           "WHERE t.transactionType IN ('PURCHASE_PREMIUM', 'PURCHASE_COURSE', 'PURCHASE_COINS') " +
           "AND t.status = 'COMPLETED'")
    java.math.BigDecimal calculateTotalPurchaseRevenue();
    
    /**
     * Calculate total revenue from purchases within a date range
     */
    @Query("SELECT COALESCE(SUM(t.cashAmount), 0) FROM WalletTransaction t " +
           "WHERE t.transactionType IN ('PURCHASE_PREMIUM', 'PURCHASE_COURSE', 'PURCHASE_COINS') " +
           "AND t.status = 'COMPLETED' " +
           "AND t.createdAt BETWEEN :startDate AND :endDate")
    java.math.BigDecimal calculateTotalPurchaseRevenueInRange(
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate
    );
    
    /**
     * Count total purchase transactions
     */
    @Query("SELECT COUNT(t) FROM WalletTransaction t " +
           "WHERE t.transactionType IN ('PURCHASE_PREMIUM', 'PURCHASE_COURSE', 'PURCHASE_COINS') " +
           "AND t.status = 'COMPLETED'")
    Long countTotalPurchaseTransactions();
    
    /**
     * Get daily purchase revenue for charts (grouped by date)
     */
    @Query("SELECT CAST(t.createdAt AS LocalDate), COALESCE(SUM(t.cashAmount), 0), COUNT(t) " +
           "FROM WalletTransaction t " +
           "WHERE t.transactionType IN ('PURCHASE_PREMIUM', 'PURCHASE_COURSE', 'PURCHASE_COINS') " +
           "AND t.status = 'COMPLETED' AND t.createdAt >= :fromDate " +
           "GROUP BY CAST(t.createdAt AS LocalDate) " +
           "ORDER BY CAST(t.createdAt AS LocalDate)")
    List<Object[]> getDailyPurchaseRevenue(@Param("fromDate") LocalDateTime fromDate);
    
    /**
     * Get monthly purchase revenue for charts (grouped by year-month)
     */
    @Query("SELECT YEAR(t.createdAt), MONTH(t.createdAt), COALESCE(SUM(t.cashAmount), 0), COUNT(t) " +
           "FROM WalletTransaction t " +
           "WHERE t.transactionType IN ('PURCHASE_PREMIUM', 'PURCHASE_COURSE', 'PURCHASE_COINS') " +
           "AND t.status = 'COMPLETED' AND t.createdAt >= :fromDate " +
           "GROUP BY YEAR(t.createdAt), MONTH(t.createdAt) " +
           "ORDER BY YEAR(t.createdAt), MONTH(t.createdAt)")
    List<Object[]> getMonthlyPurchaseRevenue(@Param("fromDate") LocalDateTime fromDate);
    
    /**
     * Get yearly purchase revenue for charts
     */
    @Query("SELECT YEAR(t.createdAt), COALESCE(SUM(t.cashAmount), 0), COUNT(t) " +
           "FROM WalletTransaction t " +
           "WHERE t.transactionType IN ('PURCHASE_PREMIUM', 'PURCHASE_COURSE', 'PURCHASE_COINS') " +
           "AND t.status = 'COMPLETED' " +
           "GROUP BY YEAR(t.createdAt) " +
           "ORDER BY YEAR(t.createdAt)")
    List<Object[]> getYearlyPurchaseRevenue();
    
    /**
     * Admin: Get all transactions ordered by date
     */
    Page<WalletTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    /**
     * Admin: Get transactions by type ordered by date
     */
    Page<WalletTransaction> findByTransactionTypeOrderByCreatedAtDesc(
        WalletTransaction.TransactionType transactionType, 
        Pageable pageable
    );
}
