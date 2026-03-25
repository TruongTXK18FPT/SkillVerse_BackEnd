package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.JobEscrow;
import com.exe.skillverse_backend.business_service.entity.JobEscrow.EscrowStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JobEscrowRepository extends JpaRepository<JobEscrow, Long> {
    Optional<JobEscrow> findByJobId(Long jobId);
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM JobEscrow e WHERE e.job.id = :jobId")
    boolean existsByJobId(@Param("jobId") Long jobId);
    List<JobEscrow> findByRecruiterId(Long recruiterId);
    List<JobEscrow> findByStatus(EscrowStatus status);
    List<JobEscrow> findByStatusIn(List<EscrowStatus> statuses);

    /**
     * Get total platform fee earned from all fully released escrows
     */
    @Query("SELECT COALESCE(SUM(e.platformFee), 0) FROM JobEscrow e WHERE e.status = 'FULLY_RELEASED'")
    java.math.BigDecimal getTotalPlatformFee();

    /**
     * Get total escrow amount from all funded/released escrows
     */
    @Query("SELECT COALESCE(SUM(e.totalAmount), 0) FROM JobEscrow e WHERE e.status IN ('FUNDED', 'PARTIALLY_RELEASED', 'FULLY_RELEASED')")
    java.math.BigDecimal getTotalEscrowVolume();

    /**
     * Count escrows by status
     */
    long countByStatus(EscrowStatus status);

    /**
     * Get total platform fee from escrows released within a date range
     */
    @Query("SELECT COALESCE(SUM(e.platformFee), 0) FROM JobEscrow e " +
           "WHERE e.status = 'FULLY_RELEASED' " +
           "AND e.releasedAt >= :startDate AND e.releasedAt <= :endDate")
    java.math.BigDecimal getPlatformFeeInRange(
        @Param("startDate") java.time.LocalDateTime startDate,
        @Param("endDate") java.time.LocalDateTime endDate
    );

    /**
     * Get total escrow volume in a date range
     */
    @Query("SELECT COALESCE(SUM(e.totalAmount), 0) FROM JobEscrow e " +
           "WHERE e.status IN ('FUNDED', 'PARTIALLY_RELEASED', 'FULLY_RELEASED') " +
           "AND e.fundedAt >= :startDate AND e.fundedAt <= :endDate")
    java.math.BigDecimal getEscrowVolumeInRange(
        @Param("startDate") java.time.LocalDateTime startDate,
        @Param("endDate") java.time.LocalDateTime endDate
    );
}
