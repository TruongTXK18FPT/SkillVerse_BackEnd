package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.JobContract;
import com.exe.skillverse_backend.business_service.enums.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobContractRepository extends JpaRepository<JobContract, Long> {
    Optional<JobContract> findByApplicationId(Long applicationId);
    List<JobContract> findByEmployerIdOrderByCreatedAtDesc(Long employerId);
    List<JobContract> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);
    boolean existsByApplicationId(Long applicationId);
    long countByApplicationJobPostingIdAndStatus(Long jobPostingId, ContractStatus status);
    List<JobContract> findByApplicationJobPostingIdAndStatusIn(Long jobPostingId, Collection<ContractStatus> statuses);
    List<JobContract> findByStatusInAndUpdatedAtBefore(Collection<ContractStatus> statuses, LocalDateTime before);

    /**
     * Active-contract lookup for "1 signed contract per candidate" rule.
     * A contract is "active" when SIGNED and its endDate is still in the future (inclusive).
     */
    @Query("SELECT c FROM JobContract c WHERE c.candidateId = :userId "
            + "AND c.status = com.exe.skillverse_backend.business_service.enums.ContractStatus.SIGNED "
            + "AND (c.endDate IS NULL OR c.endDate >= :today) "
            + "ORDER BY c.signedAt DESC")
    List<JobContract> findActiveContractsForCandidate(@Param("userId") Long userId,
                                                      @Param("today") LocalDate today);
}
