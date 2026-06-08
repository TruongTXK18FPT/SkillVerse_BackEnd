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
import com.exe.skillverse_backend.journey_service.node_mentoring.service.RoadmapNodeCompletionSyncService;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.ai.service.RoadmapEvidenceAiReviewService;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplate;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateActivity;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateSkillBlock;
import com.exe.skillverse_backend.roadmap_package_service.repository.RoadmapTemplateRepository;
import com.exe.skillverse_backend.roadmap_package_service.repository.RoadmapTemplateNodeGroupRepository;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateNodeGroup;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.GradingCriterionDto;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.GradingCriterionScoreDto;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
    private final CloudinaryService cloudinaryService;
    private final RoadmapNodeAssignmentRepository assignmentRepo;
    private final RoadmapNodeSubmissionRepository submissionRepo;
    private final RoadmapNodeReviewRepository reviewRepo;
    private final RoadmapNodeVerificationRepository verificationRepo;
    private final BookingRepository bookingRepository;
    private final UserRoadmapProgressRepository progressRepository;
    private final RoadmapSessionRepository roadmapSessionRepository;
    private final JourneyRepository journeyRepository;
    private final RoadmapEvidenceAiReviewService aiReviewService;
    private final RoadmapTemplateRepository templateRepository;
    private final RoadmapNodeCompletionSyncService syncService;
    private final RoadmapTemplateNodeGroupRepository nodeGroupRepository;
    private final ObjectMapper objectMapper;
    private final com.exe.skillverse_backend.notification_service.service.NotificationService notificationService;
    private final com.exe.skillverse_backend.shared.service.EmailService emailService;

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
        a.setExpectedOutput(request.getExpectedOutput());
        a.setRubric(request.getRubric());
        a.setAssignmentSource(request.getAssignmentSource() != null
                ? request.getAssignmentSource()
                : AssignmentSource.MENTOR_REFINED);

        // Auto-approve when mentor explicitly refines the assignment
        if (a.getAssignmentSource() == AssignmentSource.MENTOR_REFINED) {
            a.setVerificationStatus("APPROVED");
        }

        if (request.getCriteria() != null) {
            try {
                a.setCriteriaJson(objectMapper.writeValueAsString(request.getCriteria()));
            } catch (Exception ex) {
                log.error("Failed to serialize criteria to JSON", ex);
            }
        }

        RoadmapNodeAssignment saved = assignmentRepo.save(a);

        // Notify student that mentor has assigned/updated the assessment
        notifyStudentAssessmentAssigned(journey, saved, actingMentorId);

        return NodeAssignmentResponse.from(saved);
    }

    @Override
    public NodeAssignmentResponse getCurrentAssignment(Long journeyId, String nodeId) {
        resolver.resolveJourney(journeyId);
        return assignmentRepo.findFirstByJourneyIdAndNodeIdOrderByCreatedAtDesc(journeyId, nodeId)
                .map(NodeAssignmentResponse::from)
                .orElse(null);
    }

    @Override
    @Transactional
    public NodeAssignmentResponse approveAssignment(Long actingMentorId, Long journeyId, String nodeId) {
        Journey journey = resolver.resolveJourneyWithRoadmap(journeyId);
        requireAssignedMentor(actingMentorId, journeyId, nodeId);

        RoadmapNodeAssignment a = assignmentRepo
                .findFirstByJourneyIdAndNodeIdOrderByCreatedAtDesc(journeyId, nodeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Chưa có assignment cho node " + nodeId + " trong journey " + journeyId));

        if ("APPROVED".equals(a.getVerificationStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, "Assessment đã được duyệt trước đó.");
        }

        a.setVerificationStatus("APPROVED");
        RoadmapNodeAssignment saved = assignmentRepo.save(a);

        // Notify student that mentor has approved the assessment
        notifyStudentAssessmentApproved(journey, saved, actingMentorId);

        return NodeAssignmentResponse.from(saved);
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

        boolean hasMentorCoverage = bookingRepository.existsActiveBookingCoveringNode(
                journeyId, nodeId, ASSIGNED_MENTOR_STATUSES);

        // Block submit if mentor-covered but assignment not approved
        if (hasMentorCoverage && assignment.getVerificationStatus() != null
                && !"APPROVED".equals(assignment.getVerificationStatus())) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Assessment chưa được mentor duyệt. Vui lòng chờ mentor xác nhận hoặc cập nhật bài tập trước khi nộp minh chứng.");
        }

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
                
        // Trigger AI Review for unmentored learners
        if (!hasMentorCoverage && journey.getRoadmapSessionId() != null) {
            roadmapSessionRepository.findById(journey.getRoadmapSessionId()).ifPresent(session -> {
                if (session.getRoadmapTemplateId() != null) {
                    templateRepository.findById(session.getRoadmapTemplateId()).ifPresent(template -> {
                        if (Boolean.TRUE.equals(template.getAiEvidenceReviewEnabled())) {
                            RoadmapResponse.RoadmapNode node = resolver.getNodeContentFromRoadmap(journey, nodeId);
                            String nodeTitle = node != null ? node.getTitle() : "";
                            String nodeDesc = node != null ? node.getDescription() : "";
                            
                            // Retrieve expected output and rubric directly from the active assignment setup by admin/mentor
                            RoadmapNodeAssignment activeAssignment = assignmentRepo.findById(saved.getAssignmentId()).orElse(null);

                            String tempExpectedOutput = activeAssignment != null && activeAssignment.getExpectedOutput() != null 
                                    ? activeAssignment.getExpectedOutput() : "";
                            String tempRubric = activeAssignment != null && activeAssignment.getRubric() != null 
                                    ? activeAssignment.getRubric() : "";
                            String tempAiPromptHint = "";
                            String tempSkillRequirementsJson = "";

                            // Attempt to resolve matching template activity to extract supplementary AI hints and skill requirements
                            RoadmapTemplateActivity matchingActivity = resolveActivityForNode(template, node);
                            if (matchingActivity != null) {
                                if (tempExpectedOutput.isBlank()) {
                                    tempExpectedOutput = matchingActivity.getExpectedOutput() != null ? matchingActivity.getExpectedOutput() : "";
                                }
                                if (tempRubric.isBlank()) {
                                    tempRubric = matchingActivity.getRubric() != null ? matchingActivity.getRubric() : "";
                                }
                                tempAiPromptHint = matchingActivity.getAiPromptHint() != null ? matchingActivity.getAiPromptHint() : "";
                                tempSkillRequirementsJson = matchingActivity.getSkillRequirementsJson() != null ? matchingActivity.getSkillRequirementsJson() : "";
                            }
                            
                            // [SAFE FALLBACK] Always check and fall back to raw node criteria if fields are still blank after resolving template activity
                            if (node != null) {
                                if (tempRubric.isBlank() && node.getSuccessCriteria() != null && !node.getSuccessCriteria().isEmpty()) {
                                    tempRubric = String.join("\n", node.getSuccessCriteria());
                                }
                                if (tempExpectedOutput.isBlank() && node.getPracticalExercises() != null && !node.getPracticalExercises().isEmpty()) {
                                    tempExpectedOutput = String.join("\n", node.getPracticalExercises());
                                }
                            }

                            final String activityExpectedOutput = tempExpectedOutput;
                            final String activityRubric = tempRubric;
                            final String activityAiPromptHint = tempAiPromptHint;
                            final String activitySkillRequirementsJson = tempSkillRequirementsJson;

                            String nodeSkillsVal = "";
                            if (node != null && node.getSkills() != null) {
                                List<String> skillNames = node.getSkills().stream()
                                        .map(RoadmapResponse.NodeSkillRequirement::getSkillName)
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toList());
                                nodeSkillsVal = String.join(", ", skillNames);
                            }
                            final String nodeSkills = nodeSkillsVal;
                            
                            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                                    @Override
                                    public void afterCommit() {
                                        aiReviewService.reviewNodeEvidence(saved, template, 
                                                nodeTitle, nodeDesc, nodeSkills, 
                                                activityExpectedOutput, activityRubric, 
                                                activityAiPromptHint, activitySkillRequirementsJson);
                                    }
                                });
                            } else {
                                aiReviewService.reviewNodeEvidence(saved, template, 
                                        nodeTitle, nodeDesc, nodeSkills, 
                                        activityExpectedOutput, activityRubric, 
                                        activityAiPromptHint, activitySkillRequirementsJson);
                            }
                        } else {
                            log.info("AI review is disabled for template {}. Skipping automatic AI review trigger for node submission.", template.getId());
                        }
                    });
                }
            });
        }
                
        NodeEvidenceRecordResponse response = toEvidenceResponse(saved);
        response.setHasMentorCoverage(hasMentorCoverage);

        // Notify mentor that student has submitted evidence
        if (hasMentorCoverage) {
            notifyMentorEvidenceSubmitted(journey, nodeId, learnerId);
        }

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

        Integer score = null;
        String criteriaScoresJson = null;
        if (request.getReviewResult() == ReviewResult.APPROVED) {
            if (request.getCriteriaScores() != null && !request.getCriteriaScores().isEmpty()) {
                double totalEarned = 0.0;
                double totalMax = 0.0;
                for (GradingCriterionScoreDto cs : request.getCriteriaScores()) {
                    totalEarned += cs.getScore() != null ? cs.getScore() : 0.0;
                    totalMax += cs.getMaxScore() != null ? cs.getMaxScore() : 10.0;
                }
                if (totalMax > 0) {
                    score = (int) Math.round((totalEarned / totalMax) * 100.0);
                }
                try {
                    criteriaScoresJson = objectMapper.writeValueAsString(request.getCriteriaScores());
                } catch (Exception ex) {
                    log.error("Failed to serialize criteria scores to JSON", ex);
                }
            } else {
                score = request.getScore();
            }
        }

        RoadmapNodeReview review = RoadmapNodeReview.builder()
                .submissionId(s.getId())
                .mentorId(actingMentorId)
                .bookingId(request.getBookingId())
                .score(score)
                .feedback(request.getFeedback())
                .criteriaScoresJson(criteriaScoresJson)
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
                syncService.syncNodeCompletionState(journey, nodeId, false);
            }
            case REJECTED -> {
                s.setSubmissionStatus(SubmissionStatus.REWORK_REQUESTED);
                s.setVerificationStatus(VerificationStatus.REJECTED);
                s.setMentorFeedback(request.getFeedback());
                s.setLearnerMarkedComplete(false);
                syncService.syncNodeCompletionState(journey, nodeId, false);
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
            syncService.syncNodeCompletionState(journey, nodeId, true);
            syncService.recalculateAndSyncJourneyProgress(journey);
        } else {
            s.setSubmissionStatus(SubmissionStatus.REWORK_REQUESTED);
            s.setVerificationStatus(VerificationStatus.REJECTED);
            s.setLearnerMarkedComplete(false);
            syncService.syncNodeCompletionState(journey, nodeId, false);
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

        // Check if template has AI review enabled and auto-pass is enabled
        boolean aiReviewEnabled = false;
        if (journey.getRoadmapSessionId() != null) {
            aiReviewEnabled = roadmapSessionRepository.findById(journey.getRoadmapSessionId())
                    .flatMap(session -> session.getRoadmapTemplateId() != null 
                            ? templateRepository.findById(session.getRoadmapTemplateId()) 
                            : java.util.Optional.empty())
                    .map(template -> Boolean.TRUE.equals(template.getAiEvidenceReviewEnabled()) 
                            && Boolean.TRUE.equals(template.getAiAutoPassEnabled()))
                    .orElse(false);
        }

        if (!hasMentorCoverage && aiReviewEnabled && s.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Node này cần được hệ thống hoặc quản trị viên đánh giá đạt trước khi xác nhận hoàn thành.");
        }

        s.setLearnerMarkedComplete(true);
        if (!hasMentorCoverage) {
            syncService.syncNodeCompletionState(journey, nodeId, true);
            syncService.recalculateAndSyncJourneyProgress(journey);
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



    /**
     * Resolves a matching template activity for the given roadmap node by performing
     * a case-insensitive match on the activity title.
     *
     * @param template the roadmap template containing the skill blocks and activities
     * @param node the roadmap node representing the current learning quest
     * @return the matching RoadmapTemplateActivity, or null if no match is found or arguments are null
     */
    private RoadmapTemplateActivity resolveActivityForNode(RoadmapTemplate template, RoadmapResponse.RoadmapNode node) {
        if (template == null || node == null || node.getTitle() == null || template.getSkillBlocks() == null) {
            return null;
        }
        
        // Extract the skill IDs associated with this node to strictly bound the search scope
        List<Long> nodeSkillIds = new ArrayList<>();
        if (node.getSkills() != null) {
            for (RoadmapResponse.NodeSkillRequirement req : node.getSkills()) {
                if (req.getSkillId() != null) {
                    nodeSkillIds.add(req.getSkillId());
                }
            }
        }

        for (RoadmapTemplateSkillBlock block : template.getSkillBlocks()) {
            // Match within the correct skill block that meets the node's skill requirements
            if (nodeSkillIds.isEmpty() || nodeSkillIds.contains(block.getSkillId())) {
                if (block.getActivities() != null) {
                    for (RoadmapTemplateActivity act : block.getActivities()) {
                        if (node.getTitle().equalsIgnoreCase(act.getTitle())) {
                            return act;
                        }
                    }
                }
            }
        }
        return null;
    }

    private RoadmapNodeAssignment createSystemGeneratedAssignment(Journey journey, String nodeId) {
        RoadmapResponse.RoadmapNode nodeContent = resolver.getNodeContentFromRoadmap(journey, nodeId);

        // Find the matching template node group to extract structured exercises & rubrics
        RoadmapTemplateNodeGroup matchedGroup = null;
        if (journey.getRoadmapSessionId() != null) {
            try {
                RoadmapSession session = roadmapSessionRepository.findById(journey.getRoadmapSessionId()).orElse(null);
                if (session != null && session.getRoadmapTemplateId() != null) {
                    List<RoadmapTemplateNodeGroup> groups = nodeGroupRepository.findByTemplateIdOrderByOrderIndexAscIdAsc(session.getRoadmapTemplateId());
                    for (int i = 0; i < groups.size(); i++) {
                        RoadmapTemplateNodeGroup g = groups.get(i);
                        String key = g.getNodeKey();
                        if (key == null || key.isBlank()) {
                            key = "module-" + (i + 1);
                        }
                        if (key.equalsIgnoreCase(nodeId)) {
                            matchedGroup = g;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to match roadmap template node group for journey ID: {}", journey.getId(), e);
            }
        }

        String title = nodeContent != null ? nodeContent.getTitle() : "Node " + nodeId;
        String description = "";
        String expectedOutput = "";
        String rubric = "";

        if (matchedGroup != null && matchedGroup.getExercisesJson() != null && !matchedGroup.getExercisesJson().isBlank()) {
            try {
                JsonNode exercisesArr = objectMapper.readTree(matchedGroup.getExercisesJson());
                if (exercisesArr.isArray() && exercisesArr.size() > 0) {
                    // Extract first/main exercise
                    JsonNode ex = exercisesArr.get(0);
                    title = ex.path("title").asText(title);
                    description = ex.path("instruction").asText("");
                    expectedOutput = ex.path("expectedOutput").asText("");
                    rubric = ex.path("rubric").asText("");
                }
            } catch (Exception e) {
                log.warn("Failed to parse exercisesJson for template group ID: {}", matchedGroup.getId(), e);
            }
        }

        // Fallbacks for empty fields
        if (description.isBlank()) {
            if (nodeContent != null && nodeContent.getPracticalExercises() != null && !nodeContent.getPracticalExercises().isEmpty()) {
                description = nodeContent.getPracticalExercises().stream()
                        .map(ex -> "- " + ex.trim())
                        .collect(Collectors.joining("\n"));
            } else {
                description = formatNodeContentToDescription(nodeContent);
            }
        }
        if (expectedOutput.isBlank()) {
            expectedOutput = nodeContent != null && nodeContent.getPracticalExercises() != null 
                    ? String.join("\n", nodeContent.getPracticalExercises()) 
                    : "";
        }
        if (rubric.isBlank()) {
            rubric = nodeContent != null && nodeContent.getSuccessCriteria() != null 
                    ? String.join("\n", nodeContent.getSuccessCriteria()) 
                    : "";
        }

        List<GradingCriterionDto> defaultCriteria = List.of(
            GradingCriterionDto.builder().id("c1").title("Completeness (Hoàn thành bài tập)").maxScore(10).build(),
            GradingCriterionDto.builder().id("c2").title("Accuracy (Độ chính xác kỹ thuật)").maxScore(10).build(),
            GradingCriterionDto.builder().id("c3").title("Quality (Chất lượng giải trình)").maxScore(10).build()
        );
        String defaultCriteriaJson = null;
        try {
            defaultCriteriaJson = objectMapper.writeValueAsString(defaultCriteria);
        } catch (Exception ex) {
            log.error("Failed to serialize system default criteria to JSON", ex);
        }

        RoadmapNodeAssignment assignment = RoadmapNodeAssignment.builder()
                .journeyId(journey.getId())
                .roadmapSessionId(journey.getRoadmapSessionId())
                .nodeId(nodeId)
                .title(title)
                .description(description)
                .expectedOutput(expectedOutput)
                .rubric(rubric)
                .criteriaJson(defaultCriteriaJson)
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
        
        NodeEvidenceRecordResponse response = NodeEvidenceRecordResponse.from(s, latestReview, latestVerification, roadmapProgress);
        
        // Auto-sign URLs if needed to prevent 401 Unauthorized for secure assets (PDF, DOCX etc)
        if (response.getEvidenceUrl() != null) {
            response.setEvidenceUrl(signCloudinaryUrlIfNeeded(response.getEvidenceUrl()));
        }
        if (response.getAttachmentUrl() != null) {
            response.setAttachmentUrl(signCloudinaryUrlIfNeeded(response.getAttachmentUrl()));
        }
        
        return response;
    }

    /**
     * Parses a Cloudinary URL and generates a secure signed URL using the Cloudinary configuration.
     * Prevents HTTP ERROR 401 Unauthorized for Restricted/Authenticated assets.
     */
    private String signCloudinaryUrlIfNeeded(String originalUrl) {
        if (originalUrl == null || originalUrl.trim().isEmpty() || !originalUrl.contains("res.cloudinary.com")) {
            return originalUrl;
        }

        try {
            // RegEx matching pattern: https://res.cloudinary.com/<cloud_name>/<resource_type>/upload/(?:v\d+/)?(<public_id_path>)
            Pattern pattern = Pattern.compile(
                "https?://res\\.cloudinary\\.com/[^/]+/([^/]+)/upload/(?:v\\d+/)?([^?#]+)"
            );
            Matcher matcher = pattern.matcher(originalUrl);
            if (matcher.find()) {
                String resourceType = matcher.group(1); // e.g. "image" or "raw"
                String publicIdWithExt = matcher.group(2); // e.g. "skillverse/documents/user_5/file_xbkx9a.pdf"
                
                String publicId = publicIdWithExt;
                String filename = null;
                
                // For image/video, the public_id should NOT include the extension (Cloudinary treats extension as format)
                if ("image".equals(resourceType) || "video".equals(resourceType)) {
                    if (publicIdWithExt.contains(".")) {
                        publicId = publicIdWithExt.substring(0, publicIdWithExt.lastIndexOf("."));
                        filename = publicIdWithExt.substring(publicIdWithExt.lastIndexOf("/") + 1);
                    }
                } else {
                    // For raw files, keep the extension in public_id, and extract filename
                    if (publicIdWithExt.contains("/")) {
                        filename = publicIdWithExt.substring(publicIdWithExt.lastIndexOf("/") + 1);
                    }
                }
                
                log.info("[CLOUDINARY_SIGN] Successfully parsed and signing URL: resourceType={}, publicId={}, filename={}",
                        resourceType, publicId, filename);
                
                return cloudinaryService.generateSignedUrl(publicId, resourceType, filename);
            }
        } catch (Exception e) {
            log.warn("[CLOUDINARY_SIGN] Failed to generate signed URL for: {}, error: {}", originalUrl, e.getMessage());
        }
        return originalUrl;
    }

    // ─── Notification helpers ────────────────────────────────────────────────

    private void notifyStudentAssessmentAssigned(Journey journey, RoadmapNodeAssignment assignment, Long mentorId) {
        try {
            Long learnerId = journey.getUser() != null ? journey.getUser().getId() : null;
            if (learnerId == null) return;
            String nodeTitle = assignment.getTitle() != null ? assignment.getTitle() : "Node " + assignment.getNodeId();
            String msg = "Mentor đã giao/cập nhật assessment cho node \"" + truncate(nodeTitle, 60) + "\". Bạn có thể bắt đầu làm bài.";
            notificationService.createNotification(
                    learnerId,
                    "Assessment mới từ Mentor",
                    msg,
                    com.exe.skillverse_backend.notification_service.entity.NotificationType.ASSESSMENT_ASSIGNED,
                    journey.getId().toString(),
                    mentorId);
        } catch (Exception ex) {
            log.warn("Failed to notify student about assessment assigned for journey {}", journey.getId(), ex);
        }
    }

    private void notifyStudentAssessmentApproved(Journey journey, RoadmapNodeAssignment assignment, Long mentorId) {
        try {
            Long learnerId = journey.getUser() != null ? journey.getUser().getId() : null;
            if (learnerId == null) return;
            String nodeTitle = assignment.getTitle() != null ? assignment.getTitle() : "Node " + assignment.getNodeId();
            String msg = "Mentor đã duyệt assessment cho node \"" + truncate(nodeTitle, 60) + "\". Bạn đã có thể nộp minh chứng.";
            notificationService.createNotification(
                    learnerId,
                    "Assessment đã được duyệt",
                    msg,
                    com.exe.skillverse_backend.notification_service.entity.NotificationType.ASSESSMENT_APPROVED,
                    journey.getId().toString(),
                    mentorId);
        } catch (Exception ex) {
            log.warn("Failed to notify student about assessment approved for journey {}", journey.getId(), ex);
        }
    }

    private void notifyMentorEvidenceSubmitted(Journey journey, String nodeId, Long learnerId) {
        try {
            // Find the mentor via active booking
            var mentorBookings = bookingRepository.findActiveBookingsCoveringNode(
                    journey.getId(), nodeId, ASSIGNED_MENTOR_STATUSES);
            if (mentorBookings.isEmpty()) {
                // Fallback: try journey-level bookings
                mentorBookings = bookingRepository.findActiveJourneyBookingsForJourney(
                        journey.getId(), ASSIGNED_MENTOR_STATUSES);
            }
            for (var booking : mentorBookings) {
                Long mentorId = booking.getMentor() != null ? booking.getMentor().getId() : null;
                if (mentorId == null) continue;
                String learnerName = journey.getUser() != null && journey.getUser().getFullName() != null
                        ? journey.getUser().getFullName() : "Học viên";
                String msg = learnerName + " đã nộp minh chứng cho node \"" + truncate(nodeId, 40)
                        + "\". Vui lòng kiểm tra và đánh giá.";
                notificationService.createNotification(
                        mentorId,
                        "Học viên nộp bài cần chấm",
                        msg,
                        com.exe.skillverse_backend.notification_service.entity.NotificationType.ASSESSMENT_SUBMITTED_FOR_REVIEW,
                        journey.getId().toString(),
                        learnerId);
            }
        } catch (Exception ex) {
            log.warn("Failed to notify mentor about evidence submitted for journey {} node {}", journey.getId(), nodeId, ex);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
    }
}
