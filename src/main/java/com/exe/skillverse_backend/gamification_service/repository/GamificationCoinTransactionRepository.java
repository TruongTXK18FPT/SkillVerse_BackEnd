package com.exe.skillverse_backend.gamification_service.repository;

import com.exe.skillverse_backend.gamification_service.entity.GamificationCoinTransaction;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GamificationCoinTransactionRepository extends JpaRepository<GamificationCoinTransaction, Long> {
    
    Page<GamificationCoinTransaction> findByUserIdOrderByTransactionDateDesc(Long userId, Pageable pageable);
    
    List<GamificationCoinTransaction> findByUserIdAndTransactionDateBetween(
        Long userId, LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("SELECT t FROM GamificationCoinTransaction t WHERE t.userId = :userId AND t.sourceType = :sourceType ORDER BY t.transactionDate DESC")
    Page<GamificationCoinTransaction> findByUserIdAndSourceType(
        @Param("userId") Long userId, 
        @Param("sourceType") String sourceType, 
        Pageable pageable);
    
    @Query("SELECT COALESCE(SUM(t.coinAmount), 0) FROM GamificationCoinTransaction t " +
           "WHERE t.userId = :userId AND t.transactionDate >= :startDate AND t.coinAmount > 0")
    Integer sumCoinsEarnedByUserSince(@Param("userId") Long userId, @Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT COALESCE(SUM(t.coinAmount), 0) FROM GamificationCoinTransaction t " +
           "WHERE t.userId = :userId AND t.sourceType = :sourceType AND t.transactionDate >= :startDate")
    Integer sumCoinsByUserAndSourceTypeSince(
        @Param("userId") Long userId, 
        @Param("sourceType") String sourceType, 
        @Param("startDate") LocalDateTime startDate);

    // Admin dashboard queries
    @Query("SELECT COALESCE(SUM(t.coinAmount), 0) FROM GamificationCoinTransaction t WHERE t.transactionType = :type AND t.coinAmount > 0")
    Long sumCoinsByTransactionType(@Param("type") String type);

    @Query("SELECT COALESCE(SUM(t.coinAmount), 0) FROM GamificationCoinTransaction t WHERE t.transactionType = :type AND t.transactionDate > :date AND t.coinAmount > 0")
    Long sumCoinsByTransactionTypeAndDateAfter(@Param("type") String type, @Param("date") LocalDateTime date);

    @Query("SELECT COALESCE(SUM(t.coinAmount), 0) FROM GamificationCoinTransaction t WHERE t.transactionType = :type AND t.transactionDate BETWEEN :start AND :end AND t.coinAmount > 0")
    Long sumCoinsByDateRange(@Param("type") String type, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
