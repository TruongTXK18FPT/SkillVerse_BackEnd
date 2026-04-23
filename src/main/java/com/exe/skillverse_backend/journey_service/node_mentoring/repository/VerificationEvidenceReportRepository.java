package com.exe.skillverse_backend.journey_service.node_mentoring.repository;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.VerificationEvidenceReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VerificationEvidenceReportRepository extends JpaRepository<VerificationEvidenceReport, Long> {

    List<VerificationEvidenceReport> findByJourneyIdOrderByAttemptNumberAsc(Long journeyId);

    Optional<VerificationEvidenceReport> findFirstByJourneyIdOrderByAttemptNumberDesc(Long journeyId);

    Optional<VerificationEvidenceReport> findByJourneyIdAndAttemptNumber(Long journeyId, Integer attemptNumber);

    boolean existsByJourneyIdAndAttemptNumber(Long journeyId, Integer attemptNumber);
}
