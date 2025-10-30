package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    /**
     * Find all jobs by recruiter ID ordered by created date descending
     */
    List<JobPosting> findByRecruiterProfileUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Find all jobs by status ordered by created date descending (DEPRECATED - USE
     * WITH JOIN FETCH)
     */
    List<JobPosting> findByStatusOrderByCreatedAtDesc(JobStatus status);

    /**
     * Find all jobs by status with recruiter profile and user eagerly loaded (N+1
     * FIX)
     * Performance: 201 queries → 1 query for 100 jobs
     */
    @Query("SELECT DISTINCT j FROM JobPosting j " +
            "JOIN FETCH j.recruiterProfile rp " +
            "JOIN FETCH rp.user u " +
            "WHERE j.status = :status " +
            "ORDER BY j.createdAt DESC")
    List<JobPosting> findByStatusWithRecruiterOrderByCreatedAtDesc(@Param("status") JobStatus status);

    /**
     * Find all jobs by recruiter ID with recruiter profile and user eagerly loaded
     * (N+1 FIX)
     * Performance: 91 queries → 1 query for 30 jobs
     */
    @Query("SELECT DISTINCT j FROM JobPosting j " +
            "JOIN FETCH j.recruiterProfile rp " +
            "JOIN FETCH rp.user u " +
            "WHERE rp.user.id = :userId " +
            "ORDER BY j.createdAt DESC")
    List<JobPosting> findByRecruiterUserIdWithRecruiterOrderByCreatedAtDesc(@Param("userId") Long userId);

    /**
     * Find job by ID and recruiter ID (for ownership validation)
     */
    Optional<JobPosting> findByIdAndRecruiterProfileUserId(Long id, Long userId);

    /**
     * Find job by ID with recruiter profile and user eagerly loaded (N+1 FIX)
     * Performance: Prevents lazy loading when accessing job details
     */
    @Query("SELECT j FROM JobPosting j " +
            "JOIN FETCH j.recruiterProfile rp " +
            "JOIN FETCH rp.user u " +
            "WHERE j.id = :id")
    Optional<JobPosting> findByIdWithRecruiter(@Param("id") Long id);

    /**
     * Find jobs by status and deadline before given date (for auto-close scheduler)
     */
    List<JobPosting> findByStatusAndDeadlineBefore(JobStatus status, LocalDate date);
}
