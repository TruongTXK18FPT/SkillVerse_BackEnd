package com.exe.skillverse_backend.journey_service.node_mentoring.controller;

import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.AssessJourneyOutputRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.ConfirmJourneyCompletionRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.SubmitJourneyOutputAssessmentRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.JourneyCompletionGateResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.JourneyCompletionReportResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.JourneyOutputAssessmentResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.service.FinalVerificationGateService;
import com.exe.skillverse_backend.shared.util.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Final verification gate endpoints at journey level.
 *
 * - GET  /completion-gate    : anyone authenticated (learner/mentor) reads gate state.
 * - POST /completion-report  : mentor submits PASS/FAIL/PENDING gate decision.
 * - GET  /output-assessment  : read latest output assessment, if any.
 * - POST /output-assessment  : learner submits/updates output assessment.
 * - PUT  /output-assessment/assess : mentor assesses APPROVED/REJECTED.
 */
@RestController
@RequestMapping("/api/v1/journeys/{journeyId}")
@RequiredArgsConstructor
public class FinalVerificationController {

    private final FinalVerificationGateService gateService;

    @GetMapping("/completion-gate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JourneyCompletionGateResponse> getGate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long journeyId) {
        Long callerId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(gateService.evaluateGate(callerId, journeyId));
    }

    @PostMapping("/completion-report")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<JourneyCompletionReportResponse> submitCompletionReport(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long journeyId,
            @Valid @RequestBody ConfirmJourneyCompletionRequest request) {
        Long mentorId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(gateService.submitCompletionReport(mentorId, journeyId, request));
    }

    @GetMapping("/output-assessment")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JourneyOutputAssessmentResponse> getOutputAssessment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long journeyId) {
        Long callerId = JwtUtils.extractUserId(jwt);
        JourneyOutputAssessmentResponse result = gateService.getLatestOutputAssessment(callerId, journeyId);
        return result != null ? ResponseEntity.ok(result) : ResponseEntity.noContent().build();
    }

    @PostMapping("/output-assessment")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JourneyOutputAssessmentResponse> submitOutputAssessment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long journeyId,
            @Valid @RequestBody SubmitJourneyOutputAssessmentRequest request) {
        Long learnerId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(gateService.submitOutputAssessment(learnerId, journeyId, request));
    }

    @PutMapping("/output-assessment/assess")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<JourneyOutputAssessmentResponse> assessOutputAssessment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long journeyId,
            @Valid @RequestBody AssessJourneyOutputRequest request) {
        Long mentorId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(gateService.assessOutput(mentorId, journeyId, request));
    }

    @DeleteMapping("/completion-gate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> adminResetGate(@PathVariable Long journeyId) {
        gateService.adminResetGate(journeyId);
        return ResponseEntity.noContent().build();
    }

}
