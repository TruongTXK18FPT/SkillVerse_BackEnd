package com.exe.skillverse_backend.journey_service.node_mentoring.repository;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyCompletionReport;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyCompletionReport.GateDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JourneyCompletionReportRepository extends JpaRepository<JourneyCompletionReport, Long> {

    Optional<JourneyCompletionReport> findFirstByJourneyIdOrderByConfirmedAtDesc(Long journeyId);

    boolean existsByJourneyIdAndGateDecision(Long journeyId, GateDecision gateDecision);
}
