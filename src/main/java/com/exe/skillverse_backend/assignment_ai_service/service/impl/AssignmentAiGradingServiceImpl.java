package com.exe.skillverse_backend.assignment_ai_service.service.impl;

import com.exe.skillverse_backend.assignment_ai_service.dto.AiGradingResultDTO;
import com.exe.skillverse_backend.assignment_ai_service.service.AssignmentAiGradingService;
import com.exe.skillverse_backend.assignment_ai_service.service.AssignmentGradingPromptService;
import com.exe.skillverse_backend.assignment_ai_service.service.FileTextExtractorService;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.AssignmentCriteria;
import com.exe.skillverse_backend.course_service.entity.AssignmentSubmission;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.SubmissionCriteriaScore;
import com.exe.skillverse_backend.course_service.entity.enums.LessonType;
import com.exe.skillverse_backend.course_service.repository.AssignmentCriteriaRepository;
import com.exe.skillverse_backend.course_service.repository.AssignmentRepository;
import com.exe.skillverse_backend.course_service.repository.AssignmentSubmissionRepository;
import com.exe.skillverse_backend.course_service.repository.LessonRepository;
import com.exe.skillverse_backend.course_service.repository.SubmissionCriteriaScoreRepository;
import com.exe.skillverse_backend.ai_service.service.LocalAiGateway;
import com.exe.skillverse_backend.course_service.service.CourseLearningProgressService;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.repository.MediaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AssignmentAiGradingServiceImpl implements AssignmentAiGradingService {

    private static final int MAX_AI_GRADE_ATTEMPTS = 3;
    private static final int DB_CONTEXT_MAX_CHARS = 4000;

    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository submissionRepository;
    private final AssignmentCriteriaRepository criteriaRepository;
    private final SubmissionCriteriaScoreRepository criteriaScoreRepository;
    private final MediaRepository mediaRepository;
    private final AssignmentGradingPromptService gradingPromptService;
    private final FileTextExtractorService fileExtractor;
    private final NotificationService notificationService;
    private final CourseLearningProgressService courseLearningProgressService;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final LocalAiGateway localAiGateway;
    private final LessonRepository lessonRepository;

    public AssignmentAiGradingServiceImpl(
            AssignmentRepository assignmentRepository,
            AssignmentSubmissionRepository submissionRepository,
            AssignmentCriteriaRepository criteriaRepository,
            SubmissionCriteriaScoreRepository criteriaScoreRepository,
            MediaRepository mediaRepository,
            AssignmentGradingPromptService gradingPromptService,
            FileTextExtractorService fileExtractor,
            NotificationService notificationService,
            @Autowired(required = false) @Qualifier("assignmentAiChatModel") ChatModel chatModel,
            @Autowired(required = false) CourseLearningProgressService courseLearningProgressService,
            @Autowired(required = false) LocalAiGateway localAiGateway,
            LessonRepository lessonRepository) {
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.criteriaRepository = criteriaRepository;
        this.criteriaScoreRepository = criteriaScoreRepository;
        this.mediaRepository = mediaRepository;
        this.gradingPromptService = gradingPromptService;
        this.fileExtractor = fileExtractor;
        this.notificationService = notificationService;
        this.chatModel = chatModel;
        this.courseLearningProgressService = courseLearningProgressService;
        this.localAiGateway = localAiGateway;
        this.lessonRepository = lessonRepository;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    @Transactional
    public AiGradingResultDTO generateAiGrade(Long submissionId, Long mentorId) {
        AssignmentSubmission submission = submissionRepository.findByIdWithFullChain(submissionId)
                .orElseThrow(() -> new NotFoundException("SUBMISSION_NOT_FOUND"));

        Assignment assignment = submission.getAssignment();
        Module module = assignment.getModule();
        Course course = module.getCourse();

        if (mentorId != null) {
            Long authorId = course.getAuthor() != null ? course.getAuthor().getId() : null;
            if (authorId == null || !mentorId.equals(authorId)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "You can only AI-grade submissions for your own course");
            }
        }

        if (!Boolean.TRUE.equals(assignment.getAiGradingEnabled())) {
            throw new IllegalStateException("AI grading is not enabled for this assignment");
        }

        // Idempotency: skip if already AI-graded (prevent duplicate processing)
        if (Boolean.TRUE.equals(submission.getIsAiGraded())) {
            log.info("Submission {} already AI-graded, skipping duplicate processing", submissionId);
            return getAiGradeResult(submissionId);
        }

        // Check attempt cap
        if (submission.getAiGradeAttemptCount() != null
                && submission.getAiGradeAttemptCount() >= MAX_AI_GRADE_ATTEMPTS) {
            throw new IllegalArgumentException(
                "AI grading limit reached for this submission (max " + MAX_AI_GRADE_ATTEMPTS + ")");
        }

        // Increment attempt count
        int currentCount = submission.getAiGradeAttemptCount() != null
                ? submission.getAiGradeAttemptCount() : 0;
        submission.setAiGradeAttemptCount(currentCount + 1);
        submission.setAiGradedAt(Instant.now());

        // Extract file text
        String submissionText = "";
        if (submission.getFileMedia() != null) {
            Media media = submission.getFileMedia();
            submissionText = fileExtractor.extractText(media, media.getType());
        } else if (submission.getSubmissionText() != null) {
            submissionText = submission.getSubmissionText();
        }

        if (submissionText.isBlank()) {
            throw new IllegalArgumentException("No content to grade. Submission is empty.");
        }

        // Enrich grading prompt with course/module reading context
        String ragPrefix = "";
        try {
            String ragQuery = buildRagQuery(course, module, assignment);
            String courseContext = fetchCourseContentContext(ragQuery, module, course);
            if (!courseContext.isBlank()) {
                ragPrefix = "## Lý thuyết tham chiếu từ bài giảng\n" + courseContext
                        + "\n\nHãy chấm bài DỰA TRÊN lý thuyết tham chiếu ở trên."
                        + " Nếu bài làm sai so với tài liệu, hãy trừ điểm và giải thích rõ.\n\n";
            }
        } catch (Exception contextEx) {
            log.warn("Course context fetch failed for grading, continuing without context: {}", contextEx.getMessage());
        }

        // Build prompt
        String gradingStyle = assignment.getGradingStyle() != null
                ? assignment.getGradingStyle() : "STANDARD";
        String userPrompt = gradingPromptService.buildGradingPrompt(
                assignment,
                submissionText,
                gradingStyle,
                assignment.getAiGradingPrompt()
        );
        if (!ragPrefix.isBlank()) {
            userPrompt = ragPrefix + userPrompt;
        }

        // Call AI with 1 retry
        AiGradingResultDTO result = callAiWithRetry(userPrompt);

        // Save AI results to submission
        submission.setIsAiGraded(true);
        submission.setAiScore(result.getTotalScore());
        submission.setAiFeedback(result.getOverallFeedback());
        submission.setAiConfidence(result.getOverallConfidence());
        submissionRepository.save(submission);

        // Save criteriaScores to DB so mentor sees them on grading page.
        // Strategy:
        //  1. Match by criteriaId (primary — AI returns IDs from prompt)
        //  2. Fallback: match by orderIndex if AI returned wrong IDs
        // This is robust even if AI hallucinates IDs or returns null.
        List<AssignmentCriteria> criteriaList = criteriaRepository
                .findByAssignmentIdOrderByOrderIndexAsc(assignment.getId());
        Map<Long, AssignmentCriteria> criteriaIdMap = criteriaList.stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(AssignmentCriteria::getId, c -> c));

        for (int i = 0; i < result.getCriteriaScores().size(); i++) {
            AiGradingResultDTO.CriteriaScoreResult csResult = result.getCriteriaScores().get(i);
            AssignmentCriteria criteria = null;

            // Primary: try exact criteriaId match
            if (csResult.getCriteriaId() != null) {
                criteria = criteriaIdMap.get(csResult.getCriteriaId());
            }

            // Fallback: match by position (orderIndex) so we never skip due to hallucinated IDs
            if (criteria == null && i < criteriaList.size()) {
                criteria = criteriaList.get(i);
                log.debug("AI returned criteriaId {} not found, falling back to orderIndex match: {}",
                        csResult.getCriteriaId(), criteria.getId());
            }

            if (criteria == null) {
                log.warn("No matching criteria for AI result index {}, skipping", i);
                continue;
            }

            BigDecimal score = csResult.getScore() != null ? csResult.getScore() : BigDecimal.ZERO;
            SubmissionCriteriaScore entity = SubmissionCriteriaScore.builder()
                    .submission(submission)
                    .criteria(criteria)
                    .score(score)
                    .feedback(csResult.getFeedback())
                    .build();
            criteriaScoreRepository.save(entity);
            log.debug("Saved AI criteria score: submission={}, criteria={}, score={}",
                    submissionId, criteria.getId(), score);
        }

        // Auto-confirm: when aiGradingEnabled=true on the assignment, AI results are automatically
        // confirmed. Sets score, feedback, gradedAt, isPassed — student sees result immediately.
        // Mentor sees it in "Đã chấm" tab (status=GRADED) for audit but takes NO action.
        // trustAiEnabled is now legacy — removed from all logic per spec 2026-04-17.
        if (Boolean.TRUE.equals(assignment.getAiGradingEnabled())) {
            submission.setMentorConfirmed(true);
            submission.setScore(result.getTotalScore());
            submission.setFeedback(result.getOverallFeedback());
            submission.setGradedAt(Instant.now());
            submission.setIsPassed(computeIsPassedFromResult(assignment, result));
            submissionRepository.save(submission);
            log.info("AI grade auto-confirmed for submission {} (score={}, isPassed={})",
                    submissionId, result.getTotalScore(), submission.getIsPassed());

            // Recalculate course progress so the student's learning progress is updated
            if (courseLearningProgressService != null) {
                Long courseId = course.getId();
                Long studentId = submission.getUser().getId();
                courseLearningProgressService.recalculateCourseProgress(courseId, studentId);
                log.info("Course progress recalculated for student {} in course {} after AI auto-pass",
                        studentId, courseId);
            }

            // Notify student of their result
            if (notificationService != null) {
                String passStatus = submission.getIsPassed() ? "PASSED ✓" : "Cần cải thiện";
                notificationService.createNotification(
                        submission.getUser().getId(),
                        "Bài tập đã được chấm điểm",
                        "Bài tập '" + assignment.getTitle() + "' đã được AI chấm: "
                                + result.getTotalScore() + "/" + assignment.getMaxScore() + " - " + passStatus,
                        com.exe.skillverse_backend.notification_service.entity.NotificationType.ASSIGNMENT_GRADED,
                        submissionId.toString(),
                        null
                );
            }
        }

        return result;
    }

    @Override
    @Transactional
    public void toggleTrustAi(Long assignmentId, boolean enabled) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("ASSIGNMENT_NOT_FOUND"));
        assignment.setTrustAiEnabled(enabled);
        assignmentRepository.save(assignment);
        log.info("Trust AI {} for assignment {}", enabled, assignmentId);
    }

    @Override
    @Transactional
    public void requestMentorReview(Long submissionId, Long studentId, String reason) {
        AssignmentSubmission submission = submissionRepository.findByIdWithFullChain(submissionId)
                .orElseThrow(() -> new NotFoundException("SUBMISSION_NOT_FOUND"));

        if (!submission.getUser().getId().equals(studentId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only dispute your own submission");
        }

        submission.setDisputeFlag(true);
        submission.setDisputeAt(Instant.now());
        submission.setDisputeReason(reason);
        submissionRepository.save(submission);

        // Notify mentor
        Assignment assignment = submission.getAssignment();
        Long mentorId = assignment.getModule().getCourse().getAuthor().getId();
        String studentName = submission.getUser().getFullName();
        notificationService.createNotification(
                mentorId,
                "Học viên yêu cầu mentor review",
                "Học viên '" + studentName
                        + "' yêu cầu bạn xem xét lại bài assignment '"
                        + assignment.getTitle() + "'",
                NotificationType.ASSIGNMENT_GRADED,
                submissionId.toString(),
                studentId
        );

        // Also notify student of the request
        notificationService.createNotification(
                studentId,
                "Yêu cầu mentor review đã được gửi",
                "Yêu cầu xem xét lại bài '" + assignment.getTitle()
                        + "' đã được gửi. Mentor sẽ xem xét trong thời gian sớm nhất.",
                NotificationType.ASSIGNMENT_GRADED,
                submissionId.toString(),
                null
        );

        log.info("Dispute requested for submission {} by student {}", submissionId, studentId);
    }

    @Override
    @Transactional(readOnly = true)
    public AiGradingResultDTO getAiGradeResult(Long submissionId) {
        AssignmentSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("SUBMISSION_NOT_FOUND"));

        if (!Boolean.TRUE.equals(submission.getIsAiGraded())) {
            throw new IllegalStateException("No AI grade exists for this submission");
        }

        AiGradingResultDTO dto = new AiGradingResultDTO();
        dto.setTotalScore(submission.getAiScore());
        dto.setOverallFeedback(submission.getAiFeedback());
        dto.setOverallConfidence(submission.getAiConfidence());
        dto.setCriteriaScores(Collections.emptyList());
        return dto;
    }

    private String buildRagQuery(Course course, Module module, Assignment assignment) {
        StringBuilder sb = new StringBuilder();
        if (course.getTitle() != null && !course.getTitle().isBlank()) {
            sb.append(course.getTitle());
        }
        if (module.getTitle() != null && !module.getTitle().isBlank()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(module.getTitle());
        }
        if (assignment.getTitle() != null && !assignment.getTitle().isBlank()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(assignment.getTitle());
        }
        return sb.length() > 0 ? sb.toString() : "assignment grading";
    }

    private String fetchCourseContentContext(String ragQuery, Module module, Course course) {
        if (localAiGateway != null) {
            String courseId = course.getId().toString();
            String moduleId = module.getId().toString();

            // Tier 1: module-scoped course content via RAG (course_id + module_id)
            String ragContext = localAiGateway.fetchRagContext(
                    ragQuery,
                    Map.of(
                            "doc_type", "lesson",
                            "domain", "course_content",
                            "course_id", courseId,
                            "module_id", moduleId
                    ),
                    5);

            if (!ragContext.isBlank()) {
                return ragContext;
            }

            // Tier 2: course-scoped course content via RAG (course_id only)
            ragContext = localAiGateway.fetchRagContext(
                    ragQuery,
                    Map.of(
                            "doc_type", "lesson",
                            "domain", "course_content",
                            "course_id", courseId
                    ),
                    5);

            if (!ragContext.isBlank()) {
                return ragContext;
            }
        }

        // Tier 3: direct DB fallback — reading lessons in the module, truncated to DB_CONTEXT_MAX_CHARS
        String dbContext = lessonRepository.findByModuleIdOrderByOrderIndexAsc(module.getId()).stream()
                .filter(l -> LessonType.READING.equals(l.getType())
                        && l.getContentText() != null
                        && !l.getContentText().isBlank())
                .map(l -> "### " + l.getTitle() + "\n" + l.getContentText())
                .collect(Collectors.joining("\n\n"));
        if (dbContext.length() > DB_CONTEXT_MAX_CHARS) {
            dbContext = dbContext.substring(0, DB_CONTEXT_MAX_CHARS) + "\n...[truncated]";
        }
        return dbContext;
    }

    private AiGradingResultDTO callAiWithRetry(String userPrompt) {
        try {
            return callAi(userPrompt);
        } catch (Exception e) {
            log.warn("AI grading attempt 1 failed: {}", e.getMessage());
            try {
                return callAi(userPrompt);
            } catch (Exception retryEx) {
                log.error("AI grading attempt 2 also failed", retryEx);
                throw new RuntimeException(
                    "AI grading failed after 2 attempts. Error: " + retryEx.getMessage(), retryEx);
            }
        }
    }

    private AiGradingResultDTO callAi(String userPrompt) {
        // Try local first — transport fail OR schema fail both fall through to cloud
        if (localAiGateway != null && localAiGateway.isAvailable()) {
            try {
                String localResponse = localAiGateway.call("", userPrompt);
                return parseGradingResponse(localResponse);
            } catch (Exception localEx) {
                log.warn("Local AI grading failed (transport or schema), falling back to cloud: {}",
                        localEx.getMessage());
            }
        }

        if (chatModel == null) {
            throw new IllegalStateException(
                "AI grading is not available — ASSIGNMENT_AI_API_KEY is not configured. "
                + "Please configure assignment_ai.api-key in your environment.");
        }
        String cloudResponse = ChatClient.create(chatModel).prompt()
                .user(userPrompt)
                .call()
                .content();
        return parseGradingResponse(cloudResponse);
    }

    private AiGradingResultDTO parseGradingResponse(String response) {
        String json = extractJson(response);
        AiGradingResultDTO dto;
        try {
            dto = objectMapper.readValue(json, AiGradingResultDTO.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse AI grading response: " + e.getMessage(), e);
        }
        if (dto.getCriteriaScores() == null || dto.getCriteriaScores().isEmpty()) {
            throw new RuntimeException("AI returned empty criteria scores");
        }
        return dto;
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

    /**
     * Compute isPassed from AI grading result.
     * Uses Coursera-style criteria logic: if required criteria with passingPoints
     * exist, every required criterion must individually meet its threshold.
     * Otherwise falls back to totalScore >= assignment.passingScore (or 70% of maxScore).
     */
    private boolean computeIsPassedFromResult(Assignment assignment, AiGradingResultDTO result) {
        List<AssignmentCriteria> criteria = assignment.getCriteria();
        if (criteria != null && !criteria.isEmpty()) {
            // Build criteriaId → passed flag map from AI result
            Map<Long, Boolean> passedMap = result.getCriteriaScores().stream()
                    .filter(cs -> cs.getCriteriaId() != null)
                    .collect(Collectors.toMap(
                            cs -> cs.getCriteriaId(),
                            cs -> Boolean.TRUE.equals(cs.getPassed())
                    ));

            for (AssignmentCriteria criterion : criteria) {
                if (criterion.isRequired()
                        && hasMeaningfulPassingPoints(criterion.getPassingPoints())
                        && !Boolean.TRUE.equals(passedMap.get(criterion.getId()))) {
                    // Required criterion did NOT pass → overall FAIL
                    return false;
                }
            }
            // All required criteria passed → overall PASS
            return true;
        }

        // No criteria → flat score comparison
        BigDecimal passingScore = assignment.getPassingScore() != null
                ? assignment.getPassingScore()
                : assignment.getMaxScore().multiply(new BigDecimal("0.7"));
        return result.getTotalScore().compareTo(passingScore) >= 0;
    }

    private boolean hasMeaningfulPassingPoints(BigDecimal passingPoints) {
        return passingPoints != null && passingPoints.compareTo(BigDecimal.ZERO) > 0;
    }
}
