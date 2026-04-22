package com.exe.skillverse_backend.journey_service.node_mentoring.repository;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoadmapNodeVerificationRepository extends JpaRepository<RoadmapNodeVerification, Long> {

    List<RoadmapNodeVerification> findBySubmissionIdOrderByVerifiedAtDesc(Long submissionId);

    Optional<RoadmapNodeVerification> findFirstBySubmissionIdOrderByVerifiedAtDesc(Long submissionId);
}
