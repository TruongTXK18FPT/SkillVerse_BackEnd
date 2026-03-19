package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.ShortTermJob;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermJobStatus;
import java.math.BigDecimal;
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
public interface ShortTermJobRepository extends JpaRepository<ShortTermJob, Long> {

    // Find by recruiter
    List<ShortTermJob> findByRecruiterProfileUserIdOrderByCreatedAtDesc(Long recruiterId);

    Page<ShortTermJob> findByRecruiterProfileUserId(Long recruiterId, Pageable pageable);

    // Find by status
    List<ShortTermJob> findByStatus(ShortTermJobStatus status);

    Page<ShortTermJob> findByStatus(ShortTermJobStatus status, Pageable pageable);

    // Find published jobs (for candidates) — include APPLIED status so jobs with applicants remain visible
    @Query("SELECT j FROM ShortTermJob j WHERE (j.status = 'PUBLISHED' OR j.status = 'APPLIED') AND j.deadline > :now ORDER BY j.createdAt DESC")
    List<ShortTermJob> findPublishedJobs(@Param("now") LocalDateTime now);

    @Query("SELECT j FROM ShortTermJob j WHERE (j.status = 'PUBLISHED' OR j.status = 'APPLIED') AND j.deadline > :now")
    Page<ShortTermJob> findPublishedJobs(@Param("now") LocalDateTime now, Pageable pageable);

    // Search with filters — include APPLIED status so jobs with applicants remain visible
    @Query("SELECT j FROM ShortTermJob j WHERE (j.status = 'PUBLISHED' OR j.status = 'APPLIED') AND j.deadline > :now " +
            "AND (:search IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(j.description) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:minBudget IS NULL OR j.budget >= :minBudget) " +
            "AND (:maxBudget IS NULL OR j.budget <= :maxBudget) " +
            "AND (:isRemote IS NULL OR j.isRemote = :isRemote) " +
            "AND (:urgency IS NULL OR j.urgency = :urgency)")
    Page<ShortTermJob> searchJobs(
            @Param("now") LocalDateTime now,
            @Param("search") String search,
            @Param("minBudget") BigDecimal minBudget,
            @Param("maxBudget") BigDecimal maxBudget,
            @Param("isRemote") Boolean isRemote,
            @Param("urgency") String urgency,
            Pageable pageable
    );

    // Count by recruiter
    long countByRecruiterProfileUserId(Long recruiterId);

    long countByRecruiterProfileUserIdAndStatus(Long recruiterId, ShortTermJobStatus status);

    // Find with milestones
    @Query("SELECT DISTINCT j FROM ShortTermJob j LEFT JOIN FETCH j.milestones WHERE j.id = :id")
    Optional<ShortTermJob> findByIdWithMilestones(@Param("id") Long id);

    // Find expired jobs
    @Query("SELECT j FROM ShortTermJob j WHERE j.status = 'PUBLISHED' AND j.deadline < :now")
    List<ShortTermJob> findExpiredJobs(@Param("now") LocalDateTime now);

    // Statistics
    @Query("SELECT COUNT(j) FROM ShortTermJob j WHERE j.recruiterProfile.userId = :recruiterId AND j.status = 'COMPLETED'")
    long countCompletedJobsByRecruiter(@Param("recruiterId") Long recruiterId);

    @Query("SELECT COUNT(j) FROM ShortTermJob j WHERE j.recruiterProfile.userId = :recruiterId AND j.status = 'PAID'")
    long countPaidJobsByRecruiter(@Param("recruiterId") Long recruiterId);

    /**
     * Find PENDING_APPROVAL jobs created before cutoff date (for auto-cancel scheduler)
     */
    @Query("SELECT j FROM ShortTermJob j WHERE j.status = :status AND j.createdAt < :cutoffDate")
    List<ShortTermJob> findByStatusAndCreatedAtBefore(@Param("status") ShortTermJobStatus status, @Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find PUBLISHED or APPLIED jobs with deadline passed (for auto-close scheduler)
     */
    @Query("SELECT j FROM ShortTermJob j WHERE (j.status = 'PUBLISHED' OR j.status = 'APPLIED') AND j.deadline < :now")
    List<ShortTermJob> findPublishedJobsWithDeadlinePassed(@Param("now") LocalDateTime now);

    /**
     * Find IN_PROGRESS jobs with deadline passed (for auto-complete or auto-fail)
     */
    @Query("SELECT j FROM ShortTermJob j WHERE j.status = 'IN_PROGRESS' AND j.deadline < :now")
    List<ShortTermJob> findInProgressJobsWithDeadlinePassed(@Param("now") LocalDateTime now);
}
