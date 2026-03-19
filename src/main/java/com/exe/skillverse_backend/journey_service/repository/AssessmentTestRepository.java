package com.exe.skillverse_backend.journey_service.repository;

import com.exe.skillverse_backend.journey_service.entity.AssessmentTest;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssessmentTestRepository extends JpaRepository<AssessmentTest, Long> {

    /**
     * Find all tests for a journey
     */
    List<AssessmentTest> findByJourney(Journey journey);

    /**
     * Find test by ID and journey
     */
    Optional<AssessmentTest> findByIdAndJourney(Long id, Journey journey);

    /**
     * Find latest test for a journey
     */
    Optional<AssessmentTest> findTopByJourneyOrderByCreatedAtDesc(Journey journey);

    /**
     * Find tests by status
     */
    List<AssessmentTest> findByJourneyAndStatus(Journey journey, AssessmentTest.TestStatus status);

    /**
     * Find first completed test for a journey
     */
    Optional<AssessmentTest> findFirstByJourneyAndStatus(Journey journey, AssessmentTest.TestStatus status);

    /**
     * Count all generated assessment tests for a journey.
     */
    long countByJourney(Journey journey);
}
