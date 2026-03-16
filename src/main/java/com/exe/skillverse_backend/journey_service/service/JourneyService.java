package com.exe.skillverse_backend.journey_service.service;

import com.exe.skillverse_backend.journey_service.dto.request.StartJourneyRequest;
import com.exe.skillverse_backend.journey_service.dto.request.SubmitTestRequest;
import com.exe.skillverse_backend.journey_service.dto.response.*;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.study_service.dto.request.GenerateScheduleRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service interface for Journey management.
 */
public interface JourneyService {

    // Journey lifecycle management

    /**
     * Start a new journey for a user with initial assessment data.
     * This creates the journey and prepares for AI test generation.
     */
    JourneySummaryResponse startJourney(User user, StartJourneyRequest request);

    /**
     * Get journey by ID for a user.
     */
    JourneySummaryResponse getJourneyById(User user, Long journeyId);

    /**
     * Get all journeys for a user.
     */
    Page<JourneySummaryResponse> getUserJourneys(User user, Pageable pageable);

    /**
     * Get active journeys for a user.
     */
    List<JourneySummaryResponse> getActiveJourneys(User user);

    /**
     * Update journey status.
     */
    JourneySummaryResponse updateJourneyStatus(User user, Long journeyId, Journey.JourneyStatus newStatus);

    /**
     * Pause a journey.
     */
    JourneySummaryResponse pauseJourney(User user, Long journeyId);

    /**
     * Resume a paused journey.
     */
    JourneySummaryResponse resumeJourney(User user, Long journeyId);

    /**
     * Cancel a journey.
     */
    JourneySummaryResponse cancelJourney(User user, Long journeyId);

    /**
     * Complete a journey.
     */
    JourneySummaryResponse completeJourney(User user, Long journeyId);

    // Test generation and evaluation

    /**
     * Generate AI assessment test for a journey based on assessment data.
     */
    GenerateTestResponse generateAssessmentTest(User user, Long journeyId);

    /**
     * Get assessment test details.
     */
    AssessmentTestResponse getAssessmentTest(User user, Long journeyId, Long testId);

    /**
     * Submit test answers and get evaluation.
     */
    TestResultResponse submitTest(User user, Long journeyId, SubmitTestRequest request);

    /**
     * Get test result for a journey.
     */
    TestResultResponse getTestResult(User user, Long journeyId, Long resultId);

    // Roadmap integration

    /**
     * Generate roadmap for journey based on evaluation results.
     * Links to existing roadmap service.
     */
    JourneySummaryResponse generateRoadmap(User user, Long journeyId);

    /**
     * Get roadmap session for a journey.
     */
    Object getRoadmapForJourney(User user, Long journeyId);

    // Study plan integration

    /**
     * Generate study plan suggestions for roadmap nodes.
     */
    Object generateStudyPlans(User user, Long journeyId);

    /**
     * Create study plan for a specific roadmap node.
     */
    Object createStudyPlanForNode(User user, Long journeyId, String nodeId, GenerateScheduleRequest request);

    /**
     * Create study plan for a specific roadmap node by roadmap session id.
     */
    Object createStudyPlanForRoadmapNode(User user, Long roadmapSessionId, String nodeId, GenerateScheduleRequest request);

    // AI Report

    /**
     * Generate AI summary report based on journey progress and activities.
     */
    JourneySummaryResponse generateAiReport(User user, Long journeyId);

    /**
     * Get current journey progress for dashboard.
     */
    JourneySummaryResponse getCurrentJourneyProgress(User user);
}
