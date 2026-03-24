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
}
