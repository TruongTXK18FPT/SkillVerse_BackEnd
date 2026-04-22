package com.exe.skillverse_backend.journey_service.node_mentoring.repository;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoadmapNodeReviewRepository extends JpaRepository<RoadmapNodeReview, Long> {

    List<RoadmapNodeReview> findBySubmissionIdOrderByReviewedAtDesc(Long submissionId);

    Optional<RoadmapNodeReview> findFirstBySubmissionIdOrderByReviewedAtDesc(Long submissionId);

    boolean existsBySubmissionId(Long submissionId);
}
