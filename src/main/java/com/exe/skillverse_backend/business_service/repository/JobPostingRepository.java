package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    /**
     * Find PENDING_APPROVAL jobs that have been waiting for more than specified days (for auto-cancel scheduler)
     */
    @Query("SELECT j FROM JobPosting j WHERE j.status = :status AND j.createdAt < :cutoffDate")
    List<JobPosting> findByStatusAndCreatedAtBefore(@Param("status") JobStatus status, @Param("cutoffDate") java.time.LocalDateTime cutoffDate);

    /**
     * Find all OPEN jobs with recruiter eagerly loaded, ordered by boosted jobs first
     * Uses native query for better performance with ranking
     */
    @Query(value = """
        SELECT j.* FROM job_postings j
        LEFT JOIN job_boosts jb ON j.id = jb.job_posting_id
        AND jb.boost_status = 'ACTIVE'
        AND jb.expires_at > CURRENT_TIMESTAMP
        AND (jb.scheduled_start_at IS NULL OR jb.scheduled_start_at <= CURRENT_TIMESTAMP)
        WHERE j.status = 'OPEN'
        ORDER BY
            CASE WHEN jb.id IS NOT NULL THEN 0 ELSE 1 END,
            j.created_at DESC
        """, nativeQuery = true)
    List<JobPosting> findOpenJobsWithBoostRanking();

    /**
     * Find OPEN jobs with boost info for advanced ranking
     * Returns jobs with boost data for hybrid scoring
     */
    @Query("SELECT j FROM JobPosting j " +
            "JOIN FETCH j.recruiterProfile rp " +
            "JOIN FETCH rp.user u " +
            "LEFT JOIN JobBoost jb ON j.id = jb.jobPosting.id " +
            "AND jb.boostStatus = 'ACTIVE' " +
            "AND jb.expiresAt > CURRENT_TIMESTAMP " +
            "AND (jb.scheduledStartAt IS NULL OR jb.scheduledStartAt <= CURRENT_TIMESTAMP) " +
            "WHERE j.status = 'OPEN' " +
            "ORDER BY CASE WHEN jb.id IS NOT NULL THEN 0 ELSE 1 END, j.createdAt DESC")
    List<JobPosting> findOpenJobsWithBoostInfo();

    /**
     * Paginated: Find OPEN jobs with boost ranking, boosted jobs first
     */
    @Query(value = """
        SELECT j.* FROM job_postings j
        LEFT JOIN job_boosts jb ON j.id = jb.job_posting_id
        AND jb.boost_status = 'ACTIVE'
        AND jb.expires_at > CURRENT_TIMESTAMP
        AND (jb.scheduled_start_at IS NULL OR jb.scheduled_start_at <= CURRENT_TIMESTAMP)
        WHERE j.status = 'OPEN'
        ORDER BY
            CASE WHEN jb.id IS NOT NULL THEN 0 ELSE 1 END,
            j.created_at DESC
        """,
        countQuery = "SELECT COUNT(*) FROM job_postings WHERE status = 'OPEN'",
        nativeQuery = true)
    Page<JobPosting> findOpenJobsPaged(Pageable pageable);
}
