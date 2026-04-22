package com.exe.skillverse_backend.journey_service.node_mentoring.repository;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoadmapNodeAssignmentRepository extends JpaRepository<RoadmapNodeAssignment, Long> {

    Optional<RoadmapNodeAssignment> findFirstByJourneyIdAndNodeIdOrderByCreatedAtDesc(Long journeyId, String nodeId);

    List<RoadmapNodeAssignment> findByJourneyId(Long journeyId);
}
