package com.exe.skillverse_backend.journey_service.node_mentoring.ai.service.impl;

import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.ai.dto.RoadmapEvidenceAiReviewResult;
import com.exe.skillverse_backend.journey_service.node_mentoring.ai.service.RoadmapEvidenceAiReviewService;
import com.exe.skillverse_backend.journey_service.node_mentoring.ai.service.RoadmapEvidencePromptService;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.AdminReviewDecision;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.AiReviewStatus;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyOutputAssessment;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapEvidenceAiReview;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeSubmission;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.JourneyOutputAssessmentRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.RoadmapEvidenceAiReviewRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.RoadmapNodeSubmissionRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.service.RoadmapNodeCompletionSyncService;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplate;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.roadmap_package_service.constant.RoadmapEvidenceAiReviewDefaults;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
public class RoadmapEvidenceAiReviewServiceImpl implements RoadmapEvidenceAiReviewService {

    private final RoadmapEvidenceAiReviewRepository reviewRepository;
    private final RoadmapNodeSubmissionRepository submissionRepository;
    private final JourneyOutputAssessmentRepository assessmentRepository;
    private final RoadmapEvidencePromptService promptService;
    private final JourneyRepository journeyRepository;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final RoadmapNodeCompletionSyncService syncService;

    public RoadmapEvidenceAiReviewServiceImpl(
            RoadmapEvidenceAiReviewRepository reviewRepository,
            RoadmapNodeSubmissionRepository submissionRepository,
            JourneyOutputAssessmentRepository assessmentRepository,
            RoadmapEvidencePromptService promptService,
            JourneyRepository journeyRepository,
            RoadmapNodeCompletionSyncService syncService,
            @Autowired(required = false) @Qualifier("assignmentAiChatModel") ChatModel chatModel) {
        this.reviewRepository = reviewRepository;
        this.submissionRepository = submissionRepository;
        this.assessmentRepository = assessmentRepository;
        this.promptService = promptService;
        this.journeyRepository = journeyRepository;
        this.syncService = syncService;
        this.chatModel = chatModel;
        this.objectMapper = new ObjectMapper();
    }

    @Async
    @Transactional
    @Override
    public void reviewNodeEvidence(RoadmapNodeSubmission submission, RoadmapTemplate template, 
                                   String nodeTitle, String nodeDescription, String nodeSkills, 
                                   String activityExpectedOutput, String activityRubric, 
                                   String activityAiPromptHint, String activitySkillRequirementsJson) {
        
        log.info("Starting AI review for node submission {}", submission.getId());
        
        Integer attemptNumber = reviewRepository
                .findTopByNodeSubmissionIdOrderByAttemptNumberDesc(submission.getId())
                .map(r -> r.getAttemptNumber() + 1)
                .orElse(1);

        RoadmapEvidenceAiReview review = RoadmapEvidenceAiReview.builder()
                .nodeSubmissionId(submission.getId())
                .journeyId(submission.getJourneyId())
                .roadmapSessionId(submission.getRoadmapSessionId())
                .nodeId(submission.getNodeId())
                .learnerId(submission.getLearnerId())
                .attemptNumber(attemptNumber)
                .status(AiReviewStatus.PENDING)
                .build();
        review = reviewRepository.save(review);
        
        submission.setLatestAiReviewId(review.getId());
        submission.setLatestAiReviewStatus(AiReviewStatus.PENDING);
        submissionRepository.save(submission);

        // If AI review is disabled for this template, route directly to admin queue
        if (!Boolean.TRUE.equals(template.getAiEvidenceReviewEnabled())) {
            log.info("AI review is disabled for template {}. Routing node submission {} to admin queue.",
                    template.getId(), submission.getId());
            review.setStatus(AiReviewStatus.NEEDS_ADMIN_REVIEW);
            review.setErrorMessage("AI review is disabled for this template; admin review is required.");
            reviewRepository.save(review);
            submission.setLatestAiReviewStatus(AiReviewStatus.NEEDS_ADMIN_REVIEW);
            submissionRepository.save(submission);
            return;
        }

        String prompt = promptService.buildNodeEvidencePrompt(
                submission, template, nodeTitle, nodeDescription, nodeSkills, 
                activityExpectedOutput, activityRubric, activityAiPromptHint, activitySkillRequirementsJson);

        boolean isAiDecided = executeAiReview(review, template, prompt);
        if (!isAiDecided) {
            log.info("Skipping target state update for submission {} because admin intervened during AI execution.", submission.getId());
            return;
        }
        
        // Update submission status based on review result with latest-attempt safety check
        RoadmapNodeSubmission freshSubmission = submissionRepository.findById(submission.getId()).orElse(submission);
        if (freshSubmission.getLatestAiReviewId() != null && freshSubmission.getLatestAiReviewId().equals(review.getId())) {
            freshSubmission.setLatestAiReviewStatus(review.getStatus());
            if (review.getStatus() == AiReviewStatus.PASSED) {
                freshSubmission.setVerificationStatus(RoadmapNodeSubmission.VerificationStatus.VERIFIED);
                Journey journey = journeyRepository.findById(freshSubmission.getJourneyId()).orElse(null);
                if (journey != null) {
                    syncService.syncNodeCompletionState(journey, freshSubmission.getNodeId(), true);
                    syncService.recalculateAndSyncJourneyProgress(journey);
                }
            } else if (review.getStatus() == AiReviewStatus.FAILED) {
                freshSubmission.setVerificationStatus(RoadmapNodeSubmission.VerificationStatus.REJECTED);
                freshSubmission.setSubmissionStatus(RoadmapNodeSubmission.SubmissionStatus.REWORK_REQUESTED);
                freshSubmission.setLearnerMarkedComplete(false);
                Journey journey = journeyRepository.findById(freshSubmission.getJourneyId()).orElse(null);
                if (journey != null) {
                    syncService.syncNodeCompletionState(journey, freshSubmission.getNodeId(), false);
                    syncService.recalculateAndSyncJourneyProgress(journey);
                }
            }
            submissionRepository.save(freshSubmission);
        } else {
            log.info("Skipping target state update for submission {} because a newer review {} is active.", 
                     submission.getId(), freshSubmission.getLatestAiReviewId());
        }
    }

    @Async
    @Transactional
    @Override
    public void reviewFinalAssignment(JourneyOutputAssessment assessment, RoadmapTemplate template, 
                                      String aggregatedSkills) {
                                      
        log.info("Starting AI review for final assessment {}", assessment.getId());
        
        Integer attemptNumber = reviewRepository
                .findTopByJourneyOutputAssessmentIdOrderByAttemptNumberDesc(assessment.getId())
                .map(r -> r.getAttemptNumber() + 1)
                .orElse(1);

        Long sessionId = journeyRepository.findById(assessment.getJourneyId())
                .map(Journey::getRoadmapSessionId)
                .orElseThrow(() -> new NotFoundException("Journey not found with id " + assessment.getJourneyId()));

        RoadmapEvidenceAiReview review = RoadmapEvidenceAiReview.builder()
                .journeyOutputAssessmentId(assessment.getId())
                .journeyId(assessment.getJourneyId())
                .roadmapSessionId(sessionId)
                .learnerId(assessment.getLearnerId())
                .attemptNumber(attemptNumber)
                .status(AiReviewStatus.PENDING)
                .build();
        review = reviewRepository.save(review);
        
        assessment.setLatestAiReviewId(review.getId());
        assessment.setLatestAiReviewStatus(AiReviewStatus.PENDING);
        assessmentRepository.save(assessment);

        // If AI review is disabled for this template, route directly to admin queue
        if (!Boolean.TRUE.equals(template.getAiEvidenceReviewEnabled())) {
            log.info("AI review is disabled for template {}. Routing final assessment {} to admin queue.",
                    template.getId(), assessment.getId());
            review.setStatus(AiReviewStatus.NEEDS_ADMIN_REVIEW);
            review.setErrorMessage("AI review is disabled for this template; admin review is required.");
            reviewRepository.save(review);
            assessment.setLatestAiReviewStatus(AiReviewStatus.NEEDS_ADMIN_REVIEW);
            assessmentRepository.save(assessment);
            return;
        }

        String prompt = promptService.buildFinalAssignmentPrompt(assessment, template, aggregatedSkills);

        boolean isAiDecided = executeAiReview(review, template, prompt);
        if (!isAiDecided) {
            log.info("Skipping target state update for final assessment {} because admin intervened during AI execution.", assessment.getId());
            return;
        }
        
        // Update assessment status based on review result with latest-attempt safety check
        JourneyOutputAssessment freshAssessment = assessmentRepository.findById(assessment.getId()).orElse(assessment);
        if (freshAssessment.getLatestAiReviewId() != null && freshAssessment.getLatestAiReviewId().equals(review.getId())) {
            freshAssessment.setLatestAiReviewStatus(review.getStatus());
            if (review.getStatus() == AiReviewStatus.PASSED) {
                freshAssessment.setAssessmentStatus(JourneyOutputAssessment.AssessmentStatus.APPROVED);
            } else if (review.getStatus() == AiReviewStatus.FAILED) {
                freshAssessment.setAssessmentStatus(JourneyOutputAssessment.AssessmentStatus.REJECTED);
            }
            assessmentRepository.save(freshAssessment);
        } else {
            log.info("Skipping target state update for final assessment {} because a newer review {} is active.", 
                     assessment.getId(), freshAssessment.getLatestAiReviewId());
        }
    }
    
    private boolean executeAiReview(RoadmapEvidenceAiReview review, RoadmapTemplate template, String prompt) {
        try {
            if (chatModel == null) {
                throw new IllegalStateException("AI chat model is not configured.");
            }
            
            String response = ChatClient.create(chatModel).prompt()
                    .user(prompt)
                    .call()
                    .content();
                    
            RoadmapEvidenceAiReviewResult result = parseResponse(response);
            
            // Re-fetch review to check if admin already decided during AI processing
            RoadmapEvidenceAiReview freshReview = reviewRepository.findById(review.getId()).orElse(review);
            if (freshReview.getStatus() != AiReviewStatus.PENDING) {
                log.info("Review {} was already decided by admin (status: {}). Keeping admin status and updating only AI metadata.", 
                         review.getId(), freshReview.getStatus());
                
                freshReview.setAiScorePercent(result.getScorePercent());
                freshReview.setAiConfidence(result.getConfidence());
                freshReview.setAiFeedback(result.getFeedback());
                freshReview.setAiRubricBreakdownJson(result.getRubricBreakdownJson());
                populateAiMeta(freshReview);
                
                reviewRepository.save(freshReview);
                
                review.setStatus(freshReview.getStatus());
                return false; // Admin intervened
            }
            
            review.setAiScorePercent(result.getScorePercent());
            review.setAiConfidence(result.getConfidence());
            review.setAiFeedback(result.getFeedback());
            review.setAiRubricBreakdownJson(result.getRubricBreakdownJson());
            populateAiMeta(review);
            
            applyThresholds(review, template);
            
        } catch (Exception e) {
            log.error("AI review failed: {}", e.getMessage(), e);
            
            RoadmapEvidenceAiReview freshReview = reviewRepository.findById(review.getId()).orElse(review);
            if (freshReview.getStatus() != AiReviewStatus.PENDING) {
                log.info("Review {} was already decided by admin (status: {}) despite AI failure. Keeping admin status.", 
                         review.getId(), freshReview.getStatus());
                review.setStatus(freshReview.getStatus());
                return false; // Admin intervened
            }
            
            review.setStatus(AiReviewStatus.NEEDS_ADMIN_REVIEW);
            review.setErrorMessage(e.getMessage());
        }
        
        reviewRepository.save(review);
        return true; // Decided by AI flow
    }

    private void populateAiMeta(RoadmapEvidenceAiReview review) {
        String provider = "SPRING_AI";
        String modelName = "unknown";
        if (chatModel != null) {
            String className = chatModel.getClass().getSimpleName();
            if (className.contains("Mistral")) {
                provider = "MISTRAL";
            } else if (className.contains("OpenAi")) {
                provider = "OPENAI";
            } else if (className.contains("Ollama")) {
                provider = "OLLAMA";
            } else {
                provider = className.replace("ChatModel", "").toUpperCase();
            }
            
            try {
                Object options = chatModel.getClass().getMethod("getDefaultOptions").invoke(chatModel);
                if (options != null) {
                    Object modelObj = options.getClass().getMethod("getModel").invoke(options);
                    if (modelObj != null) {
                        modelName = modelObj.toString();
                    }
                }
            } catch (Exception e) {
                log.warn("Could not retrieve AI model name dynamically: {}", e.getMessage());
            }
        }
        
        if ("unknown".equals(modelName)) {
            if ("MISTRAL".equals(provider)) {
                modelName = "mistral-large-latest";
            } else {
                modelName = "local-ai";
            }
        }
        
        review.setAiProvider(provider);
        review.setAiModelName(modelName);
    }
    
    private RoadmapEvidenceAiReviewResult parseResponse(String response) {
        String json = extractJson(response);
        try {
            return objectMapper.readValue(json, RoadmapEvidenceAiReviewResult.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse AI grading response: " + e.getMessage(), e);
        }
    }
    
    private String extractJson(String response) {
        String cleaned = response != null ? response.trim() : "";
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }
    
    
    private void applyThresholds(RoadmapEvidenceAiReview review, RoadmapTemplate template) {
        if (!Boolean.TRUE.equals(template.getAiEvidenceReviewEnabled())) {
            review.setStatus(AiReviewStatus.NEEDS_ADMIN_REVIEW);
            return;
        }
        
        Double confidence = review.getAiConfidence() != null ? review.getAiConfidence() : 0.0;
        Integer score = review.getAiScorePercent() != null ? review.getAiScorePercent() : 0;
        
        Double minConfidenceToReview = template.getAiManualReviewBelowConfidence() != null 
                ? template.getAiManualReviewBelowConfidence() : RoadmapEvidenceAiReviewDefaults.AI_MANUAL_REVIEW_BELOW_CONFIDENCE;
                
        if (confidence < minConfidenceToReview) {
            review.setStatus(AiReviewStatus.NEEDS_ADMIN_REVIEW);
            return;
        }
        
        Double minConfidenceToPass = template.getAiAutoPassMinConfidence() != null 
                ? template.getAiAutoPassMinConfidence() : RoadmapEvidenceAiReviewDefaults.AI_AUTO_PASS_MIN_CONFIDENCE;
        Integer minScoreToPass = template.getAiAutoPassMinScorePercent() != null 
                ? template.getAiAutoPassMinScorePercent() : RoadmapEvidenceAiReviewDefaults.AI_AUTO_PASS_MIN_SCORE_PERCENT;
                
        if (Boolean.TRUE.equals(template.getAiAutoPassEnabled())) {
            if (confidence >= minConfidenceToPass && score >= minScoreToPass) {
                review.setStatus(AiReviewStatus.PASSED);
            } else {
                review.setStatus(AiReviewStatus.FAILED);
            }
        } else {
            // Auto pass is disabled, AI can only fail or defer to admin
            if (score < minScoreToPass) {
                review.setStatus(AiReviewStatus.FAILED);
            } else {
                review.setStatus(AiReviewStatus.NEEDS_ADMIN_REVIEW);
            }
        }
    }

    @Transactional
    @Override
    public RoadmapEvidenceAiReview decideReview(Long reviewId, AdminReviewDecision decision, String reason, Long adminId) {
        RoadmapEvidenceAiReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Review not found"));

        // Load targets and enforce latest attempt check BEFORE updating review state
        RoadmapNodeSubmission targetSubmission = null;
        JourneyOutputAssessment targetAssessment = null;

        if (review.getNodeSubmissionId() != null) {
            targetSubmission = submissionRepository.findById(review.getNodeSubmissionId())
                    .orElseThrow(() -> new NotFoundException("Node submission not found"));
            if (targetSubmission.getLatestAiReviewId() == null || !targetSubmission.getLatestAiReviewId().equals(review.getId())) {
                throw new BadRequestException("Cannot review this attempt because a newer submission review exists.");
            }
        } else if (review.getJourneyOutputAssessmentId() != null) {
            targetAssessment = assessmentRepository.findById(review.getJourneyOutputAssessmentId())
                    .orElseThrow(() -> new NotFoundException("Journey assessment not found"));
            if (targetAssessment.getLatestAiReviewId() == null || !targetAssessment.getLatestAiReviewId().equals(review.getId())) {
                throw new BadRequestException("Cannot review this attempt because a newer submission review exists.");
            }
        }

        // Reason Validation
        String trimmedReason = reason != null ? reason.trim() : null;
        boolean isOverride = review.getStatus() == AiReviewStatus.PASSED || review.getStatus() == AiReviewStatus.FAILED;
        if (isOverride && (trimmedReason == null || trimmedReason.isEmpty())) {
            throw new BadRequestException("Vui lòng nhập lý do khi ghi đè kết quả hệ thống.");
        }
        if (decision == AdminReviewDecision.REJECT && (trimmedReason == null || trimmedReason.isEmpty())) {
            throw new BadRequestException("Vui lòng nhập lý do để học viên biết cần sửa gì.");
        }

        // Set Audit Fields
        review.setAdminDecision(decision);
        review.setAdminReviewReason(trimmedReason);
        review.setAdminReviewedBy(adminId);
        review.setAdminReviewedAt(Instant.now());

        // Update Review Status
        if (decision == AdminReviewDecision.APPROVE) {
            review.setStatus(AiReviewStatus.PASSED);
        } else {
            review.setStatus(AiReviewStatus.FAILED);
        }

        reviewRepository.save(review);

        // Apply effects to target
        if (targetSubmission != null) {
            targetSubmission.setLatestAiReviewStatus(review.getStatus());
            if (review.getStatus() == AiReviewStatus.PASSED) {
                targetSubmission.setVerificationStatus(RoadmapNodeSubmission.VerificationStatus.VERIFIED);
                Journey journey = journeyRepository.findById(targetSubmission.getJourneyId()).orElse(null);
                if (journey != null) {
                    syncService.syncNodeCompletionState(journey, targetSubmission.getNodeId(), true);
                    syncService.recalculateAndSyncJourneyProgress(journey);
                }
            } else {
                targetSubmission.setVerificationStatus(RoadmapNodeSubmission.VerificationStatus.REJECTED);
                targetSubmission.setSubmissionStatus(RoadmapNodeSubmission.SubmissionStatus.REWORK_REQUESTED);
                targetSubmission.setLearnerMarkedComplete(false);
                Journey journey = journeyRepository.findById(targetSubmission.getJourneyId()).orElse(null);
                if (journey != null) {
                    syncService.syncNodeCompletionState(journey, targetSubmission.getNodeId(), false);
                    syncService.recalculateAndSyncJourneyProgress(journey);
                }
            }
            submissionRepository.save(targetSubmission);
        } else if (targetAssessment != null) {
            targetAssessment.setLatestAiReviewStatus(review.getStatus());
            if (review.getStatus() == AiReviewStatus.PASSED) {
                targetAssessment.setAssessmentStatus(JourneyOutputAssessment.AssessmentStatus.APPROVED);
            } else {
                targetAssessment.setAssessmentStatus(JourneyOutputAssessment.AssessmentStatus.REJECTED);
            }
            assessmentRepository.save(targetAssessment);
        }

        return review;
    }

}
