package com.exe.skillverse_backend.journey_service.repository;

import com.exe.skillverse_backend.journey_service.entity.AssessmentTest;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.entity.TestResult;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestResultRepository extends JpaRepository<TestResult, Long> {

    /**
     * Find all results for a journey
     */
    List<TestResult> findByJourney(Journey journey);

    /**
     * Find result by assessment test
     */
    Optional<TestResult> findByAssessmentTest(AssessmentTest assessmentTest);

    /**
     * Find latest result for a journey
     */
    Optional<TestResult> findTopByJourneyOrderByCreatedAtDesc(Journey journey);

    /**
     * Find result by journey and test
     */
    Optional<TestResult> findByJourneyAndAssessmentTest(Journey journey, AssessmentTest assessmentTest);
}
