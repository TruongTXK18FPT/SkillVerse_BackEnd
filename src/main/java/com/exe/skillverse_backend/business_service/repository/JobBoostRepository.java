package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.JobBoost;
import com.exe.skillverse_backend.business_service.entity.enums.JobBoostStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for JobBoost entity
 */
@Repository
public interface JobBoostRepository extends JpaRepository<JobBoost, Long> {

    /**
     * Find active boost by job posting ID
     */
    Optional<JobBoost> findByJobPostingIdAndBoostStatus(Long jobPostingId, JobBoostStatus status);

    /**
     * Find active boost for a job posting (regardless of specific status)
     */
    @Query("SELECT jb FROM JobBoost jb WHERE jb.jobPosting.id = :jobPostingId AND jb.boostStatus = 'ACTIVE' AND jb.expiresAt > CURRENT_TIMESTAMP")
    Optional<JobBoost> findActiveBoostByJobPostingId(@Param("jobPostingId") Long jobPostingId);

    /**
     * Find all active boosts for a recruiter
     */
    List<JobBoost> findByRecruiterIdAndBoostStatus(Long recruiterId, JobBoostStatus status);

    /**
     * Find all active boosts (for job listing ranking)
     */
    @Query("SELECT jb FROM JobBoost jb WHERE jb.boostStatus = 'ACTIVE' AND jb.expiresAt > CURRENT_TIMESTAMP AND (jb.scheduledStartAt IS NULL OR jb.scheduledStartAt <= CURRENT_TIMESTAMP)")
    List<JobBoost> findAllActiveBoosts();

    /**
     * Find boosted job IDs (for ranking)
     */
    @Query("SELECT jb.jobPosting.id FROM JobBoost jb WHERE jb.boostStatus = 'ACTIVE' AND jb.expiresAt > CURRENT_TIMESTAMP AND (jb.scheduledStartAt IS NULL OR jb.scheduledStartAt <= CURRENT_TIMESTAMP)")
    List<Long> findAllActiveBoostedJobIds();

    /**
     * Find boosts expiring before given date (for scheduler)
     */
    @Query("SELECT jb FROM JobBoost jb WHERE jb.boostStatus = 'ACTIVE' AND jb.expiresAt <= :expiryDate")
    List<JobBoost> findBoostsExpiringBefore(@Param("expiryDate") LocalDateTime expiryDate);

    /**
     * Find scheduled boosts that should now be active
     */
    @Query("SELECT jb FROM JobBoost jb WHERE jb.boostStatus = 'SCHEDULED' AND jb.scheduledStartAt <= :currentTime")
    List<JobBoost> findScheduledBoostsReadyToActivate(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Count active boosts for a recruiter
     */
    @Query("SELECT COUNT(jb) FROM JobBoost jb WHERE jb.recruiterId = :recruiterId AND jb.boostStatus = 'ACTIVE' AND jb.expiresAt > CURRENT_TIMESTAMP")
    int countActiveBoostsByRecruiter(@Param("recruiterId") Long recruiterId);

    /**
     * Check if a job has active boost
     */
    @Query("SELECT CASE WHEN COUNT(jb) > 0 THEN true ELSE false END FROM JobBoost jb WHERE jb.jobPosting.id = :jobPostingId AND jb.boostStatus = 'ACTIVE' AND jb.expiresAt > CURRENT_TIMESTAMP")
    boolean hasActiveBoost(@Param("jobPostingId") Long jobPostingId);

    /**
     * Find boost by job posting ID (any status)
     */
    Optional<JobBoost> findByJobPostingId(Long jobPostingId);

    /**
     * Delete boosts for a specific job posting.
     */
    @Modifying
    void deleteByJobPostingId(Long jobPostingId);

    /**
     * Update boost status
     */
    @Modifying
    @Query("UPDATE JobBoost jb SET jb.boostStatus = :status, jb.updatedAt = CURRENT_TIMESTAMP WHERE jb.id = :id")
    void updateBoostStatus(@Param("id") Long id, @Param("status") JobBoostStatus status);
}
