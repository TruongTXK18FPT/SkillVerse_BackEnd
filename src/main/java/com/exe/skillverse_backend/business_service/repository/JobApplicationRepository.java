package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    /**
     * Find all applications by user ID ordered by applied date descending
     */
    List<JobApplication> findByUserIdOrderByAppliedAtDesc(Long userId);

    /**
     * Find all applications for a specific job ordered by applied date descending
     */
    List<JobApplication> findByJobPostingIdOrderByAppliedAtDesc(Long jobId);

    /**
     * Find application by job ID and user ID (for duplicate check)
     */
    Optional<JobApplication> findByJobPostingIdAndUserId(Long jobId, Long userId);

    /**
     * Check if application exists for job and user
     */
    boolean existsByJobPostingIdAndUserId(Long jobId, Long userId);

    /**
     * Delete all applications for a specific job (for reopen functionality)
     */
    @Modifying
    @Query("DELETE FROM JobApplication ja WHERE ja.jobPosting.id = :jobId")
    void deleteByJobPostingId(@Param("jobId") Long jobId);
}
