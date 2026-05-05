package com.exe.skillverse_backend.journey_service.node_mentoring.service.impl;

import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import com.exe.skillverse_backend.ai_service.entity.UserRoadmapProgress;
import com.exe.skillverse_backend.ai_service.repository.RoadmapSessionRepository;
import com.exe.skillverse_backend.ai_service.repository.UserRoadmapProgressRepository;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.ReviewNodeSubmissionRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.SubmitNodeEvidenceRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.UpsertNodeAssignmentRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.VerifyNodeRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.NodeAssignmentResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.NodeEvidenceRecordResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.NodeReviewResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.NodeVerificationResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeAssignment;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeAssignment.AssignmentSource;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeReview;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeReview.ReviewResult;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeSubmission;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeSubmission.SubmissionStatus;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeSubmission.VerificationStatus;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeVerification;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeVerification.NodeVerificationStatus;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.RoadmapNodeAssignmentRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.RoadmapNodeReviewRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.RoadmapNodeSubmissionRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.RoadmapNodeVerificationRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.service.NodeMentoringService;
import com.exe.skillverse_backend.journey_service.node_mentoring.service.RoadmapNodeResolver;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class NodeMentoringServiceImpl implements NodeMentoringService {

    /**
     * Statuses that grant a mentor review/verify authorization on a (journey, node).
     * COMPLETED/CANCELLED/REJECTED/DISPUTED/REFUNDED are intentionally excluded.
     */
    private static final List<BookingStatus> ASSIGNED_MENTOR_STATUSES = List.of(
            BookingStatus.CONFIRMED,
            BookingStatus.ONGOING,
            BookingStatus.MENTORING_ACTIVE,
            BookingStatus.PENDING_COMPLETION);

    private final RoadmapNodeResolver resolver;
    private final RoadmapNodeAssignmentRepository assignmentRepo;
    private final RoadmapNodeSubmissionRepository submissionRepo;
    private final RoadmapNodeReviewRepository reviewRepo;
    private final RoadmapNodeVerificationRepository verificationRepo;
    private final BookingRepository bookingRepository;
    private final UserRoadmapProgressRepository progressRepository;
    private final RoadmapSessionRepository roadmapSessionRepository;
    private final JourneyRepository journeyRepository;

    // ─── Assignment ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public NodeAssignmentResponse upsertAssignment(Long actingMentorId, Long journeyId, String nodeId,
                                                   UpsertNodeAssignmentRequest request) {
        Journey journey = resolver.resolveJourneyWithRoadmap(journeyId);
        requireAssignedMentor(actingMentorId, journeyId, nodeId);

        RoadmapNodeAssignment a = assignmentRepo
                .findFirstByJourneyIdAndNodeIdOrderByCreatedAtDesc(journeyId, nodeId)
                .orElseGet(() -> RoadmapNodeAssignment.builder()
                        .journeyId(journeyId)
                        .roadmapSessionId(journey.getRoadmapSessionId())
                        .nodeId(nodeId)
                        .createdBy(actingMentorId)
                        .build());

        a.setTitle(request.getTitle());
        a.setDescription(request.getDescription());
        a.setNodeSkillId(request.getNodeSkillId());
        a.setAssignmentSource(request.getAssignmentSource() != null
                ? request.getAssignmentSource()
                : AssignmentSource.MENTOR_REFINED);

        RoadmapNodeAssignment saved = assignmentRepo.save(a);
        return NodeAssignmentResponse.from(saved);
    }

    @Override
    public NodeAssignmentResponse getCurrentAssignment(Long journeyId, String nodeId) {
        resolver.resolveJourney(journeyId);
        return assignmentRepo.findFirstByJourneyIdAndNodeIdOrderByCreatedAtDesc(journeyId, nodeId)
                .map(NodeAssignmentResponse::from)
                .orElse(null);
    }

    // ─── Evidence ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public NodeEvidenceRecordResponse submitEvidence(Long learnerId, Long journeyId, String nodeId,
                                                     SubmitNodeEvidenceRequest request) {
        Journey journey = resolver.resolveJourneyWithRoadmap(journeyId);
        resolver.ensureLearnerOwns(journey, learnerId);

        var existingSubmission = submissionRepo.findByJourneyIdAndNodeId(journeyId, nodeId);
        boolean isNewSubmission = existingSubmission.isEmpty();
        RoadmapNodeSubmission s = existingSubmission
                .orElseGet(() -> RoadmapNodeSubmission.builder()
                        .journeyId(journeyId)
                        .roadmapSessionId(journey.getRoadmapSessionId())
                        .nodeId(nodeId)
                        .learnerId(learnerId)
                        .build());

        if (!isNewSubmission && !canLearnerSubmitEvidence(s)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Minh chứng đã được nộp và đang chờ mentor đánh giá. "
                            + "Bạn chỉ có thể nộp lại khi mentor yêu cầu làm lại hoặc đánh fail node này.");
        }

        s.setSubmissionText(request.getSubmissionText());
        s.setEvidenceUrl(request.getEvidenceUrl());
        s.setEvidencePublicId(request.getEvidencePublicId());
        s.setEvidenceResourceType(request.getEvidenceResourceType());
        s.setAttachmentUrl(request.getAttachmentUrl());
        s.setAttachmentPublicId(request.getAttachmentPublicId());
        s.setAttachmentResourceType(request.getAttachmentResourceType());

        // Ensure assignment exists - auto-create SYSTEM_GENERATED for free learners
        // or use existing assignment (mentor-refined or previously created)
        RoadmapNodeAssignment assignment = assignmentRepo
                .findFirstByJourneyIdAndNodeIdOrderByCreatedAtDesc(journeyId, nodeId)
                .orElseGet(() -> createSystemGeneratedAssignment(journey, nodeId));
        s.setAssignmentId(assignment.getId());

        boolean isRework = !isNewSubmission
                && (s.getSubmissionStatus() == SubmissionStatus.REWORK_REQUESTED
                || s.getSubmissionStatus() == SubmissionStatus.DRAFT
                || s.getVerificationStatus() == VerificationStatus.REJECTED);
        s.setSubmissionStatus(isRework ? SubmissionStatus.RESUBMITTED : SubmissionStatus.SUBMITTED);
        // New/updated submission resets verification state back to PENDING so a
        // mentor can review again. VERIFIED + unlocked journey shouldn't happen
        // because locked-journey case was rejected above.
        s.setVerificationStatus(VerificationStatus.PENDING);
        s.setLearnerMarkedComplete(false);

        RoadmapNodeSubmission saved = submissionRepo.save(s);
        boolean hasMentorCoverage = bookingRepository.existsActiveBookingCoveringNode(
                journeyId, nodeId, ASSIGNED_MENTOR_STATUSES);
        NodeEvidenceRecordResponse response = toEvidenceResponse(saved);
        response.setHasMentorCoverage(hasMentorCoverage);
        return response;
    }

    private boolean canLearnerSubmitEvidence(RoadmapNodeSubmission submission) {
        if (submission == null) {
            return true;
        }
        return submission.getSubmissionStatus() == SubmissionStatus.DRAFT
                || submission.getSubmissionStatus() == SubmissionStatus.REWORK_REQUESTED
                || submission.getVerificationStatus() == VerificationStatus.REJECTED;
    }

    @Override
    public NodeEvidenceRecordResponse getNodeEvidence(Long callerId, Long journeyId, String nodeId) {
        Journey journey = resolver.resolveJourney(journeyId);
        boolean isOwner = journey.getUser() != null && callerId.equals(journey.getUser().getId());
        boolean isAssignedMentor = bookingRepository.existsActiveNodeBookingForMentor(
                callerId, journeyId, nodeId, ASSIGNED_MENTOR_STATUSES);
        // Fallback: ROADMAP_MENTORING / JOURNEY_MENTORING bookings have nodeId=null
        // but still authorize the mentor to view evidence for any node in the journey.
        if (!isAssignedMentor) {
            isAssignedMentor = bookingRepository.existsActiveJourneyBookingForMentor(
                    callerId, journeyId, ASSIGNED_MENTOR_STATUSES);
        }
        if (!isOwner && !isAssignedMentor) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Access denied: you are not the journey owner or an assigned mentor for node " + nodeId);
        }
        boolean hasMentorCoverage = bookingRepository.existsActiveBookingCoveringNode(
                journeyId, nodeId, ASSIGNED_MENTOR_STATUSES);
        return submissionRepo.findByJourneyIdAndNodeId(journeyId, nodeId)
                .map(s -> {
                    NodeEvidenceRecordResponse r = toEvidenceResponse(s);
                    r.setHasMentorCoverage(hasMentorCoverage);
                    return r;
                })
                .orElse(null);
    }

    // ─── Review ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public NodeReviewResponse reviewSubmission(Long actingMentorId, Long journeyId, String nodeId,
                                               ReviewNodeSubmissionRequest request) {
        Journey journey = resolver.resolveJourneyWithRoadmap(journeyId);
        requireAssignedMentor(actingMentorId, journeyId, nodeId);

        RoadmapNodeSubmission s = submissionRepo.findByJourneyIdAndNodeId(journeyId, nodeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "No submission to review for node " + nodeId));

        if (s.getVerificationStatus() == VerificationStatus.VERIFIED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Submission already verified; review not allowed");
        }

        // requireLearnerMarkedNodeCompleted(journey, nodeId);

        Integer score = request.getReviewResult() == ReviewResult.APPROVED ? request.getScore() : null;

        RoadmapNodeReview review = RoadmapNodeReview.builder()
                .submissionId(s.getId())
                .mentorId(actingMentorId)
                .bookingId(request.getBookingId())
                .score(score)
                .feedback(request.getFeedback())
                .reviewResult(request.getReviewResult())
                .build();
        RoadmapNodeReview savedReview = reviewRepo.save(review);

        // Transition submission state based on review outcome.
        switch (request.getReviewResult()) {
            case APPROVED -> {
                s.setVerificationStatus(VerificationStatus.APPROVED);
                s.setMentorFeedback(request.getFeedback());
            }
            case REWORK_REQUESTED -> {
                s.setSubmissionStatus(SubmissionStatus.REWORK_REQUESTED);
                s.setVerificationStatus(VerificationStatus.UNDER_REVIEW);
                s.setMentorFeedback(request.getFeedback());
                s.setLearnerMarkedComplete(false);
                syncNodeCompletionState(journey, nodeId, false);
            }
            case REJECTED -> {
                s.setSubmissionStatus(SubmissionStatus.REWORK_REQUESTED);
                s.setVerificationStatus(VerificationStatus.REJECTED);
                s.setMentorFeedback(request.getFeedback());
                s.setLearnerMarkedComplete(false);
                syncNodeCompletionState(journey, nodeId, false);
            }
        }
        submissionRepo.save(s);

        return NodeReviewResponse.from(savedReview);
    }

    // ─── Verification ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public NodeVerificationResponse verifyNode(Long actingMentorId, Long journeyId, String nodeId,
                                               VerifyNodeRequest request) {
        Journey journey = resolver.resolveJourneyWithRoadmap(journeyId);
        requireAssignedMentor(actingMentorId, journeyId, nodeId);

        RoadmapNodeSubmission s = submissionRepo.findByJourneyIdAndNodeId(journeyId, nodeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "No submission to verify for node " + nodeId));

        RoadmapNodeReview latestReview = reviewRepo.findFirstBySubmissionIdOrderByReviewedAtDesc(s.getId())
                .orElse(null);
        if (latestReview == null || latestReview.getReviewResult() != ReviewResult.APPROVED
                || s.getVerificationStatus() != VerificationStatus.APPROVED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Cannot verify node before the latest review is APPROVED");
        }

        RoadmapNodeVerification v = RoadmapNodeVerification.builder()
                .submissionId(s.getId())
                .mentorId(actingMentorId)
                .bookingId(request.getBookingId())
                .nodeVerificationStatus(request.getNodeVerificationStatus())
                .verificationNote(request.getVerificationNote())
                .build();
        RoadmapNodeVerification savedVerification = verificationRepo.save(v);

        if (request.getNodeVerificationStatus() == NodeVerificationStatus.VERIFIED) {
            s.setVerificationStatus(VerificationStatus.VERIFIED);
            syncNodeCompletionState(journey, nodeId, true);
            recalculateAndSyncJourneyProgress(journey);
        } else {
            s.setSubmissionStatus(SubmissionStatus.REWORK_REQUESTED);
            s.setVerificationStatus(VerificationStatus.REJECTED);
            s.setLearnerMarkedComplete(false);
            syncNodeCompletionState(journey, nodeId, false);
        }
        submissionRepo.save(s);

        return NodeVerificationResponse.from(savedVerification);
    }

    // ─── Self-confirm ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public NodeEvidenceRecordResponse selfConfirmNode(Long learnerId, Long journeyId, String nodeId) {
        Journey journey = resolver.resolveJourneyWithRoadmap(journeyId);
        resolver.ensureLearnerOwns(journey, learnerId);

        RoadmapNodeSubmission s = submissionRepo.findByJourneyIdAndNodeId(journeyId, nodeId)
                .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT,
                        "Bạn cần nộp minh chứng trước khi xác nhận hoàn thành node này."));

        if (s.getSubmissionStatus() != SubmissionStatus.SUBMITTED
                && s.getSubmissionStatus() != SubmissionStatus.RESUBMITTED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Evidence phải ở trạng thái SUBMITTED hoặc RESUBMITTED trước khi tự xác nhận.");
        }

        // Mentor coverage is an audit/display signal only — it does not block self-confirmation.
        boolean hasMentorCoverage = bookingRepository.existsActiveBookingCoveringNode(
                journeyId, nodeId, ASSIGNED_MENTOR_STATUSES);
        s.setLearnerMarkedComplete(true);
        if (!hasMentorCoverage) {
            syncNodeCompletionState(journey, nodeId, true);
            recalculateAndSyncJourneyProgress(journey);
        }
        RoadmapNodeSubmission saved = submissionRepo.save(s);
        NodeEvidenceRecordResponse response = toEvidenceResponse(saved);
        response.setHasMentorCoverage(hasMentorCoverage);
        return response;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void requireAssignedMentor(Long mentorId, Long journeyId, String nodeId) {
        // First: check node-specific booking (NODE_MENTORING with nodeId set)
        boolean allowed = bookingRepository.existsActiveNodeBookingForMentor(
                mentorId, journeyId, nodeId, ASSIGNED_MENTOR_STATUSES);
        // Fallback: JOURNEY_MENTORING bookings have nodeId=null but still authorize
        // the mentor to review any node within that journey
        if (!allowed) {
            allowed = bookingRepository.existsActiveJourneyBookingForMentor(
                    mentorId, journeyId, ASSIGNED_MENTOR_STATUSES);
        }
        if (!allowed) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Mentor is not assigned to node " + nodeId + " in journey " + journeyId);
        }
    }



    private void syncNodeCompletionState(Journey journey, String nodeId, boolean completed) {
        if (journey == null || journey.getRoadmapSessionId() == null || nodeId == null || nodeId.isBlank()) {
            return;
        }

        RoadmapSession roadmapSession = roadmapSessionRepository
                .findById(journey.getRoadmapSessionId())
                .orElse(null);
        if (roadmapSession == null) {
            return;
        }

        UserRoadmapProgress progress = progressRepository
                .findBySessionIdAndQuestId(roadmapSession.getId(), nodeId)
                .orElse(UserRoadmapProgress.builder()
                        .roadmapSession(roadmapSession)
                        .questId(nodeId)
                        .status(UserRoadmapProgress.ProgressStatus.NOT_STARTED)
                        .progress(0)
                        .build());

        if (completed) {
            progress.setStatus(UserRoadmapProgress.ProgressStatus.COMPLETED);
            progress.setProgress(100);
            if (progress.getCompletedAt() == null) {
                progress.setCompletedAt(Instant.now());
            }
        } else {
            progress.setStatus(UserRoadmapProgress.ProgressStatus.NOT_STARTED);
            progress.setProgress(0);
            progress.setCompletedAt(null);
        }

        progressRepository.save(progress);
    }

    private void recalculateAndSyncJourneyProgress(Journey journey) {
        if (journey == null || journey.getRoadmapSessionId() == null) {
            return;
        }
        RoadmapSession session = roadmapSessionRepository
                .findById(journey.getRoadmapSessionId())
                .orElse(null);
        if (session == null) {
            return;
        }
        List<UserRoadmapProgress> allProgress =
                progressRepository.findBySessionId(journey.getRoadmapSessionId());
        long completedCount = allProgress.stream()
                .filter(p -> p.getStatus() == UserRoadmapProgress.ProgressStatus.COMPLETED)
                .count();
        // Use canonical totalNodes from the session so a single completed node in a
        // 10-node roadmap reports 10% (not 100% from allProgress.size() == 1).
        int totalNodes = (session.getTotalNodes() != null && session.getTotalNodes() > 0)
                ? session.getTotalNodes()
                : allProgress.size();
        if (totalNodes == 0) {
            return;
        }
        int roadmapPct = (int) Math.round(completedCount * 100.0 / totalNodes);
        // Map roadmap completion into lifecycle range [30, 90].
        // 0% => 30, 100% => 90. Final verification (100%) is set elsewhere.
        int mapped = Math.min(90, Math.max(30, (int) Math.round(30 + roadmapPct * 0.6)));
        int current = journey.getProgressPercentage() != null ? journey.getProgressPercentage() : 0;
        int next = Math.max(current, mapped);
        if (!Objects.equals(current, next)) {
            journey.setProgressPercentage(next);
            journeyRepository.save(journey);
        }
    }

    /**
     * Auto-creates a SYSTEM_GENERATED assignment from roadmap node content.
     * Used when free learner submits evidence without a mentor-created assignment.
     */
    private RoadmapNodeAssignment createSystemGeneratedAssignment(Journey journey, String nodeId) {
        RoadmapResponse.RoadmapNode nodeContent = resolver.getNodeContentFromRoadmap(journey, nodeId);

        RoadmapNodeAssignment assignment = RoadmapNodeAssignment.builder()
                .journeyId(journey.getId())
                .roadmapSessionId(journey.getRoadmapSessionId())
                .nodeId(nodeId)
                .title(nodeContent != null ? nodeContent.getTitle() : "Node " + nodeId)
                .description(formatNodeContentToDescription(nodeContent))
                .assignmentSource(AssignmentSource.SYSTEM_GENERATED)
                .createdBy(null) // System created
                .build();

        return assignmentRepo.save(assignment);
    }

    /**
     * Formats roadmap node content into assignment description.
     */
    private String formatNodeContentToDescription(RoadmapResponse.RoadmapNode node) {
        if (node == null) {
            return "Complete the tasks for this node and submit your evidence.";
        }

        StringBuilder sb = new StringBuilder();

        if (node.getDescription() != null && !node.getDescription().isBlank()) {
            sb.append(node.getDescription()).append("\n\n");
        }

        if (node.getLearningObjectives() != null && !node.getLearningObjectives().isEmpty()) {
            sb.append("**Learning Objectives:**\n");
            node.getLearningObjectives().forEach(o -> sb.append("- ").append(o).append("\n"));
            sb.append("\n");
        }

        if (node.getPracticalExercises() != null && !node.getPracticalExercises().isEmpty()) {
            sb.append("**Practical Exercises:**\n");
            node.getPracticalExercises().forEach(e -> sb.append("- ").append(e).append("\n"));
            sb.append("\n");
        }

        if (node.getSuccessCriteria() != null && !node.getSuccessCriteria().isEmpty()) {
            sb.append("**Success Criteria:**\n");
            node.getSuccessCriteria().forEach(c -> sb.append("- ").append(c).append("\n"));
        }

        return sb.toString().trim();
    }

    private NodeEvidenceRecordResponse toEvidenceResponse(RoadmapNodeSubmission s) {
        NodeReviewResponse latestReview = reviewRepo
                .findFirstBySubmissionIdOrderByReviewedAtDesc(s.getId())
                .map(NodeReviewResponse::from)
                .orElse(null);
        NodeVerificationResponse latestVerification = verificationRepo
                .findFirstBySubmissionIdOrderByVerifiedAtDesc(s.getId())
                .map(NodeVerificationResponse::from)
                .orElse(null);
        UserRoadmapProgress roadmapProgress = s.getRoadmapSessionId() != null
                ? progressRepository.findBySessionIdAndQuestId(s.getRoadmapSessionId(), s.getNodeId()).orElse(null)
                : null;
        return NodeEvidenceRecordResponse.from(s, latestReview, latestVerification, roadmapProgress);
    }
}
