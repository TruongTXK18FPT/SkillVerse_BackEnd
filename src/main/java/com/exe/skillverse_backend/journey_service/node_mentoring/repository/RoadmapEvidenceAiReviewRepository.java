package com.exe.skillverse_backend.journey_service.node_mentoring.repository;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.AiReviewStatus;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapEvidenceAiReview;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoadmapEvidenceAiReviewRepository extends JpaRepository<RoadmapEvidenceAiReview, Long> {
    
    Optional<RoadmapEvidenceAiReview> findTopByNodeSubmissionIdOrderByAttemptNumberDesc(Long nodeSubmissionId);
    
    Optional<RoadmapEvidenceAiReview> findTopByJourneyOutputAssessmentIdOrderByAttemptNumberDesc(Long journeyOutputAssessmentId);
    
    Page<RoadmapEvidenceAiReview> findByStatus(AiReviewStatus status, Pageable pageable);
}

