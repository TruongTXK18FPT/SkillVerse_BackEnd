package com.exe.skillverse_backend.journey_service.node_mentoring.service;

import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.ReviewNodeSubmissionRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.SubmitNodeEvidenceRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.UpsertNodeAssignmentRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.VerifyNodeRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.NodeAssignmentResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.NodeEvidenceRecordResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.NodeReviewResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.NodeVerificationResponse;

/**
 * Core node mentoring flow: assignment snapshot, evidence submit/update,
 * mentor review, mentor node verification, and read access.
 *
 * Authorization rule (Phase 1): a mentor may review/verify only if they have
 * at least one active booking ({@code CONFIRMED / ONGOING / PENDING_COMPLETION})
 * for the same (journeyId, nodeId) — enforced in the implementation.
 */
public interface NodeMentoringService {

    // ─── Assignment ───────────────────────────────────────────────────────────

    /**
     * Mentor-refined upsert of the node assignment snapshot.
     * The invoking mentor must be an assigned mentor for this (journey, node).
     */
    NodeAssignmentResponse upsertAssignment(
            Long actingMentorId,
            Long journeyId,
            String nodeId,
            UpsertNodeAssignmentRequest request);

    /** Read the current assignment if any. */
    NodeAssignmentResponse getCurrentAssignment(Long journeyId, String nodeId);

    // ─── Evidence ─────────────────────────────────────────────────────────────

    /**
     * Learner submit/update evidence. Upserts the single current record for
     * (journeyId, nodeId). If the record exists and is in REWORK_REQUESTED, it
     * transitions to RESUBMITTED; otherwise becomes SUBMITTED.
     */
    NodeEvidenceRecordResponse submitEvidence(
            Long learnerId,
            Long journeyId,
            String nodeId,
            SubmitNodeEvidenceRequest request);

    /**
     * Read evidence for a node. Access is granted to:
     * - the journey owner (learner)
     * - any mentor with an active booking for (journeyId, nodeId)
     */
    NodeEvidenceRecordResponse getNodeEvidence(Long callerId, Long journeyId, String nodeId);

    // ─── Review ───────────────────────────────────────────────────────────────

    /**
     * Mentor review. Must be an assigned mentor. Creates a RoadmapNodeReview and
     * transitions submission.verificationStatus to APPROVED / UNDER_REVIEW / REJECTED
     * and submission.submissionStatus when appropriate (REWORK_REQUESTED).
     */
    NodeReviewResponse reviewSubmission(
            Long actingMentorId,
            Long journeyId,
            String nodeId,
            ReviewNodeSubmissionRequest request);

    // ─── Verification ─────────────────────────────────────────────────────────

    /**
     * Mentor node verification. Must be an assigned mentor AND the submission
     * must have at least one APPROVED review before verification is allowed.
     * On VERIFIED, submission.verificationStatus becomes VERIFIED.
     */
    NodeVerificationResponse verifyNode(
            Long actingMentorId,
            Long journeyId,
            String nodeId,
            VerifyNodeRequest request);
}
