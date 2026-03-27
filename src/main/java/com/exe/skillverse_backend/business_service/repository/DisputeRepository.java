package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.Dispute;
import com.exe.skillverse_backend.business_service.entity.Dispute.DisputeStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {
    Optional<Dispute> findByJobId(Long jobId);

    boolean existsByJobId(Long jobId);

    List<Dispute> findByInitiatorId(Long initiatorId);

    List<Dispute> findByRespondentId(Long respondentId);

    Page<Dispute> findByInitiatorIdOrRespondentId(Long initiatorId, Long respondentId, Pageable pageable);

    List<Dispute> findByStatus(DisputeStatus status);

    Page<Dispute> findByStatusIn(List<DisputeStatus> statuses, Pageable pageable);

    @Query("SELECT d FROM Dispute d WHERE d.status IN :statuses")
    List<Dispute> findActiveDisputes(@Param("statuses") List<DisputeStatus> statuses);

    long countByStatus(DisputeStatus status);

    @Query("SELECT d FROM Dispute d WHERE d.status IN :statuses")
    Page<Dispute> findByStatusInPaginated(@Param("statuses") List<DisputeStatus> statuses, Pageable pageable);

    // ==================== SLA / ESCALATION QUERIES ====================

    // Find disputes where admin exceeded 5-day resolution SLA
    @Query("SELECT d FROM Dispute d WHERE d.status IN ('OPEN','UNDER_INVESTIGATION','AWAITING_RESPONSE') AND d.adminResolutionDeadlineAt < :now AND d.status != 'ESCALATED'")
    List<Dispute> findOverdueDisputes(@Param("now") java.time.LocalDateTime now);
}
