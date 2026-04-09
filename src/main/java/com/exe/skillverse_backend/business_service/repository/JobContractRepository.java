package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.JobContract;
import com.exe.skillverse_backend.business_service.enums.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
