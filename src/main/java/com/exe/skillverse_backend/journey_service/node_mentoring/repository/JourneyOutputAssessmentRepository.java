package com.exe.skillverse_backend.journey_service.node_mentoring.repository;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyOutputAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JourneyOutputAssessmentRepository extends JpaRepository<JourneyOutputAssessment, Long> {

    Optional<JourneyOutputAssessment> findFirstByJourneyIdOrderBySubmittedAtDesc(Long journeyId);
}
