package com.exe.skillverse_backend.journey_service.node_mentoring.repository;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeSubmission;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeSubmission.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoadmapNodeSubmissionRepository extends JpaRepository<RoadmapNodeSubmission, Long> {

    /** Returns the single current evidence record for a given node within a journey. */
    Optional<RoadmapNodeSubmission> findByJourneyIdAndNodeId(Long journeyId, String nodeId);

    List<RoadmapNodeSubmission> findByJourneyId(Long journeyId);

    long countByJourneyIdAndVerificationStatus(Long journeyId, VerificationStatus status);
}
