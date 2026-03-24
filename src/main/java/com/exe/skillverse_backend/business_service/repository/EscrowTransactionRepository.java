package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.EscrowTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EscrowTransactionRepository extends JpaRepository<EscrowTransaction, Long> {
    List<EscrowTransaction> findByEscrowIdOrderByCreatedAtDesc(Long escrowId);

    @Query("SELECT et FROM EscrowTransaction et WHERE et.escrow.id = :escrowId ORDER BY et.createdAt DESC")
    Page<EscrowTransaction> findByEscrowIdPaged(@Param("escrowId") Long escrowId, Pageable pageable);
}
