package com.exe.skillverse_backend.gamification_service.repository;

import com.exe.skillverse_backend.gamification_service.entity.GamificationUserWallet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

@Repository
public interface GamificationUserWalletRepository extends JpaRepository<GamificationUserWallet, Long> {
    
    Optional<GamificationUserWallet> findByUserId(Long userId);
    
    boolean existsByUserId(Long userId);
    
    @Query("SELECT COALESCE(SUM(w.totalCoins), 0) FROM GamificationUserWallet w WHERE w.userId = :userId")
    Integer getTotalCoinsByUserId(@Param("userId") Long userId);
    
    @Query("SELECT w FROM GamificationUserWallet w WHERE w.userId = :userId")
    Optional<GamificationUserWallet> findWalletWithUserDetails(@Param("userId") Long userId);

    // Admin dashboard queries
    @Query("SELECT COUNT(w) FROM GamificationUserWallet w WHERE w.lastActivityDate > :date")
    Long countByLastActivityDateAfter(@Param("date") LocalDateTime date);

    @Query("SELECT COUNT(w) FROM GamificationUserWallet w WHERE w.lastActivityDate BETWEEN :start AND :end")
    Long countByLastActivityDateBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT w FROM GamificationUserWallet w ORDER BY w.totalCoins DESC")
    List<GamificationUserWallet> findTopByCoinBalanceOrderByDesc(int limit);

    @Query("SELECT w FROM GamificationUserWallet w ORDER BY w.totalXp DESC")
    List<GamificationUserWallet> findTopByTotalXpOrderByDesc(int limit);

    @Query(value = "SELECT w FROM GamificationUserWallet w ORDER BY w.totalCoins DESC")
    List<GamificationUserWallet> findTopCoinEarners(Pageable pageable);

    @Query(value = "SELECT w FROM GamificationUserWallet w ORDER BY w.totalXp DESC")
    List<GamificationUserWallet> findTopXpEarners(Pageable pageable);
}
