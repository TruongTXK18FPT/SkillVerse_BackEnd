package com.exe.skillverse_backend.wallet_service.repository;

import com.exe.skillverse_backend.wallet_service.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Wallet entity
 */
@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {
    
    /**
     * Find wallet by user ID
     */
    Optional<Wallet> findByUser_Id(Long userId);
    
    /**
     * Find wallet by user ID with pessimistic lock
     * Use this for update operations to prevent race conditions
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.user.id = :userId")
    Optional<Wallet> findByUserIdWithLock(@Param("userId") Long userId);
    
    /**
     * Check if wallet exists for user
     */
    boolean existsByUser_Id(Long userId);
    
    /**
     * Find all active wallets
     */
    @Query("SELECT w FROM Wallet w WHERE w.status = 'ACTIVE'")
    Iterable<Wallet> findAllActive();
    
    /**
     * Find wallets with balance greater than amount
     */
    @Query("SELECT w FROM Wallet w WHERE w.cashBalance >= :minBalance")
    Iterable<Wallet> findWalletsWithMinCashBalance(@Param("minBalance") java.math.BigDecimal minBalance);
}
