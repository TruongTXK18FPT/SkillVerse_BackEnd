package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.CandidateMatchScore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for CandidateMatchScore
 */
@Repository
public interface CandidateMatchScoreRepository extends JpaRepository<CandidateMatchScore, Long> {

    /**
     * Find match scores for a job, ordered by total score
     */
    @Query("SELECT cms FROM CandidateMatchScore cms WHERE cms.jobPosting.id = :jobId ORDER BY cms.totalScore DESC")
    Page<CandidateMatchScore> findByJobPostingIdOrderByScoreDesc(@Param("jobId") Long jobId, Pageable pageable);

    /**
     * Find match score for a specific job-candidate pair
     */
    Optional<CandidateMatchScore> findByJobPostingIdAndCandidateId(Long jobId, Long candidateId);

    /**
     * Find all match scores for a candidate
     */
    List<CandidateMatchScore> findByCandidateIdOrderByTotalScoreDesc(Long candidateId);

    /**
     * Check if match score exists
     */
    boolean existsByJobPostingIdAndCandidateId(Long jobId, Long candidateId);

    /**
     * Delete all match scores for a job
     */
    void deleteByJobPostingId(Long jobId);
}
