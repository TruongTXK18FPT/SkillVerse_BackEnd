package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.ShortTermJobApplication;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermApplicationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ShortTermJobApplicationRepository extends JpaRepository<ShortTermJobApplication, Long> {

    // Find by job
    List<ShortTermJobApplication> findByShortTermJobId(Long jobId);

    Page<ShortTermJobApplication> findByShortTermJobId(Long jobId, Pageable pageable);

    // Find by user
    List<ShortTermJobApplication> findByUserIdOrderByAppliedAtDesc(Long userId);

    @Query("SELECT DISTINCT a FROM ShortTermJobApplication a LEFT JOIN FETCH a.revisionNotes WHERE a.user.id = :userId ORDER BY a.appliedAt DESC")
    List<ShortTermJobApplication> findByUserIdOrderByAppliedAtDescWithRevisionNotes(@Param("userId") Long userId);

    Page<ShortTermJobApplication> findByUserId(Long userId, Pageable pageable);

    @Query(value = "SELECT DISTINCT a FROM ShortTermJobApplication a LEFT JOIN FETCH a.revisionNotes WHERE a.user.id = :userId",
           countQuery = "SELECT COUNT(DISTINCT a) FROM ShortTermJobApplication a WHERE a.user.id = :userId")
    Page<ShortTermJobApplication> findByUserIdWithRevisionNotes(@Param("userId") Long userId, Pageable pageable);

    // Find by status
    List<ShortTermJobApplication> findByStatus(ShortTermApplicationStatus status);

    List<ShortTermJobApplication> findByShortTermJobIdAndStatus(Long jobId, ShortTermApplicationStatus status);

    // Check if user already applied
    boolean existsByShortTermJobIdAndUserId(Long jobId, Long userId);

    Optional<ShortTermJobApplication> findByShortTermJobIdAndUserId(Long jobId, Long userId);

    // Find accepted application for a job
    @Query("SELECT a FROM ShortTermJobApplication a WHERE a.shortTermJob.id = :jobId AND a.status = 'ACCEPTED'")
    Optional<ShortTermJobApplication> findAcceptedApplicationByJobId(@Param("jobId") Long jobId);

    // Find working application
    @Query("SELECT a FROM ShortTermJobApplication a WHERE a.shortTermJob.id = :jobId AND a.status = 'WORKING'")
    Optional<ShortTermJobApplication> findWorkingApplicationByJobId(@Param("jobId") Long jobId);

    // Find approved application (for job completion)
    @Query("SELECT a FROM ShortTermJobApplication a WHERE a.shortTermJob.id = :jobId AND a.status = 'APPROVED'")
    Optional<ShortTermJobApplication> findApprovedApplicationByJobId(@Param("jobId") Long jobId);

    // Find application by job ID and status (for auto-advance logic)
    @Query("SELECT a FROM ShortTermJobApplication a WHERE a.shortTermJob.id = :jobId AND a.status = :status")
    Optional<ShortTermJobApplication> findByJobIdAndStatus(@Param("jobId") Long jobId, @Param("status") ShortTermApplicationStatus status);

    // Count by job
    long countByShortTermJobId(Long jobId);

    long countByShortTermJobIdAndStatus(Long jobId, ShortTermApplicationStatus status);

    // Count by user
    long countByUserId(Long userId);

    @Query("SELECT COUNT(a) FROM ShortTermJobApplication a WHERE a.user.id = :userId AND a.status = 'COMPLETED'")
    long countCompletedByUser(@Param("userId") Long userId);

    // Find with deliverables
    @Query("SELECT DISTINCT a FROM ShortTermJobApplication a LEFT JOIN FETCH a.deliverables WHERE a.id = :id")
    Optional<ShortTermJobApplication> findByIdWithDeliverables(@Param("id") Long id);

    // Find with revision notes
    @Query("SELECT DISTINCT a FROM ShortTermJobApplication a LEFT JOIN FETCH a.revisionNotes WHERE a.id = :id")
    Optional<ShortTermJobApplication> findByIdWithRevisionNotes(@Param("id") Long id);

    // Find completed applications by user (COMPLETED or PAID status)
    @Query("SELECT a FROM ShortTermJobApplication a WHERE a.user.id = :userId AND (a.status = 'COMPLETED' OR a.status = 'PAID') ORDER BY a.completedAt DESC")
    List<ShortTermJobApplication> findCompletedByUserId(@Param("userId") Long userId);

    // Find completed applications with deliverables
    @Query("SELECT DISTINCT a FROM ShortTermJobApplication a LEFT JOIN FETCH a.deliverables WHERE a.user.id = :userId AND (a.status = 'COMPLETED' OR a.status = 'PAID') ORDER BY a.completedAt DESC")
    List<ShortTermJobApplication> findCompletedByUserIdWithDeliverables(@Param("userId") Long userId);

    // Find completed applications with job info
    @Query("SELECT DISTINCT a FROM ShortTermJobApplication a JOIN FETCH a.shortTermJob WHERE a.user.id = :userId AND (a.status = 'COMPLETED' OR a.status = 'PAID') ORDER BY a.completedAt DESC")
    List<ShortTermJobApplication> findCompletedByUserIdWithJob(@Param("userId") Long userId);

    // ==================== SLA / OVERDUE QUERIES ====================

    // Find applications where recruiter has exceeded 48h review SLA (SUBMITTED + deadline passed)
    @Query("SELECT a FROM ShortTermJobApplication a WHERE a.status = 'SUBMITTED' AND a.reviewDeadlineAt < :now")
    List<ShortTermJobApplication> findOverdueReviewApplications(@Param("now") java.time.LocalDateTime now);

    // Find applications where user has exceeded 72h response SLA for cancellation
    @Query("SELECT a FROM ShortTermJobApplication a WHERE a.status = 'CANCELLATION_REQUESTED' AND a.responseDeadlineAt < :now")
    List<ShortTermJobApplication> findOverdueCancellationResponse(@Param("now") java.time.LocalDateTime now);
}
