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
     * (DEPRECATED - USE WITH JOIN FETCH)
     */
    List<JobApplication> findByUserIdOrderByAppliedAtDesc(Long userId);

    /**
     * Find all applications by user ID with job posting and recruiter eagerly
     * loaded (N+1 FIX)
     * Performance: 41 queries → 1 query for 20 applications
     */
    @Query("SELECT DISTINCT ja FROM JobApplication ja " +
            "JOIN FETCH ja.jobPosting jp " +
            "JOIN FETCH jp.recruiterProfile rp " +
            "JOIN FETCH rp.user u " +
            "WHERE ja.user.id = :userId " +
            "ORDER BY ja.appliedAt DESC")
    List<JobApplication> findByUserIdWithJobAndRecruiterOrderByAppliedAtDesc(@Param("userId") Long userId);

    /**
     * Find all applications for a specific job ordered by applied date descending
     * (DEPRECATED - USE WITH JOIN FETCH)
     */
    List<JobApplication> findByJobPostingIdOrderByAppliedAtDesc(Long jobId);

    /**
     * Find all applications for a specific job with user eagerly loaded (N+1 FIX)
     * Performance: Prevents lazy loading when loading applicants list
     */
    @Query("SELECT DISTINCT ja FROM JobApplication ja " +
            "JOIN FETCH ja.user u " +
            "WHERE ja.jobPosting.id = :jobId " +
            "ORDER BY ja.appliedAt DESC")
    List<JobApplication> findByJobPostingIdWithUserOrderByAppliedAtDesc(@Param("jobId") Long jobId);

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
