package com.exe.skillverse_backend.journey_service.node_mentoring.controller;

import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.ReviewNodeSubmissionRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.SubmitNodeEvidenceRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.UpsertNodeAssignmentRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.VerifyNodeRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.NodeAssignmentResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.NodeEvidenceRecordResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.NodeReviewResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.NodeVerificationResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.service.NodeMentoringService;
import com.exe.skillverse_backend.shared.util.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Phase 1 node mentoring flow.
 *
 * Endpoints:
 * - Learner: submit/update evidence, read current evidence.
 * - Mentor: upsert assignment snapshot, review submission, verify node.
 *
 * All endpoints require authentication. Per-action role checks:
 * - Evidence submission: the journey's owning learner only.
 * - Assignment/Review/Verify: only mentors assigned to the node via an active booking.
 *   Enforcement lives in NodeMentoringServiceImpl.
 */
@RestController
@RequestMapping("/api/v1/journeys/{journeyId}/nodes/{nodeId}")
@RequiredArgsConstructor
public class NodeMentoringController {

    private final NodeMentoringService nodeMentoringService;

    // ─── Assignment ───────────────────────────────────────────────────────────

    @GetMapping("/assignment")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NodeAssignmentResponse> getAssignment(
            @PathVariable Long journeyId,
            @PathVariable String nodeId) {
        return ResponseEntity.ok(nodeMentoringService.getCurrentAssignment(journeyId, nodeId));
    }

    @PutMapping("/assignment")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<NodeAssignmentResponse> upsertAssignment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long journeyId,
            @PathVariable String nodeId,
            @Valid @RequestBody UpsertNodeAssignmentRequest request) {
        Long mentorId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(nodeMentoringService.upsertAssignment(mentorId, journeyId, nodeId, request));
    }

    // ─── Evidence ─────────────────────────────────────────────────────────────

    @GetMapping("/evidence")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NodeEvidenceRecordResponse> getEvidence(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long journeyId,
            @PathVariable String nodeId) {
        Long callerId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(nodeMentoringService.getNodeEvidence(callerId, journeyId, nodeId));
    }

    @PostMapping("/evidence")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NodeEvidenceRecordResponse> submitEvidence(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long journeyId,
            @PathVariable String nodeId,
            @Valid @RequestBody SubmitNodeEvidenceRequest request) {
        Long learnerId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(nodeMentoringService.submitEvidence(learnerId, journeyId, nodeId, request));
    }

    // ─── Self-confirm (free learner) ──────────────────────────────────────────

    @PostMapping("/self-confirm")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NodeEvidenceRecordResponse> selfConfirmNode(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long journeyId,
            @PathVariable String nodeId) {
        Long learnerId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(nodeMentoringService.selfConfirmNode(learnerId, journeyId, nodeId));
    }

    // ─── Review ───────────────────────────────────────────────────────────────

    @PostMapping("/review")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<NodeReviewResponse> reviewSubmission(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long journeyId,
            @PathVariable String nodeId,
            @Valid @RequestBody ReviewNodeSubmissionRequest request) {
        Long mentorId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(nodeMentoringService.reviewSubmission(mentorId, journeyId, nodeId, request));
    }

    // ─── Verification ─────────────────────────────────────────────────────────

    @PostMapping("/verify")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<NodeVerificationResponse> verifyNode(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long journeyId,
            @PathVariable String nodeId,
            @Valid @RequestBody VerifyNodeRequest request) {
        Long mentorId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(nodeMentoringService.verifyNode(mentorId, journeyId, nodeId, request));
    }

}
