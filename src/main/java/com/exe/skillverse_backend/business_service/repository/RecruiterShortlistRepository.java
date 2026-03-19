package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.RecruiterShortlist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for RecruiterShortlist
 */
@Repository
public interface RecruiterShortlistRepository extends JpaRepository<RecruiterShortlist, Long> {

    /**
     * Find shortlists by recruiter
     */
    Page<RecruiterShortlist> findByRecruiterId(Long recruiterId, Pageable pageable);

    /**
     * Find shortlists by recruiter and status
     */
    Page<RecruiterShortlist> findByRecruiterIdAndShortlistStatus(Long recruiterId,
            RecruiterShortlist.ShortlistStatus status, Pageable pageable);

    /**
     * Find specific shortlist
     */
    Optional<RecruiterShortlist> findByRecruiterIdAndCandidateIdAndJobPostingId(
            Long recruiterId, Long candidateId, Long jobPostingId);

    /**
     * Find shortlists by recruiter and candidate
     */
    List<RecruiterShortlist> findByRecruiterIdAndCandidateId(Long recruiterId, Long candidateId);

    /**
     * Check if shortlist exists
     */
    boolean existsByRecruiterIdAndCandidateIdAndJobPostingId(Long recruiterId, Long candidateId, Long jobPostingId);

    /**
     * Count shortlists by recruiter
     */
    long countByRecruiterIdAndShortlistStatus(Long recruiterId, RecruiterShortlist.ShortlistStatus status);
}
