package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.CandidateSearchSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for CandidateSearchSession
 */
@Repository
public interface CandidateSearchSessionRepository extends JpaRepository<CandidateSearchSession, Long> {

    List<CandidateSearchSession> findByRecruiterIdOrderBySearchedAtDesc(Long recruiterId);
}
