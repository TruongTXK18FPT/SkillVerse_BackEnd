package com.exe.skillverse_backend.journey_service.node_mentoring.service;

import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.AssessJourneyOutputRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.ConfirmJourneyCompletionRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.SubmitJourneyOutputAssessmentRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.JourneyCompletionGateResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.JourneyCompletionReportResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.JourneyOutputAssessmentResponse;

/**
 * Final verification gate for a journey.
 *
 * Gate is evaluated from two signals:
 * 1. At least one JourneyCompletionReport with gateDecision = PASS.
 * 2. If journey.journeyOutputVerificationRequired = true, the latest
 *    JourneyOutputAssessment must be APPROVED.
 *
 * When journey.finalVerificationRequired = false the gate is NOT_REQUIRED
 * and completeJourney proceeds without it.
 */
public interface FinalVerificationGateService {

    /**
     * Evaluates the gate state. Caller must be the journey owner or an assigned mentor.
     */
    JourneyCompletionGateResponse evaluateGate(Long callerId, Long journeyId);

    /**
     * Throws an ApiException(CONFLICT) when the gate is required but not PASSED.
     * No-op when the gate is NOT_REQUIRED or already PASSED.
     */
    void requireGatePassed(Journey journey);

    // ─── Completion report (mentor) ───────────────────────────────────────────

    JourneyCompletionReportResponse submitCompletionReport(
            Long actingMentorId,
            Long journeyId,
            ConfirmJourneyCompletionRequest request);

    // ─── Output assessment (learner submit, mentor assess) ────────────────────

    JourneyOutputAssessmentResponse submitOutputAssessment(
            Long learnerId,
            Long journeyId,
            SubmitJourneyOutputAssessmentRequest request);

    JourneyOutputAssessmentResponse assessOutput(
            Long actingMentorId,
            Long journeyId,
            AssessJourneyOutputRequest request);

    /**
     * Returns the latest output assessment. Caller must be journey owner or assigned mentor.
     * Returns null (204) when none exists.
     */
    JourneyOutputAssessmentResponse getLatestOutputAssessment(Long callerId, Long journeyId);

    /**
     * Admin-only: reset finalVerificationRequired to false when a journey is permanently
     * stuck (e.g. mentor confirmed but never submitted completion report).
     * Allows learner to complete as COMPLETED_UNVERIFIED.
     */
    void adminResetGate(Long journeyId);
}
